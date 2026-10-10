import asyncio
import hashlib
import hmac
import json
import logging
import time
from types import SimpleNamespace

import anthropic
import httpx
import httpx2
import pytest

from jellyfin_mcp import server, whatsapp
from jellyfin_mcp.jellyfin import JellyfinClient
from test_server import M1, SECRET, SESSION, FakeJellyfin

APP_SECRET = "app-secret-xyz"
VERIFY = "verify-me-123"
TOKEN = "EAAG-graph-token-should-never-be-logged"
ALLOWED = "421905123456"
STRANGER = "420777888999"
PHONE_ID = "1098765"

ENV = {
    "WHATSAPP_TOKEN": TOKEN,
    "WHATSAPP_PHONE_NUMBER_ID": PHONE_ID,
    "WHATSAPP_APP_SECRET": APP_SECRET,
    "WHATSAPP_VERIFY_TOKEN": VERIFY,
    "WHATSAPP_ALLOWED_NUMBERS": " +421 905 123 456 , 00421911000111",
    "ANTHROPIC_API_KEY": "sk-ant-test",
}


def sign(body: bytes, secret: str = APP_SECRET) -> str:
    return "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()


def text_msg(msg_id: str, body: str, sender: str = ALLOWED) -> dict:
    return {"from": sender, "id": msg_id, "timestamp": "1760000000", "type": "text", "text": {"body": body}}


def payload(*messages: dict, statuses: list | None = None) -> dict:
    value = {"messaging_product": "whatsapp", "metadata": {"phone_number_id": PHONE_ID}}
    if messages:
        value["messages"] = list(messages)
    if statuses:
        value["statuses"] = statuses
    return {"object": "whatsapp_business_account",
            "entry": [{"id": "waba", "changes": [{"field": "messages", "value": value}]}]}


class FakeGraph:
    def __init__(self) -> None:
        self.sent: list[dict] = []
        self.read: list[str] = []
        self.auth: set[str] = set()
        self.paths: set[str] = set()

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.auth.add(request.headers.get("authorization", ""))
        self.paths.add(request.url.path)
        body = json.loads(request.content)
        if body.get("status") == "read":
            self.read.append(body["message_id"])
        else:
            self.sent.append(body)
        return httpx.Response(200, json={"messages": [{"id": "wamid.out"}]})

    def texts(self) -> list[str]:
        return [m["text"]["body"] for m in self.sent]


def tool_use(name: str, args: dict, id_: str = "toolu_1"):
    return SimpleNamespace(type="tool_use", id=id_, name=name, input=args)


def text(t: str):
    return SimpleNamespace(type="text", text=t)


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


def resp(stop_reason: str, *content):
    return SimpleNamespace(stop_reason=stop_reason, content=list(content))


def config() -> whatsapp.Config:
    return whatsapp.Config(
        token=TOKEN, phone_number_id=PHONE_ID, app_secret=APP_SECRET, verify_token=VERIFY,
        allowed_numbers=frozenset({ALLOWED}), anthropic_api_key="sk-ant-test",
    )


@pytest.fixture
def jf():
    f = FakeJellyfin()
    server.set_client(JellyfinClient("http://jf", "key", "Roman", transport=httpx.MockTransport(f.handler)))
    return f


@pytest.fixture
def graph():
    return FakeGraph()


def make_bot(graph: FakeGraph, responses: list) -> whatsapp.WhatsAppBot:
    return whatsapp.WhatsAppBot(
        config(), server.mcp, whatsapp.WHATSAPP_SYSTEM + server.INSTRUCTIONS,
        anthropic_client=FakeAnthropic(responses),
        graph_transport=httpx.MockTransport(graph.handler),
    )


@pytest.fixture
def bot_env(monkeypatch):
    for k, v in ENV.items():
        monkeypatch.setenv(k, v)
    whatsapp.set_bot(None)
    yield
    whatsapp.set_bot(None)


def run(coro):
    return asyncio.run(coro)


# ---- unit ----

def test_config_from_env(bot_env, monkeypatch):
    c = whatsapp.Config.from_env()
    assert c.allowed_numbers == {"421905123456", "421911000111"}
    assert c.model == "claude-sonnet-5-5" and c.graph_version.startswith("v")
    assert TOKEN not in repr(c) and APP_SECRET not in repr(c)
    monkeypatch.setenv("CLAUDE_MODEL", "claude-haiku-5-5")
    monkeypatch.setenv("GRAPH_VERSION", "22.0")
    c = whatsapp.Config.from_env()
    assert c.model == "claude-haiku-5-5" and c.graph_version == "v22.0"
    monkeypatch.delenv("WHATSAPP_APP_SECRET")
    assert whatsapp.Config.from_env() is None


