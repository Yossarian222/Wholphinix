"""WhatsApp bot: messages to a WhatsApp Business number are answered by Claude, which controls
the TV with the same tools the MCP server exposes.

Meta (WhatsApp Business Cloud API) calls the webhook at /<MCP_SECRET>/whatsapp:
- GET: subscription verification (hub.verify_token must equal WHATSAPP_VERIFY_TOKEN)
- POST: events, signed with X-Hub-Signature-256 (HMAC-SHA256 of the body, key = app secret).
  We answer 200 right away and process in a background task, because Meta retries slow
  deliveries.

Each allowed text message goes to the Anthropic Messages API with the MCP tools as Claude tools
(a manual tool-use loop, max MAX_TOOL_ROUNDS model calls); tool calls go through
FastMCP.call_tool, i.e. exactly the code the MCP clients run. The reply is sent back through
the Graph API.

Voice notes (type "audio") are downloaded from the Graph API (media id -> url -> bytes, size
capped), transcribed with an OpenAI-compatible /audio/transcriptions endpoint (OpenAI Whisper or
a local faster-whisper-server/speaches) and then handled exactly like a text message; the reply
starts with "Rozumel som: „…“". Enabled only when STT_API_KEY or a custom STT_URL is set.

Logging: never tokens, full phone numbers, message text, transcripts or audio at INFO (only the
last 3 digits of numbers).
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import logging
import os
import re
import time
from collections import OrderedDict, deque
from dataclasses import dataclass, field
from typing import Any

import httpx
from starlette.requests import Request
from starlette.responses import PlainTextResponse, Response

log = logging.getLogger("jellyfin_mcp.whatsapp")

DEFAULT_MODEL = "claude-sonnet-5-5"
DEFAULT_GRAPH_VERSION = "v23.0"
DEFAULT_EFFORT = "low"  # chat with short tool sequences; raise via CLAUDE_EFFORT if needed
MAX_TOOL_ROUNDS = 6
MAX_TOKENS = 4096
MAX_WHATSAPP_CHARS = 4096
HISTORY_EXCHANGES = 10
HISTORY_TTL_SECONDS = 30 * 60
DEDUP_SIZE = 500
DEFAULT_STT_URL = "https://api.openai.com/v1/audio/transcriptions"
DEFAULT_STT_MODEL = "whisper-1"
DEFAULT_STT_LANGUAGE = "sk"
MAX_MEDIA_BYTES = 16 * 1024 * 1024  # WhatsApp's own limit for audio
MEDIA_TIMEOUT = httpx.Timeout(30.0, connect=10.0)
STT_TIMEOUT = httpx.Timeout(120.0, connect=10.0)  # a local CPU Whisper can be slow
# Server-side refusal fallback (Claude API, beta): only these models accept fallbacks="default"
FALLBACK_MODELS = {"claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1"}
FALLBACK_BETA = "server-side-fallback-2026-07-01"

TEXT_ONLY_REPLY = "Zatiaľ rozumiem len textu 🙂"
ERROR_REPLY = "Prepáč, niečo sa pokazilo a nepodarilo sa mi to vybaviť 😕 Skús to o chvíľu znova."
REFUSAL_REPLY = "Prepáč, s týmto ti nepomôžem."
TOO_LONG_REPLY = "Prepáč, zamotal som sa v tom 😅 Skús to napísať inak alebo po kúskoch."
VOICE_DISABLED_REPLY = "Hlasové správy zatiaľ nemám zapnuté 🙉 Napíš mi to, prosím, textom."
VOICE_ERROR_REPLY = "Prepáč, hlasovku sa mi nepodarilo rozpoznať 😕 Skús to znova alebo mi to napíš."
VOICE_TOO_BIG_REPLY = "Prepáč, tá hlasovka je na mňa pridlhá 😅 Skús kratšiu alebo mi to napíš."
HEARD_PREFIX = "Rozumel som: „{}“"

WHATSAPP_SYSTEM = """\
Si domáci asistent v WhatsAppe. Ovládaš televízor v obývačke s aplikáciou Wholphinix \
(Jellyfin): púšťaš filmy a seriály, prepínaš titulky a dabing, pauzuješ, pretáčaš, \
odporúčaš, čo pozerať, a posielaš správy na TV. Na všetko máš nástroje - používaj ich, \
nehádaj z hlavy, čo je v knižnici alebo čo práve beží.

