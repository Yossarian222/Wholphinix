"""Phone app ("Telka"): a small installable web app (PWA) for chatting with Claude about the TV.

Served by this server under /<MCP_SECRET>/app, i.e. over the same Tailscale Funnel as the MCP
endpoint; open it in Chrome on the phone and "Add to home screen". No WhatsApp/Meta needed.

- GET  /app                 the page (static files in phone_app/)
- POST /app/chat            {"text", "conversation"} -> {"reply"}  (shared Claude agent, agent.py)
- POST /app/reset           {"conversation"} -> forget that conversation's history
- GET  /app/status          whats_playing, for the "now playing" card
- POST /app/control         {"action", "seconds"?} -> the control tool directly (remote buttons,
                            no Claude call, so instant and free)

The secret path segment is the only authentication (like /mcp), so the page sends
Referrer-Policy: no-referrer and is never indexed. Chat needs ANTHROPIC_API_KEY (+ optional
CLAUDE_MODEL, CLAUDE_EFFORT); PHONE_APP=0 turns the whole app off (404).

Logging: never message text or replies at INFO.
"""

from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Any

from starlette.requests import Request
from starlette.responses import JSONResponse, PlainTextResponse, Response

from .agent import DEFAULT_EFFORT, DEFAULT_MODEL, ChatAgent, tool_result_text

log = logging.getLogger("jellyfin_mcp.phone")

STATIC_DIR = Path(__file__).parent / "phone_app"
STATIC_FILES = {
    "manifest.webmanifest": "application/manifest+json",
    "app.js": "text/javascript; charset=utf-8",
    "icon-192.png": "image/png",
    "icon-512.png": "image/png",
}
MAX_TEXT_CHARS = 2000
MAX_BODY_BYTES = 16 * 1024
CONVERSATION_RE = re.compile(r"[A-Za-z0-9_-]{8,64}")
CONTROL_ACTIONS = {"pause", "resume", "toggle", "stop", "forward", "back", "next", "previous"}
NO_KEY_REPLY = "Chat ešte nemám zapnutý - v Portaineri chýba ANTHROPIC_API_KEY 🔑"

SECURITY_HEADERS = {
    # The URL contains the secret: never send it to another site
    "Referrer-Policy": "no-referrer",
    "X-Robots-Tag": "noindex, nofollow",
    "X-Content-Type-Options": "nosniff",
    "Content-Security-Policy": (
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
        "img-src 'self' data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'"
    ),
}

PHONE_SYSTEM = """\
Si domáci asistent v mobilnej appke „Telka“. Ovládaš televízor v obývačke s aplikáciou \
Wholphinix (Jellyfin): púšťaš filmy a seriály, prepínaš titulky a dabing, pauzuješ, pretáčaš, \
odporúčaš, čo pozerať, a posielaš správy na TV. Na všetko máš nástroje - používaj ich, \
nehádaj z hlavy, čo je v knižnici alebo čo práve beží.

Štýl: píš po slovensky, tykaj, odpovedaj krátko a priateľsky ako v chate (jedna-dve vety, \
emoji striedmo). Odpovede si používateľ môže nechať prečítať nahlas, takže žiadne nadpisy, \
tabuľky ani markdown; ak vypisuješ viac možností, daj krátky zoznam s pomlčkami.
Správy často diktuje hlasom, takže názvy môžu byť skomolené - hľadaj aj podobné varianty.
Keď je požiadavka nejasná, opýtaj sa - napr. keď sedí viac verzií filmu, vymenuj ich s rokmi \
a nechaj vybrať. Po akcii stručne potvrď, čo si spravil (napr. „Púšťam Pelíšky od 20. minúty 🍿“).
Keď nástroj vráti chybu, povedz to ľudsky jednou vetou.

Podrobný postup pre nástroje (anglicky, platí aj tu; odpovedaj ale vždy po slovensky):
"""


class PhoneAgent(ChatAgent):
    label = "Phone"


_mcp: Any = None
_system_prompt = PHONE_SYSTEM
_agent: PhoneAgent | None = None


def enabled() -> bool:
    return os.environ.get("PHONE_APP", "").strip().lower() not in {"0", "false", "no", "off"}


def set_agent(agent: PhoneAgent | None) -> None:
    """For tests."""
    global _agent
    _agent = agent


def get_agent() -> PhoneAgent | None:
    """The chat agent, created on first use; None without ANTHROPIC_API_KEY."""
    global _agent
    if _agent is None:
        env = lambda n, d="": os.environ.get(n, "").strip() or d  # noqa: E731
        key = env("ANTHROPIC_API_KEY")
        if not key:
            return None
        _agent = PhoneAgent(
            _mcp,
            _system_prompt,
            api_key=key,
            model=env("CLAUDE_MODEL", DEFAULT_MODEL),
            effort=env("CLAUDE_EFFORT", DEFAULT_EFFORT),
        )
        log.info("Phone app chat enabled (model %s)", _agent.model)
    return _agent