def test_verify_signature():
    body = b'{"a":1}'
    assert whatsapp.verify_signature(APP_SECRET, body, sign(body))
    assert not whatsapp.verify_signature(APP_SECRET, body, sign(body, "other"))
    assert not whatsapp.verify_signature(APP_SECRET, body + b" ", sign(body))
    assert not whatsapp.verify_signature(APP_SECRET, body, None)
    assert not whatsapp.verify_signature(APP_SECRET, body, "sha1=abc")


def test_normalize_and_mask():
    assert whatsapp.normalize_number("+421 905 123-456") == "421905123456"
    assert whatsapp.normalize_number("00421905123456") == "421905123456"
    assert whatsapp.mask_number("421905123456") == "…456"


def test_deduper():
    d = whatsapp.Deduper(size=3)
    assert not d.seen("a") and d.seen("a")
    for x in "bcd":
        d.seen(x)
    assert not d.seen("a")  # evicted


def test_memory_expires():
    now = [0.0]
    m = whatsapp.Memory(ttl=1800, clock=lambda: now[0])
    conv = m.get("1")
    conv.exchanges.append(("a", "b"))
    m.touch(conv)
    now[0] = 1000
    assert list(m.get("1").exchanges) == [("a", "b")]
    now[0] = 1000 + 1801
    assert list(m.get("1").exchanges) == []


def test_history_limited_to_10_exchanges():
    conv = whatsapp.Conversation()
    for i in range(15):
        conv.exchanges.append((str(i), str(i)))
    assert len(conv.exchanges) == 10 and conv.exchanges[0][0] == "5"


# ---- webhook over HTTP ----

def test_not_configured_is_404(base_url, monkeypatch):
    for k in ENV:
        monkeypatch.delenv(k, raising=False)
    whatsapp.set_bot(None)
    assert httpx.get(f"{base_url}/{SECRET}/whatsapp", params={"hub.mode": "subscribe"}).status_code == 404
    assert httpx.post(f"{base_url}/{SECRET}/whatsapp", content=b"{}").status_code == 404
    assert httpx.get(f"{base_url}/health").status_code == 200  # the rest still works


def test_verification(base_url, bot_env):
    url = f"{base_url}/{SECRET}/whatsapp"
    ok = httpx.get(url, params={"hub.mode": "subscribe", "hub.verify_token": VERIFY, "hub.challenge": "1158201444"})
    assert ok.status_code == 200 and ok.text == "1158201444"
    bad = httpx.get(url, params={"hub.mode": "subscribe", "hub.verify_token": "nope", "hub.challenge": "x"})
    assert bad.status_code == 403
    assert httpx.get(url, params={"hub.mode": "unsubscribe", "hub.verify_token": VERIFY}).status_code == 403
    # Without the secret path the webhook is not reachable
    assert httpx.get(f"{base_url}/whatsapp", params={"hub.verify_token": VERIFY}).status_code == 404


def test_post_signature(base_url, bot_env, graph):
    bot = make_bot(graph, [resp("end_turn", text("Ahoj!"))])
    whatsapp.set_bot(bot)
    url = f"{base_url}/{SECRET}/whatsapp"
    body = json.dumps(payload(text_msg("wamid.1", "ahoj"))).encode()
    assert httpx.post(url, content=body).status_code == 403  # missing
    assert httpx.post(url, content=body, headers={"X-Hub-Signature-256": sign(body, "wrong")}).status_code == 403
    assert httpx.post(url, content=body, headers={"X-Hub-Signature-256": "sha256=zz"}).status_code == 403
    time.sleep(0.1)
    assert graph.sent == [] and bot.anthropic.requests == []

    r = httpx.post(url, content=body, headers={"X-Hub-Signature-256": sign(body)})
    assert r.status_code == 200
    for _ in range(100):
        if graph.sent:
            break
        time.sleep(0.02)
    assert graph.texts() == ["Ahoj!"]
    assert graph.read == ["wamid.1"]
    assert graph.auth == {f"Bearer {TOKEN}"}
    assert graph.paths == {f"/{bot.config.graph_version}/{PHONE_ID}/messages"}
    assert graph.sent[0]["to"] == ALLOWED and graph.sent[0]["messaging_product"] == "whatsapp"


# ---- message handling ----