Štýl: píš po slovensky, tykaj, odpovedaj krátko a priateľsky ako v chate (jedna-dve vety, \
emoji striedmo). Žiadne nadpisy ani tabuľky; ak vypisuješ viac možností, daj krátky zoznam.
Keď je požiadavka nejasná, opýtaj sa - napr. keď sedí viac verzií filmu, vymenuj ich s rokmi \
a nechaj vybrať. Po akcii stručne potvrď, čo si spravil (napr. „Púšťam Pelíšky od 20. minúty 🍿“).
Keď nástroj vráti chybu, povedz to ľudsky jednou vetou.

Podrobný postup pre nástroje (anglicky, platí aj tu; odpovedaj ale vždy po slovensky):
"""


def mask_number(number: str) -> str:
    """Only the last 3 digits, for logs."""
    return "…" + number[-3:] if number else "?"


def normalize_number(raw: str) -> str:
    """'+421 905 123 456' / '00421905123456' / '421905123456' -> '421905123456'."""
    digits = re.sub(r"\D", "", raw or "")
    if digits.startswith("00"):
        digits = digits[2:]
    return digits


@dataclass(frozen=True)
class Config:
    token: str
    phone_number_id: str
    app_secret: str
    verify_token: str
    allowed_numbers: frozenset[str]
    anthropic_api_key: str
    model: str = DEFAULT_MODEL
    graph_version: str = DEFAULT_GRAPH_VERSION
    effort: str = DEFAULT_EFFORT
    stt_url: str = DEFAULT_STT_URL
    stt_api_key: str = ""
    stt_model: str = DEFAULT_STT_MODEL
    stt_language: str = DEFAULT_STT_LANGUAGE

    REQUIRED = (
        "WHATSAPP_TOKEN",
        "WHATSAPP_PHONE_NUMBER_ID",
        "WHATSAPP_APP_SECRET",
        "WHATSAPP_VERIFY_TOKEN",
        "WHATSAPP_ALLOWED_NUMBERS",
        "ANTHROPIC_API_KEY",
    )

    @classmethod
    def missing(cls) -> list[str]:
        return [name for name in cls.REQUIRED if not os.environ.get(name, "").strip()]

    @classmethod
    def from_env(cls) -> Config | None:
        """None when the WhatsApp bot is not configured (the endpoints then return 404)."""
        if cls.missing():
            return None
        env = lambda n, d="": os.environ.get(n, "").strip() or d  # noqa: E731
        allowed = frozenset(
            n for n in (normalize_number(x) for x in env("WHATSAPP_ALLOWED_NUMBERS").split(",")) if n
        )
        version = env("GRAPH_VERSION", DEFAULT_GRAPH_VERSION)
        if not version.startswith("v"):
            version = "v" + version
        return cls(
            token=env("WHATSAPP_TOKEN"),
            phone_number_id=env("WHATSAPP_PHONE_NUMBER_ID"),
            app_secret=env("WHATSAPP_APP_SECRET"),
            verify_token=env("WHATSAPP_VERIFY_TOKEN"),
            allowed_numbers=allowed,
            anthropic_api_key=env("ANTHROPIC_API_KEY"),
            model=env("CLAUDE_MODEL", DEFAULT_MODEL),
            graph_version=version,
            effort=env("CLAUDE_EFFORT", DEFAULT_EFFORT),
            stt_url=env("STT_URL", DEFAULT_STT_URL),
            stt_api_key=env("STT_API_KEY"),
            stt_model=env("STT_MODEL", DEFAULT_STT_MODEL),
            stt_language=env("STT_LANGUAGE", DEFAULT_STT_LANGUAGE),
        )

    @property
    def voice_enabled(self) -> bool:
        """Voice notes need a key (OpenAI) or an own endpoint (local Whisper, key optional)."""
        return bool(self.stt_api_key) or self.stt_url != DEFAULT_STT_URL

    def __repr__(self) -> str:  # never leak secrets into logs/tracebacks
        return (
            f"Config(model={self.model!r}, graph_version={self.graph_version!r}, "
            f"allowed_numbers={len(self.allowed_numbers)}, voice={self.voice_enabled})"
        )


def verify_signature(app_secret: str, body: bytes, header: str | None) -> bool:
    """X-Hub-Signature-256: 'sha256=<hex HMAC-SHA256 of the raw body>'."""
    if not header or not header.startswith("sha256="):
        return False
    expected = hmac.new(app_secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, header[len("sha256="):].strip().lower())


class Deduper:
    """Remembers the last `size` message ids."""

    def __init__(self, size: int = DEDUP_SIZE) -> None:
        self.size = size
        self._seen: OrderedDict[str, None] = OrderedDict()

    def seen(self, message_id: str) -> bool:
        """True if already seen; otherwise records it and returns False."""
        if message_id in self._seen:
            self._seen.move_to_end(message_id)
            return True
        self._seen[message_id] = None
        while len(self._seen) > self.size:
            self._seen.popitem(last=False)
        return False


@dataclass
class Conversation:
    exchanges: deque[tuple[str, str]] = field(default_factory=lambda: deque(maxlen=HISTORY_EXCHANGES))
    last_active: float = 0.0
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


class Memory:
    """Per-number history of (user text, final assistant text), in process memory.

    Only plain text is kept between turns (no tool calls / thinking blocks), so dropping the
    oldest exchange never edits a replayed thinking block.
    """

    def __init__(self, ttl: float = HISTORY_TTL_SECONDS, clock=time.monotonic) -> None:
        self.ttl = ttl
        self.clock = clock
        self._convs: dict[str, Conversation] = {}

    def get(self, number: str) -> Conversation:
        now = self.clock()
        # Drop expired conversations (also of other numbers) so memory doesn't grow
        for n in [n for n, c in self._convs.items() if now - c.last_active > self.ttl and not c.lock.locked()]:
            del self._convs[n]
        conv = self._convs.get(number)
        if conv is None:
            conv = self._convs[number] = Conversation(last_active=now)
        return conv

    def touch(self, conv: Conversation) -> None:
        conv.last_active = self.clock()


def _block_get(block: Any, name: str, default: Any = None) -> Any:
    if isinstance(block, dict):
        return block.get(name, default)
    return getattr(block, name, default)


class VoiceError(Exception):
    """A voice note could not be turned into text; `reply` is what the user gets."""

    def __init__(self, reason: str, reply: str = VOICE_ERROR_REPLY) -> None:
        super().__init__(reason)
        self.reply = reply


def tool_result_text(result: Any) -> str:
    """FastMCP.call_tool result -> text for a tool_result block."""
    if isinstance(result, tuple) and len(result) == 2 and result[1] is not None:
        return json.dumps(result[1], ensure_ascii=False)
    if isinstance(result, dict):
        return json.dumps(result, ensure_ascii=False)
    blocks = result[0] if isinstance(result, tuple) else result
    return "\n".join(str(_block_get(b, "text", "")) for b in blocks or []) or "{}"


class WhatsAppBot:
    def __init__(
        self,
        config: Config,
        mcp: Any,
        system_prompt: str,
        anthropic_client: Any | None = None,
        graph_transport: httpx.AsyncBaseTransport | None = None,
        http_transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.config = config
        self.mcp = mcp
        self.system_prompt = system_prompt
        if anthropic_client is None:
            from anthropic import AsyncAnthropic

            anthropic_client = AsyncAnthropic(api_key=config.anthropic_api_key)
        self.anthropic = anthropic_client
        self.graph = httpx.AsyncClient(
            base_url=f"https://graph.facebook.com/{config.graph_version}",
            timeout=20.0,
            transport=graph_transport,
        )
        # Media downloads (lookaside.fbsbx.com URLs) and the speech-to-text endpoint
        self.http = httpx.AsyncClient(timeout=MEDIA_TIMEOUT, transport=http_transport)
        self.dedup = Deduper()
        self.memory = Memory()
        self._tools: list[dict[str, Any]] | None = None
        self._tasks: set[asyncio.Task] = set()

    # ---- webhook entry points ----

    def schedule(self, payload: dict[str, Any]) -> asyncio.Task:
        """Process a webhook payload in the background (the HTTP response goes out right away)."""
        task = asyncio.create_task(self.handle_payload(payload))
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        return task

    async def handle_payload(self, payload: dict[str, Any]) -> None:
        jobs = []
        for entry in payload.get("entry") or []:
            for change in entry.get("changes") or []:
                if change.get("field") != "messages":
                    continue
                value = change.get("value") or {}
                # Successful statuses (sent/delivered/read) are ignored; a failed one says why a reply never arrived
                for status in value.get("statuses") or []:
                    if status.get("status") == "failed":
                        for err in status.get("errors") or [{}]:
                            log.warning(
                                "WhatsApp: reply to %s not delivered: %s %s %s",
                                mask_number(str(status.get("recipient_id") or "")),
                                err.get("code"),
                                err.get("title"),
                                str((err.get("error_data") or {}).get("details") or "")[:200],
                            )
                for msg in value.get("messages") or []:
                    jobs.append(self._handle_message(msg))
        if jobs:
            await asyncio.gather(*jobs)

    async def _handle_message(self, msg: dict[str, Any]) -> None:
        try:
            number = normalize_number(str(msg.get("from") or ""))
            if number not in self.config.allowed_numbers:
                log.info("WhatsApp: ignoring message from a number not allowed (%s)", mask_number(number))
                return
            msg_id = str(msg.get("id") or "")
            if not msg_id or self.dedup.seen(msg_id):
                log.debug("WhatsApp: duplicate message %s", msg_id)
                return
            kind = msg.get("type")
            log.info("WhatsApp: %s message from %s", kind, mask_number(number))
            await self.mark_read(msg_id)
            if kind == "reaction":
                return
            if kind == "audio":
                await self._handle_voice(number, msg.get("audio") or {})
                return
            if kind != "text":
                await self.send_text(number, TEXT_ONLY_REPLY)
                return
            text = str((msg.get("text") or {}).get("body") or "").strip()
            if not text:
                return
            reply = await self.answer(number, text)
            await self.send_text(number, reply)
        except Exception:
            log.exception("WhatsApp: failed to handle a message")

    async def _handle_voice(self, number: str, audio: dict[str, Any]) -> None:
        if not self.config.voice_enabled:
            await self.send_text(number, VOICE_DISABLED_REPLY)
            return
        try:
            data = await self.download_media(str(audio.get("id") or ""))
            text = await self.transcribe(data)
        except VoiceError as ex:
            log.warning("WhatsApp: voice note from %s failed: %s", mask_number(number), ex)
            await self.send_text(number, ex.reply)
            return
        log.info("WhatsApp: voice note from %s transcribed (%d chars)", mask_number(number), len(text))
        reply = await self.answer(number, text)
        await self.send_text(number, HEARD_PREFIX.format(text) + "\n\n" + reply)

    # ---- voice: media download + speech to text ----

    async def download_media(self, media_id: str) -> bytes:
        """Graph API: GET /<media_id> -> {"url", "file_size", ...}, then GET url (same Bearer)."""
        if not re.fullmatch(r"[0-9A-Za-z_-]{1,128}", media_id):
            raise VoiceError("invalid media id")
        try:
            r = await self.graph.get(f"/{media_id}", headers=self._headers())
        except httpx.HTTPError as ex:
            raise VoiceError(f"media lookup: {type(ex).__name__}") from None
        if r.status_code >= 400:
            raise VoiceError(f"media lookup: HTTP {r.status_code}")
        try:
            meta = r.json()
        except ValueError:
            raise VoiceError("media lookup: not JSON") from None
        url = str(meta.get("url") or "") if isinstance(meta, dict) else ""
        if not url.startswith("https://"):
            raise VoiceError("media lookup: no https url")
        try:
            declared = int(meta.get("file_size") or 0)
        except (TypeError, ValueError):
            declared = 0
        if declared > MAX_MEDIA_BYTES:
            raise VoiceError(f"media too big ({declared} B)", VOICE_TOO_BIG_REPLY)

        try:
            async with self.http.stream("GET", url, headers=self._headers()) as resp:
                if resp.status_code >= 400:
                    raise VoiceError(f"media download: HTTP {resp.status_code}")
                try:
                    length = int(resp.headers.get("content-length") or 0)
                except ValueError:
                    length = 0
                if length > MAX_MEDIA_BYTES:
                    raise VoiceError(f"media too big ({length} B)", VOICE_TOO_BIG_REPLY)
                buf = bytearray()
                async for chunk in resp.aiter_bytes():
                    buf += chunk
                    if len(buf) > MAX_MEDIA_BYTES:
                        raise VoiceError("media too big (stream)", VOICE_TOO_BIG_REPLY)
        except httpx.HTTPError as ex:
            raise VoiceError(f"media download: {type(ex).__name__}") from None
        if not buf:
            raise VoiceError("media download: empty")
        return bytes(buf)

    async def transcribe(self, data: bytes) -> str:
        """OpenAI-compatible POST /v1/audio/transcriptions (OpenAI, faster-whisper-server, speaches)."""
        c = self.config
        headers = {"Authorization": f"Bearer {c.stt_api_key}"} if c.stt_api_key else {}
        form = {"model": c.stt_model, "response_format": "json"}
        if c.stt_language:
            form["language"] = c.stt_language
        try:
            r = await self.http.post(
                c.stt_url,
                data=form,
                files={"file": ("voice.ogg", data, "audio/ogg")},
                headers=headers,
                timeout=STT_TIMEOUT,
            )
        except httpx.HTTPError as ex:
            raise VoiceError(f"transcription: {type(ex).__name__}") from None
        if r.status_code >= 400:
            # Error bodies are messages like "invalid api key", never the key or the audio
            raise VoiceError(f"transcription: HTTP {r.status_code} {r.text[:200]}")
        try:
            body = r.json()
            text = str(body.get("text") or "") if isinstance(body, dict) else ""
        except ValueError:
            text = r.text  # response_format=text servers
        text = " ".join(text.split())
        if not text:
            raise VoiceError("transcription: empty")
        return text

    # ---- Claude ----

    async def tool_definitions(self) -> list[dict[str, Any]]:
        """The MCP tools as Claude tool definitions (stable order, so the cached prefix stays the same)."""
        if self._tools is None:
            tools = sorted(await self.mcp.list_tools(), key=lambda t: t.name)
            self._tools = [
                {"name": t.name, "description": t.description or "", "input_schema": t.inputSchema}
                for t in tools
            ]
        return self._tools

    def _request_params(self, tools: list[dict[str, Any]], messages: list[dict[str, Any]]) -> dict[str, Any]:
        params: dict[str, Any] = {
            "model": self.config.model,
            "max_tokens": MAX_TOKENS,
            # Render order is tools -> system -> messages: the breakpoint on the system block
            # caches tools + system; the top-level one caches the growing tool-loop messages.
            "system": [{"type": "text", "text": self.system_prompt, "cache_control": {"type": "ephemeral"}}],
            "tools": tools,
            "messages": messages,
            "cache_control": {"type": "ephemeral"},
            "output_config": {"effort": self.config.effort},
        }
        if self.config.model in FALLBACK_MODELS:
            params["betas"] = [FALLBACK_BETA]
            params["fallbacks"] = "default"
        return params

    async def _run_tool(self, name: str, args: Any) -> tuple[str, bool]:
        try:
            result = await self.mcp.call_tool(name, args if isinstance(args, dict) else {})
            return tool_result_text(result), False
        except Exception as ex:  # tool errors go back to Claude, which explains them
            log.info("WhatsApp: tool %s failed: %s", name, type(ex).__name__)
            return f"Error: {ex}", True

    async def run_agent(self, history: list[tuple[str, str]], text: str) -> str:
        import anthropic

        tools = await self.tool_definitions()
        messages: list[dict[str, Any]] = []
        for user_text, assistant_text in history:
            messages.append({"role": "user", "content": user_text})
            messages.append({"role": "assistant", "content": assistant_text})
        messages.append({"role": "user", "content": text})

        for _ in range(MAX_TOOL_ROUNDS):
            try:
                response = await self.anthropic.beta.messages.create(**self._request_params(tools, messages))
            except anthropic.RateLimitError:
                log.warning("WhatsApp: Claude rate limited")
                return ERROR_REPLY
            except anthropic.APIStatusError as ex:
                # The API's message says what is wrong (no secrets in it), e.g. low credit or a bad parameter
                log.error("WhatsApp: Claude API error %s: %s", ex.status_code, str(ex.message)[:300])
                return ERROR_REPLY
            except anthropic.APIConnectionError:
                log.error("WhatsApp: cannot reach the Claude API")
                return ERROR_REPLY

            stop = response.stop_reason
            if stop == "refusal":
                return REFUSAL_REPLY
            if stop in ("tool_use", "pause_turn"):
                # Append the whole content unchanged (thinking blocks included): append-only
                messages.append({"role": "assistant", "content": response.content})
                if stop == "pause_turn":
                    continue
                results = []
                # Sequentially: TV commands depend on order (play, then set_audio)
                for block in response.content:
                    if _block_get(block, "type") != "tool_use":
                        continue
                    name = _block_get(block, "name")
                    log.info("WhatsApp: tool %s", name)
                    content, is_error = await self._run_tool(name, _block_get(block, "input"))
                    result = {"type": "tool_result", "tool_use_id": _block_get(block, "id"), "content": content}
                    if is_error:
                        result["is_error"] = True
                    results.append(result)
                messages.append({"role": "user", "content": results})
                continue
            reply = "\n".join(
                _block_get(b, "text") for b in response.content if _block_get(b, "type") == "text"
            ).strip()
            return reply or "👍"
        log.warning("WhatsApp: gave up after %d tool rounds", MAX_TOOL_ROUNDS)
        return TOO_LONG_REPLY

    async def answer(self, number: str, text: str) -> str:
        conv = self.memory.get(number)
        async with conv.lock:  # one turn at a time per number, in order
            try:
                reply = await self.run_agent(list(conv.exchanges), text)
            except Exception:
                log.exception("WhatsApp: agent failed")
                return ERROR_REPLY
            if reply not in (ERROR_REPLY, TOO_LONG_REPLY):
                conv.exchanges.append((text, reply))
            self.memory.touch(conv)
            return reply

    # ---- Graph API ----

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.config.token}"}

    async def _post(self, body: dict[str, Any], what: str) -> bool:
        try:
            r = await self.graph.post(
                f"/{self.config.phone_number_id}/messages", json=body, headers=self._headers()
            )
        except httpx.HTTPError as ex:
            log.error("WhatsApp: %s failed: %s", what, type(ex).__name__)
            return False
        if r.status_code >= 400:
            # The error body has a code/message, never the token
            log.error("WhatsApp: %s failed: HTTP %s %s", what, r.status_code, r.text[:300])
            return False
        return True

    async def send_text(self, to: str, text: str) -> bool:
        text = text.strip() or "👍"
        if len(text) > MAX_WHATSAPP_CHARS:
            text = text[: MAX_WHATSAPP_CHARS - 1].rstrip() + "…"
        return await self._post(
            {"messaging_product": "whatsapp", "to": to, "type": "text", "text": {"body": text}},
            "send",
        )

    async def mark_read(self, message_id: str) -> bool:
        return await self._post(
            {"messaging_product": "whatsapp", "status": "read", "message_id": message_id},
            "mark read",
        )


# ---- wiring into the Starlette app ----

_bot: WhatsAppBot | None = None
_mcp: Any = None
_system_prompt = WHATSAPP_SYSTEM


def set_bot(bot: WhatsAppBot | None) -> None:
    """For tests."""
    global _bot
    _bot = bot


def get_bot() -> WhatsAppBot | None:
    """The bot, created from the environment on first use; None if WhatsApp is not configured."""
    global _bot
    if _bot is None:
        config = Config.from_env()
        if config is None:
            return None
        _bot = WhatsAppBot(config, _mcp, _system_prompt)
        log.info("WhatsApp bot enabled: %r", config)
    return _bot


async def webhook(request: Request) -> Response:
    bot = get_bot()
    if bot is None:
        return PlainTextResponse("not found", status_code=404)
    if request.method == "GET":
        q = request.query_params
        token = q.get("hub.verify_token") or ""
        if q.get("hub.mode") == "subscribe" and hmac.compare_digest(
            token.encode(), bot.config.verify_token.encode()
        ):
            log.info("WhatsApp: webhook verified")
            return PlainTextResponse(q.get("hub.challenge") or "")
        log.warning("WhatsApp: webhook verification rejected")
        return PlainTextResponse("forbidden", status_code=403)

    body = await request.body()
    if not verify_signature(bot.config.app_secret, body, request.headers.get("x-hub-signature-256")):
        log.warning("WhatsApp: invalid or missing X-Hub-Signature-256")
        return PlainTextResponse("forbidden", status_code=403)
    try:
        payload = json.loads(body)
    except ValueError:
        return PlainTextResponse("bad request", status_code=400)
    if isinstance(payload, dict):
        bot.schedule(payload)
    return PlainTextResponse("ok")


def register(mcp: Any, instructions: str) -> None:
    """Add GET/POST /whatsapp to the FastMCP Starlette app (served under /<MCP_SECRET>/)."""
    global _mcp, _system_prompt
    _mcp = mcp
    _system_prompt = WHATSAPP_SYSTEM + instructions
    mcp.custom_route("/whatsapp", methods=["GET", "POST"])(webhook)
    missing = Config.missing()
    if missing and len(missing) < len(Config.REQUIRED):
        # Partially configured: say which variable names are missing (never values)
        log.warning("WhatsApp bot disabled, missing: %s", ", ".join(missing))