def _json(data: Any, status: int = 200) -> JSONResponse:
    return JSONResponse(data, status_code=status, headers={**SECURITY_HEADERS, "Cache-Control": "no-store"})


def _not_found() -> Response:
    return PlainTextResponse("not found", status_code=404)


async def _read_json(request: Request) -> dict[str, Any] | None:
    body = await request.body()
    if len(body) > MAX_BODY_BYTES:
        return None
    try:
        data = json.loads(body or b"{}")
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


async def _call_tool(name: str, args: dict[str, Any]) -> dict[str, Any]:
    result = await _mcp.call_tool(name, args)
    try:
        data = json.loads(tool_result_text(result))
    except ValueError:
        return {"message": tool_result_text(result)}
    return data if isinstance(data, dict) else {"result": data}


# ---- routes ----


async def page(request: Request) -> Response:
    if not enabled():
        return _not_found()
    html = (STATIC_DIR / "index.html").read_bytes()
    return Response(html, media_type="text/html; charset=utf-8", headers={**SECURITY_HEADERS, "Cache-Control": "no-cache"})


async def static(request: Request) -> Response:
    name = request.path_params.get("name", "")
    if not enabled() or name not in STATIC_FILES:
        return _not_found()
    # Icons rarely change; the rest revalidates so a redeploy shows up right away
    cache = "public, max-age=604800" if name.endswith(".png") else "no-cache"
    return Response(
        (STATIC_DIR / name).read_bytes(),
        media_type=STATIC_FILES[name],
        headers={**SECURITY_HEADERS, "Cache-Control": cache},
    )


async def chat(request: Request) -> Response:
    if not enabled():
        return _not_found()
    data = await _read_json(request)
    if data is None:
        return _json({"error": "bad request"}, 400)
    text = str(data.get("text") or "").strip()
    conversation = str(data.get("conversation") or "")
    if not text or not CONVERSATION_RE.fullmatch(conversation):
        return _json({"error": "bad request"}, 400)
    if len(text) > MAX_TEXT_CHARS:
        return _json({"reply": f"To je na mňa pridlhé 😅 Skús to skrátiť (max {MAX_TEXT_CHARS} znakov)."})
    agent = get_agent()
    if agent is None:
        return _json({"reply": NO_KEY_REPLY})
    log.info("Phone: message (%d chars)", len(text))
    reply = await agent.answer("phone:" + conversation, text)
    return _json({"reply": reply})


async def reset(request: Request) -> Response:
    if not enabled():
        return _not_found()
    data = await _read_json(request) or {}
    conversation = str(data.get("conversation") or "")
    agent = get_agent()
    if agent is not None and CONVERSATION_RE.fullmatch(conversation):
        agent.memory.forget("phone:" + conversation)
    return _json({"ok": True})


async def status(request: Request) -> Response:
    if not enabled():
        return _not_found()
    try:
        return _json(await _call_tool("whats_playing", {}))
    except Exception as ex:
        log.info("Phone: status failed: %s", type(ex).__name__)
        return _json({"connected": None, "message": str(ex)[:200]})


async def control(request: Request) -> Response:
    if not enabled():
        return _not_found()
    data = await _read_json(request)
    action = str((data or {}).get("action") or "")
    if action not in CONTROL_ACTIONS:
        return _json({"error": "bad request"}, 400)
    args: dict[str, Any] = {"action": action}
    seconds = (data or {}).get("seconds")
    if isinstance(seconds, (int, float)) and not isinstance(seconds, bool) and 0 < seconds <= 3600:
        args["seconds"] = float(seconds)
    log.info("Phone: control %s", action)
    try:
        return _json(await _call_tool("control", args))
    except Exception as ex:  # e.g. Wholphinix not connected: show it on the phone
        return _json({"ok": False, "error": str(ex)[:200]})


def register(mcp: Any, instructions: str) -> None:
    """Add the /app routes to the FastMCP Starlette app (served under /<MCP_SECRET>/)."""
    global _mcp, _system_prompt
    _mcp = mcp
    _system_prompt = PHONE_SYSTEM + instructions
    mcp.custom_route("/app", methods=["GET"])(page)
    mcp.custom_route("/app/chat", methods=["POST"])(chat)
    mcp.custom_route("/app/reset", methods=["POST"])(reset)
    mcp.custom_route("/app/status", methods=["GET"])(status)
    mcp.custom_route("/app/control", methods=["POST"])(control)
    mcp.custom_route("/app/{name}", methods=["GET"])(static)