def test_ignores_numbers_not_allowed_and_statuses(graph, caplog):
    bot = make_bot(graph, [])
    with caplog.at_level(logging.DEBUG, logger="jellyfin_mcp"):
        run(bot.handle_payload(payload(text_msg("wamid.x", "pusti film", sender=STRANGER))))
        run(bot.handle_payload(payload(statuses=[{"id": "wamid.y", "status": "delivered", "recipient_id": ALLOWED}])))
    assert graph.sent == [] and graph.read == [] and bot.anthropic.requests == []
    logs = " ".join(r.getMessage() for r in caplog.records)
    assert STRANGER not in logs and "999" in logs and "pusti film" not in logs


def test_failed_status_logged(graph, caplog):
    bot = make_bot(graph, [])
    failed = {
        "id": "wamid.z",
        "status": "failed",
        "recipient_id": ALLOWED,
        "errors": [{"code": 131030, "title": "Recipient phone number not in allowed list", "error_data": {"details": "x"}}],
    }
    with caplog.at_level(logging.INFO, logger="jellyfin_mcp"):
        run(bot.handle_payload(payload(statuses=[failed])))
    logs = " ".join(r.getMessage() for r in caplog.records)
    assert "131030" in logs and "not in allowed list" in logs and ALLOWED not in logs
    assert graph.sent == []


def test_dedup(graph):
    bot = make_bot(graph, [resp("end_turn", text("Raz"))])
    p = payload(text_msg("wamid.dup", "ahoj"))
    run(bot.handle_payload(p))
    run(bot.handle_payload(p))  # Meta redelivery
    assert graph.texts() == ["Raz"] and len(bot.anthropic.requests) == 1


def test_non_text_reply(graph):
    bot = make_bot(graph, [])
    image = {"from": ALLOWED, "id": "wamid.i", "type": "image", "image": {"id": "m1"}}
    run(bot.handle_payload(payload(image)))
    assert graph.texts() == [whatsapp.TEXT_ONLY_REPLY] and bot.anthropic.requests == []


# ---- voice notes ----

MEDIA_URL = "https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=m1&ext=1&hash=abc"
AUDIO = b"OggS" + b"\x00" * 1000


def voice_msg(msg_id: str, media_id: str = "m1") -> dict:
    return {"from": ALLOWED, "id": msg_id, "type": "audio",
            "audio": {"id": media_id, "mime_type": "audio/ogg; codecs=opus", "voice": True}}


class FakeVoice:
    """Graph media lookup + media download + STT endpoint, behind one MockTransport."""

    def __init__(self, graph: FakeGraph, audio: bytes = AUDIO, transcript: str = "pusti Pelíšky",
                 stt_status: int = 200, file_size: int | None = None) -> None:
        self.graph = graph
        self.audio = audio
        self.transcript = transcript
        self.stt_status = stt_status
        self.file_size = len(audio) if file_size is None else file_size
        self.stt: list[httpx.Request] = []
        self.media_auth: list[str] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        host, path = request.url.host, request.url.path
        if host == "graph.facebook.com" and request.method == "GET":
            assert path.endswith("/m1")
            self.media_auth.append(request.headers.get("authorization", ""))
            return httpx.Response(200, json={"url": MEDIA_URL, "mime_type": "audio/ogg; codecs=opus",
                                             "file_size": self.file_size, "id": "m1"})
        if host == "graph.facebook.com":
            return self.graph.handler(request)
        if host == "lookaside.fbsbx.com":
            self.media_auth.append(request.headers.get("authorization", ""))
            return httpx.Response(200, content=self.audio, headers={"content-type": "audio/ogg"})
        self.stt.append(request)
        if self.stt_status >= 400:
            return httpx.Response(self.stt_status, json={"error": {"message": "boom"}})
        return httpx.Response(200, json={"text": self.transcript})


def make_voice_bot(graph: FakeGraph, voice: FakeVoice, responses: list, **cfg) -> whatsapp.WhatsAppBot:
    cfg = {"stt_api_key": "sk-openai-test", **cfg}
    transport = httpx.MockTransport(voice.handler)
    return whatsapp.WhatsAppBot(
        whatsapp.Config(**{**config().__dict__, **cfg}), server.mcp, "sys",
        anthropic_client=FakeAnthropic(responses), graph_transport=transport, http_transport=transport,
    )


def test_voice_config(bot_env, monkeypatch):
    assert not whatsapp.Config.from_env().voice_enabled
    monkeypatch.setenv("STT_URL", "http://192.168.1.201:8000/v1/audio/transcriptions")
    c = whatsapp.Config.from_env()
    assert c.voice_enabled and c.stt_api_key == "" and c.stt_model == "whisper-1" and c.stt_language == "sk"
    monkeypatch.delenv("STT_URL")
    monkeypatch.setenv("STT_API_KEY", "sk-openai")
    monkeypatch.setenv("STT_MODEL", "gpt-4o-mini-transcribe")
    c = whatsapp.Config.from_env()
    assert c.voice_enabled and c.stt_url == whatsapp.DEFAULT_STT_URL and c.stt_model == "gpt-4o-mini-transcribe"
    assert "sk-openai" not in repr(c)


