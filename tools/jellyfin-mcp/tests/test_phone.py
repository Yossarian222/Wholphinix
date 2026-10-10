import logging

import httpx
import pytest

from jellyfin_mcp import phone, server
from jellyfin_mcp.jellyfin import JellyfinClient
from test_server import M1, SECRET, SESSION, FakeJellyfin
from test_whatsapp import FakeAnthropic, resp, text, tool_use

CONV = "abcdef0123456789"


@pytest.fixture
def jf():
    f = FakeJellyfin()
    server.set_client(JellyfinClient("http://jf", "key", "Roman", transport=httpx.MockTransport(f.handler)))
    return f


@pytest.fixture
def app(base_url, monkeypatch):
    monkeypatch.delenv("PHONE_APP", raising=False)
    phone.set_agent(None)
    yield f"{base_url}/{SECRET}/app"
    phone.set_agent(None)


def fake_agent(responses: list) -> phone.PhoneAgent:
    agent = phone.PhoneAgent(
        server.mcp, phone.PHONE_SYSTEM + server.INSTRUCTIONS, api_key="sk-ant-test",
        anthropic_client=FakeAnthropic(responses),
    )
    phone.set_agent(agent)
    return agent


def test_page_and_static_files(app, base_url):
    r = httpx.get(app)
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/html")
    assert r.headers["referrer-policy"] == "no-referrer"
    assert "Telka" in r.text and "app.js" in r.text
    assert httpx.get(app + "/").status_code == 200  # trailing slash: same page
    for name, ctype in [("app.js", "text/javascript"), ("manifest.webmanifest", "application/manifest+json"),
                        ("icon-192.png", "image/png"), ("icon-512.png", "image/png")]:
        r = httpx.get(f"{app}/{name}")
        assert r.status_code == 200 and r.headers["content-type"].startswith(ctype), name
        assert r.content
    manifest = httpx.get(f"{app}/manifest.webmanifest").json()
    assert manifest["display"] == "standalone" and manifest["start_url"] == "../app"
    # Only the listed files, never arbitrary paths from the package
    assert httpx.get(f"{app}/index.html").status_code == 404
    assert httpx.get(f"{app}/..%2Fphone.py").status_code == 404
    # Without the secret: nothing
    assert httpx.get(f"{base_url}/app").status_code == 404


def test_disabled(app, monkeypatch):
    monkeypatch.setenv("PHONE_APP", "0")
    assert httpx.get(app).status_code == 404
    assert httpx.post(f"{app}/chat", json={"text": "x", "conversation": CONV}).status_code == 404


def test_chat_without_key(app, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    r = httpx.post(f"{app}/chat", json={"text": "ahoj", "conversation": CONV})
    assert r.status_code == 200 and r.json()["reply"] == phone.NO_KEY_REPLY


@pytest.mark.parametrize("body", [
    {"text": "", "conversation": CONV},
    {"text": "ahoj", "conversation": "short"},
    {"text": "ahoj", "conversation": "../../etc"},
    ["not", "a", "dict"],
])
def test_chat_bad_request(app, body):
    fake_agent([])
    assert httpx.post(f"{app}/chat", json=body).status_code == 400


def test_chat_too_long(app):
    agent = fake_agent([])
    r = httpx.post(f"{app}/chat", json={"text": "x" * (phone.MAX_TEXT_CHARS + 1), "conversation": CONV})
    assert "pridlhé" in r.json()["reply"] and agent.anthropic.requests == []


def test_chat_tool_use_and_history(app, jf, caplog):
    agent = fake_agent([
        resp("tool_use", tool_use("search_library", {"query": "pulp fiction"}, "t1")),
        resp("tool_use", tool_use("play", {"item_id": M1}, "t2")),
        resp("end_turn", text("Púšťam Pulp Fiction 🍿")),
        resp("end_turn", text("Jasné")),
    ])
    with caplog.at_level(logging.INFO, logger="jellyfin_mcp"):
        r = httpx.post(f"{app}/chat", json={"text": "pusti pulp fiction", "conversation": CONV}, timeout=10)
    assert r.json() == {"reply": "Púšťam Pulp Fiction 🍿"}
    _, path, params, _ = jf.calls[-1]
    assert path == f"/Sessions/{SESSION}/Playing" and params["itemIds"] == M1
    first = agent.anthropic.requests[0]
    assert "mobilnej appke" in first["system"][0]["text"] and "search_library" in first["system"][0]["text"]
    logs = " ".join(rec.getMessage() for rec in caplog.records)
    assert "pulp fiction" not in logs.lower() and "Phone: tool play" in logs

    httpx.post(f"{app}/chat", json={"text": "a titulky", "conversation": CONV}, timeout=10)
    assert agent.anthropic.requests[-1]["messages"][:2] == [
        {"role": "user", "content": "pusti pulp fiction"},
        {"role": "assistant", "content": "Púšťam Pulp Fiction 🍿"},
    ]

    # Reset forgets the history; a different conversation id never sees it
    assert httpx.post(f"{app}/reset", json={"conversation": CONV}).json() == {"ok": True}
    agent.anthropic.responses.append(resp("end_turn", text("Ahoj")))
    httpx.post(f"{app}/chat", json={"text": "ahoj", "conversation": CONV}, timeout=10)
    assert agent.anthropic.requests[-1]["messages"] == [{"role": "user", "content": "ahoj"}]


def test_status(app, jf):
    s = httpx.get(f"{app}/status").json()
    assert s["connected"] is True and s["playing"]["name"] == "Pulp Fiction"
    assert s["position_min"] == 10.0 and s["paused"] is False
    assert httpx.get(f"{app}/status").headers["cache-control"] == "no-store"


def test_status_not_connected(app, jf):
    jf.wholphin_user = "someone-else"
    s = httpx.get(f"{app}/status").json()
    assert s["connected"] is False and s["message"]


def test_control(app, jf):
    r = httpx.post(f"{app}/control", json={"action": "toggle"})
    assert r.json() == {"ok": True, "action": "toggle"}
    assert jf.calls[-1][1] == f"/Sessions/{SESSION}/Playing/PlayPause"

    httpx.post(f"{app}/control", json={"action": "back", "seconds": 30})
    _, path, params, _ = jf.calls[-1]
    assert path == f"/Sessions/{SESSION}/Playing/Seek" and int(params["seekPositionTicks"]) == 570 * 10_000_000


@pytest.mark.parametrize("body", [{"action": "seek"}, {"action": "rm -rf"}, {}])
def test_control_rejects_other_actions(app, jf, body):
    assert httpx.post(f"{app}/control", json=body).status_code == 400
    assert jf.calls == []


def test_control_error_shown(app, jf):
    jf.wholphin_user = "someone-else"
    r = httpx.post(f"{app}/control", json={"action": "pause"})
    assert r.status_code == 200 and r.json()["ok"] is False and r.json()["error"]
