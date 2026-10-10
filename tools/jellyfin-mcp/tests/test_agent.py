import asyncio
import json
import logging
from datetime import datetime
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import anthropic
import httpx
import httpx2
import pytest

from jellyfin_mcp import agent, server
from jellyfin_mcp.jellyfin import JellyfinClient
from test_server import M1, SESSION, FakeJellyfin

NOW = datetime(2026, 10, 10, 21, 39, tzinfo=ZoneInfo("Europe/Bratislava"))
NOTE = "[Aktuálny čas: sobota 10. 10. 2026, 21:39]"
KEY = "phone:abcdef0123456789"


def user(text: str) -> dict:
    """The new user message as the agent sends it: a time note block + the text."""
    return {"role": "user", "content": [{"type": "text", "text": NOTE}, {"type": "text", "text": text}]}


def tool_use(name: str, args: dict, id_: str = "toolu_1"):
    return SimpleNamespace(type="tool_use", id=id_, name=name, input=args)


def text(t: str):
    return SimpleNamespace(type="text", text=t)


def resp(stop_reason: str, *content):
    return SimpleNamespace(stop_reason=stop_reason, content=list(content))


class FakeAnthropic:
    """Stands in for AsyncAnthropic: returns scripted responses, records requests."""

    def __init__(self, responses: list) -> None:
        self.responses = list(responses)
        self.requests: list[dict] = []
        self.beta = SimpleNamespace(messages=SimpleNamespace(create=self.create))

    async def create(self, **kwargs):
        self.requests.append({**kwargs, "messages": list(kwargs["messages"])})
        r = self.responses.pop(0)
        if isinstance(r, Exception):
            raise r
        return r


def make_agent(responses: list, system: str = "Tykaj. " + server.INSTRUCTIONS) -> agent.ChatAgent:
    a = agent.ChatAgent(server.mcp, system, api_key="sk-ant-test", anthropic_client=FakeAnthropic(responses))
    a.now = lambda: NOW
    return a


@pytest.fixture
def jf():
    f = FakeJellyfin()
    server.set_client(JellyfinClient("http://jf", "key", "Roman", transport=httpx.MockTransport(f.handler)))
    return f


def run(coro):
    return asyncio.run(coro)


def test_memory_expires():
    now = [0.0]
    m = agent.Memory(ttl=1800, clock=lambda: now[0])
    conv = m.get("1")
    conv.exchanges.append(("a", "b"))
    m.touch(conv)
    now[0] = 1000
    assert list(m.get("1").exchanges) == [("a", "b")]
    now[0] = 1000 + 1801
    assert list(m.get("1").exchanges) == []


def test_history_limited_to_10_exchanges():
    conv = agent.Conversation()
    for i in range(15):
        conv.exchanges.append((str(i), str(i)))
    assert len(conv.exchanges) == 10 and conv.exchanges[0][0] == "5"


def test_end_to_end_tool_use(jf, caplog):
    a = make_agent([
        resp("tool_use", text("Hľadám…"), tool_use("search_library", {"query": "pulp fiction"}, "t1")),
        resp("tool_use", tool_use("play", {"item_id": M1}, "t2")),
        resp("end_turn", text("Púšťam Pulp Fiction od 20. minúty 🍿")),
    ])
    with caplog.at_level(logging.INFO, logger="jellyfin_mcp"):
        reply = run(a.answer(KEY, "pusti pulp fiction"))
    assert reply == "Púšťam Pulp Fiction od 20. minúty 🍿"
    # The real MCP tool ran against (fake) Jellyfin
    _, path, params, _ = jf.calls[-1]
    assert path == f"/Sessions/{SESSION}/Playing" and params["itemIds"] == M1

    reqs = a.anthropic.requests
    assert len(reqs) == 3
    first = reqs[0]
    assert first["model"] == "claude-sonnet-5-5"
    assert first["fallbacks"] == "default" and first["betas"] == [agent.FALLBACK_BETA]
    assert first["system"][-1]["cache_control"] == {"type": "ephemeral"}
    names = [t["name"] for t in first["tools"]]
    assert names == sorted(names) and {"play", "search_library", "set_audio", "show_message"} <= set(names)
    assert reqs[1]["tools"] == first["tools"] and reqs[1]["system"] == first["system"]  # stable prefix
    assert first["messages"] == [user("pusti pulp fiction")]
    result = reqs[1]["messages"][-1]["content"][0]
    assert result["tool_use_id"] == "t1" and "is_error" not in result
    assert json.loads(result["content"])["results"][0]["id"] == M1

    logs = " ".join(r.getMessage() for r in caplog.records)
    assert "pulp fiction" not in logs.lower()

    # Follow-up uses the remembered exchange (plain text, without the time note)
    a.anthropic.responses.append(resp("end_turn", text("Jasné")))
    run(a.answer(KEY, "a daj titulky"))
    assert a.anthropic.requests[-1]["messages"] == [
        {"role": "user", "content": "pusti pulp fiction"},
        {"role": "assistant", "content": "Púšťam Pulp Fiction od 20. minúty 🍿"},
        user("a daj titulky"),
    ]


def test_tool_error_goes_back_to_claude(jf):
    a = make_agent([
        resp("tool_use", tool_use("play", {"item_id": "../Users"})),
        resp("end_turn", text("Toto neviem pustiť.")),
    ])
    assert run(a.answer(KEY, "pusti")) == "Toto neviem pustiť."
    result = a.anthropic.requests[1]["messages"][-1]["content"][0]
    assert result["is_error"] is True and "Invalid" in result["content"]
    assert jf.calls == []


def test_claude_error_apology():
    err = anthropic.APIConnectionError(request=httpx2.Request("POST", "https://api.anthropic.com/v1/messages"))
    a = make_agent([err])
    assert run(a.answer(KEY, "ahoj")) == agent.ERROR_REPLY
    assert list(a.memory.get(KEY).exchanges) == []  # failed turn not remembered


def test_tool_round_limit(jf):
    a = make_agent([resp("tool_use", tool_use("whats_playing", {}, f"t{i}")) for i in range(10)])
    assert run(a.answer(KEY, "čo beží")) == agent.TOO_LONG_REPLY
    assert len(a.anthropic.requests) == agent.MAX_TOOL_ROUNDS


def test_refusal_and_no_fallback_for_haiku():
    a = make_agent([resp("refusal")])
    a.model = "claude-haiku-5-5"
    assert run(a.answer(KEY, "?")) == agent.REFUSAL_REPLY
    assert "fallbacks" not in a.anthropic.requests[0]


def test_time_note_and_timezone(monkeypatch):
    assert agent.time_note(NOW) == NOTE
    assert agent.time_note(datetime(2026, 1, 4, 7, 5)) == "[Aktuálny čas: nedeľa 4. 1. 2026, 07:05]"
    monkeypatch.delenv("TIMEZONE", raising=False)
    assert agent.local_timezone().key == "Europe/Bratislava"
    monkeypatch.setenv("TIMEZONE", "Europe/Prague")
    assert agent.local_timezone().key == "Europe/Prague"
    monkeypatch.setenv("TIMEZONE", "Mars/Olympus")
    assert agent.local_timezone().key == "Europe/Bratislava"