def test_voice_transcribed_and_answered(graph, caplog):
    voice = FakeVoice(graph)
    bot = make_voice_bot(graph, voice, [resp("end_turn", text("Púšťam Pelíšky 🍿"))])
    with caplog.at_level(logging.DEBUG, logger="jellyfin_mcp"):
        run(bot.handle_payload(payload(voice_msg("wamid.v1"))))
    assert graph.texts() == ["Rozumel som: „pusti Pelíšky“\n\nPúšťam Pelíšky 🍿"]
    assert graph.read == ["wamid.v1"]
    # Same Claude loop as text, with the transcript as the user message
    assert bot.anthropic.requests[0]["messages"] == [{"role": "user", "content": "pusti Pelíšky"}]
    assert list(bot.memory.get(ALLOWED).exchanges) == [("pusti Pelíšky", "Púšťam Pelíšky 🍿")]
    # Media lookup and download both carry the WhatsApp token
    assert voice.media_auth == [f"Bearer {TOKEN}", f"Bearer {TOKEN}"]
    (stt,) = voice.stt
    assert str(stt.url) == whatsapp.DEFAULT_STT_URL
    assert stt.headers["authorization"] == "Bearer sk-openai-test"
    body = stt.content
    assert b'name="model"\r\n\r\nwhisper-1' in body and b'name="language"\r\n\r\nsk' in body
    assert b'filename="voice.ogg"' in body and AUDIO in body
    logs = " ".join(r.getMessage() for r in caplog.records)
    assert "Pelíšky" not in logs and ALLOWED not in logs and TOKEN not in logs and "sk-openai-test" not in logs


def test_voice_local_whisper_without_key(graph):
    voice = FakeVoice(graph, transcript="  čo   beží  ")
    url = "http://whisper:8000/v1/audio/transcriptions"
    bot = make_voice_bot(graph, voice, [resp("end_turn", text("Nič."))], stt_api_key="", stt_url=url)
    run(bot.handle_payload(payload(voice_msg("wamid.v2"))))
    assert graph.texts() == ["Rozumel som: „čo beží“\n\nNič."]
    assert str(voice.stt[0].url) == url and "authorization" not in voice.stt[0].headers


def test_voice_disabled(graph):
    voice = FakeVoice(graph)
    bot = make_voice_bot(graph, voice, [], stt_api_key="")
    run(bot.handle_payload(payload(voice_msg("wamid.v3"))))
    assert graph.texts() == [whatsapp.VOICE_DISABLED_REPLY]
    assert voice.media_auth == [] and voice.stt == [] and bot.anthropic.requests == []


def test_voice_too_big(graph, monkeypatch):
    # Declared size over the limit: nothing is downloaded
    voice = FakeVoice(graph, file_size=whatsapp.MAX_MEDIA_BYTES + 1)
    bot = make_voice_bot(graph, voice, [])
    run(bot.handle_payload(payload(voice_msg("wamid.v4"))))
    assert graph.texts() == [whatsapp.VOICE_TOO_BIG_REPLY] and len(voice.media_auth) == 1

    # Size not declared, but the download itself exceeds the limit
    monkeypatch.setattr(whatsapp, "MAX_MEDIA_BYTES", 500)
    graph2 = FakeGraph()
    voice2 = FakeVoice(graph2, file_size=0)
    bot2 = make_voice_bot(graph2, voice2, [])
    run(bot2.handle_payload(payload(voice_msg("wamid.v5"))))
    assert graph2.texts() == [whatsapp.VOICE_TOO_BIG_REPLY]
    assert voice2.stt == [] and bot2.anthropic.requests == []


@pytest.mark.parametrize("stt_status,transcript", [(500, "x"), (401, "x"), (200, "   ")])
def test_voice_transcription_failure(graph, stt_status, transcript):
    voice = FakeVoice(graph, transcript=transcript, stt_status=stt_status)
    bot = make_voice_bot(graph, voice, [])
    run(bot.handle_payload(payload(voice_msg("wamid.v6"))))
    assert graph.texts() == [whatsapp.VOICE_ERROR_REPLY] and bot.anthropic.requests == []


def test_end_to_end_tool_use(graph, jf, caplog):
    bot = make_bot(graph, [
        resp("tool_use", text("Hľadám…"), tool_use("search_library", {"query": "pulp fiction"}, "t1")),
        resp("tool_use", tool_use("play", {"item_id": M1}, "t2")),
        resp("end_turn", text("Púšťam Pulp Fiction od 20. minúty 🍿")),
    ])
    with caplog.at_level(logging.INFO, logger="jellyfin_mcp"):
        run(bot.handle_payload(payload(text_msg("wamid.e2e", "pusti pulp fiction"))))
    assert graph.texts() == ["Púšťam Pulp Fiction od 20. minúty 🍿"]
    # The real MCP tool ran against (fake) Jellyfin
    _, path, params, _ = jf.calls[-1]
    assert path == f"/Sessions/{SESSION}/Playing" and params["itemIds"] == M1

    reqs = bot.anthropic.requests
    assert len(reqs) == 3
    first = reqs[0]
    assert first["model"] == "claude-sonnet-5-5"
    assert first["fallbacks"] == "default" and first["betas"] == [whatsapp.FALLBACK_BETA]
    assert first["system"][-1]["cache_control"] == {"type": "ephemeral"}
    assert "tykaj" in first["system"][0]["text"]
    names = [t["name"] for t in first["tools"]]
    assert names == sorted(names) and {"play", "search_library", "set_audio", "show_message"} <= set(names)
    assert reqs[1]["tools"] == first["tools"] and reqs[1]["system"] == first["system"]  # stable prefix
    assert first["messages"] == [{"role": "user", "content": "pusti pulp fiction"}]
    result = reqs[1]["messages"][-1]["content"][0]
    assert result["tool_use_id"] == "t1" and "is_error" not in result
    assert json.loads(result["content"])["results"][0]["id"] == M1

    logs = " ".join(r.getMessage() for r in caplog.records)
    assert ALLOWED not in logs and "pulp fiction" not in logs.lower() and TOKEN not in logs

    # Follow-up uses the remembered exchange
    bot.anthropic.responses.append(resp("end_turn", text("Jasné")))
    run(bot.handle_payload(payload(text_msg("wamid.e2e2", "a daj titulky"))))
    assert bot.anthropic.requests[-1]["messages"] == [
        {"role": "user", "content": "pusti pulp fiction"},
        {"role": "assistant", "content": "Púšťam Pulp Fiction od 20. minúty 🍿"},
        {"role": "user", "content": "a daj titulky"},
    ]


def test_tool_error_goes_back_to_claude(graph, jf):
    bot = make_bot(graph, [
        resp("tool_use", tool_use("play", {"item_id": "../Users"})),
        resp("end_turn", text("Toto neviem pustiť.")),
    ])
    run(bot.handle_payload(payload(text_msg("wamid.err", "pusti"))))
    result = bot.anthropic.requests[1]["messages"][-1]["content"][0]
    assert result["is_error"] is True and "Invalid" in result["content"]
    assert graph.texts() == ["Toto neviem pustiť."] and jf.calls == []


def test_claude_error_apology(graph):
    err = anthropic.APIConnectionError(request=httpx2.Request("POST", "https://api.anthropic.com/v1/messages"))
    bot = make_bot(graph, [err])
    run(bot.handle_payload(payload(text_msg("wamid.c", "ahoj"))))
    assert graph.texts() == [whatsapp.ERROR_REPLY]
    assert list(bot.memory.get(ALLOWED).exchanges) == []  # failed turn not remembered


def test_tool_round_limit(graph, jf):
    bot = make_bot(graph, [resp("tool_use", tool_use("whats_playing", {}, f"t{i}")) for i in range(10)])
    run(bot.handle_payload(payload(text_msg("wamid.loop", "čo beží"))))
    assert len(bot.anthropic.requests) == whatsapp.MAX_TOOL_ROUNDS
    assert graph.texts() == [whatsapp.TOO_LONG_REPLY]


def test_refusal_and_no_fallback_for_haiku(graph):
    bot = make_bot(graph, [resp("refusal")])
    bot.model = "claude-haiku-5-5"
    run(bot.handle_payload(payload(text_msg("wamid.r", "?"))))
    assert graph.texts() == [whatsapp.REFUSAL_REPLY]
    assert "fallbacks" not in bot.anthropic.requests[0]


def test_long_reply_truncated(graph):
    bot = make_bot(graph, [resp("end_turn", text("x" * 5000))])
    run(bot.handle_payload(payload(text_msg("wamid.long", "dlho"))))
    assert len(graph.texts()[0]) == whatsapp.MAX_WHATSAPP_CHARS
