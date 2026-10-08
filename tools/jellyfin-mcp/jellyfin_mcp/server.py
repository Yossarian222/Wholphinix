"""MCP server that lets Claude control Jellyfin playback on the Wholphinix TV session.

Exposed over streamable HTTP at /<MCP_SECRET>/mcp. The secret path segment is the only
authentication, so keep it long and random and never commit it.
"""

from __future__ import annotations

import hmac
import logging
import os
from typing import Any, Literal

from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings
from starlette.types import ASGIApp, Receive, Scope, Send

from .jellyfin import (
    TICKS_PER_SECOND,
    JellyfinClient,
    JellyfinError,
    NoTargetSession,
    Stream,
    pick_stream,
    summarize_item,
)

log = logging.getLogger("jellyfin_mcp")

INSTRUCTIONS = """\
You control the Jellyfin media server and the Wholphinix app on the user's living-room TV.
The user usually writes in Slovak. Typical requests: play a movie or the next episode of a
series, switch subtitles or dubbing (audio language), pause, rewind, stop.

Workflow:
- To play something, call search_library first, then play with the chosen id. If several
  results fit equally (e.g. remakes), ask which one, mentioning the year.
- For a series without a specific episode, play(series_id) continues with the next unwatched
  episode. For "S2E5" style requests use play_episode.
- For subtitles/dubbing call set_subtitles / set_audio with a language ("slovenčina", "cz",
  "eng") or an index from whats_playing. "titulky preč/vypni" means set_subtitles("off").
- "dabing" means audio track language.
- If a tool says Wholphinix is not connected, tell the user to open the app on the TV.
Keep replies short.
"""


def _env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None or value == "":
        raise RuntimeError(f"Missing required environment variable {name}")
    return value


_client: JellyfinClient | None = None


def client() -> JellyfinClient:
    global _client
    if _client is None:
        _client = JellyfinClient(
            base_url=_env("JELLYFIN_URL"),
            api_key=_env("JELLYFIN_API_KEY"),
            username=_env("JELLYFIN_USER"),
            target_client=os.environ.get("TARGET_CLIENT", "Wholphin"),
            target_device=os.environ.get("TARGET_DEVICE") or None,
        )
    return _client


def set_client(c: JellyfinClient) -> None:
    """For tests."""
    global _client
    _client = c


mcp = FastMCP(
    "jellyfin-tv",
    instructions=INSTRUCTIONS,
    stateless_http=True,
    json_response=True,
    streamable_http_path="/mcp",
    # Auth is the secret path; the Host header is the public Funnel name, so skip host checks
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)

KIND_TYPES = {
    "movie": ["Movie"],
    "series": ["Series"],
    "episode": ["Episode"],
    "any": ["Movie", "Series", "Episode"],
}


@mcp.tool()
async def search_library(
    query: str, kind: Literal["any", "movie", "series", "episode"] = "any"
) -> dict[str, Any]:
    """Search the Jellyfin library by title. Returns ids to use with play/play_episode.

    Search with the original or the Slovak/Czech title; if nothing is found, retry with the
    other language title or fewer words.
    """
    items = await client().search(query, KIND_TYPES[kind])
    return {"results": [summarize_item(i) for i in items]}


@mcp.tool()
async def continue_watching() -> dict[str, Any]:
    """List partially watched videos and next-up episodes of series in progress."""
    c = client()
    return {
        "in_progress": [summarize_item(i) for i in await c.resume_items()],
        "next_up": [summarize_item(i) for i in await c.next_up(limit=8)],
    }


async def _resume_seconds(item: dict[str, Any], from_start: bool) -> float:
    if from_start:
        return 0.0
    ticks = (item.get("UserData") or {}).get("PlaybackPositionTicks") or 0
    return ticks / TICKS_PER_SECOND


@mcp.tool()
async def play(item_id: str, from_start: bool = False) -> dict[str, Any]:
    """Play a movie, episode or series on the TV.

    Movies/episodes resume where they were left off unless from_start=True.
    For a series id, plays the next unwatched episode (or S1E1 if none was started).
    """
    c = client()
    session = await c.target_session()
    item = await c.item(item_id)
    if item.get("Type") == "Series":
        nxt = await c.next_up(series_id=item_id)
        if not nxt:
            eps = await c.episodes(item_id)
            if not eps:
                raise JellyfinError(f"Series '{item.get('Name')}' has no episodes")
            nxt = [eps[0]]
        item = nxt[0]
    start = await _resume_seconds(item, from_start)
    await c.play(session["Id"], item["Id"], start)
    return {"playing": summarize_item(item), "start_min": round(start / 60, 1)}


@mcp.tool()
async def play_episode(
    series_id: str, season: int, episode: int, from_start: bool = False
) -> dict[str, Any]:
    """Play a specific episode, e.g. season=2, episode=5 for S02E05."""
    c = client()
    session = await c.target_session()
    eps = await c.episodes(series_id, season=season)
    match = next((e for e in eps if e.get("IndexNumber") == episode), None)
    if match is None:
        available = sorted(e.get("IndexNumber") for e in eps if e.get("IndexNumber") is not None)
        raise JellyfinError(f"S{season}E{episode} not found. Episodes in season {season}: {available}")
    start = await _resume_seconds(match, from_start)
    await c.play(session["Id"], match["Id"], start)
    return {"playing": summarize_item(match), "start_min": round(start / 60, 1)}


async def _now_playing() -> tuple[dict[str, Any], dict[str, Any], list[Stream]]:
    c = client()
    session = await c.target_session()
    item = session.get("NowPlayingItem")
    if not item:
        raise JellyfinError("Nothing is playing on the TV right now")
    return session, item, await c.streams(item["Id"])


@mcp.tool()
async def whats_playing() -> dict[str, Any]:
    """What is playing on the TV: title, position, pause state, and available audio/subtitle tracks."""
    try:
        session, item, streams = await _now_playing()
    except NoTargetSession as ex:
        return {"connected": False, "message": str(ex)}
    except JellyfinError as ex:
        return {"connected": True, "playing": False, "message": str(ex)}
    state = session.get("PlayState") or {}
    return {
        "connected": True,
        "playing": summarize_item(item),
        "position_min": round((state.get("PositionTicks") or 0) / TICKS_PER_SECOND / 60, 1),
        "paused": bool(state.get("IsPaused")),
        "audio_index": state.get("AudioStreamIndex"),
        "subtitle_index": state.get("SubtitleStreamIndex"),
        "audio_tracks": [s.as_dict() for s in streams if s.type == "Audio"],
        "subtitle_tracks": [s.as_dict() for s in streams if s.type == "Subtitle"],
    }


@mcp.tool()
async def set_subtitles(choice: str, forced_only: bool = False) -> dict[str, Any]:
    """Switch subtitles on the TV. choice: "off", a language ("slovenčina", "cz", "eng") or a track index.

    forced_only=True picks the forced (foreign-parts-only) track of that language if present.
    """
    session, _item, streams = await _now_playing()
    if choice.strip().lower() in {"off", "none", "vypni", "vypnut", "ziadne", "žiadne", "-1"}:
        await client().general_command(session["Id"], "SetSubtitleStreamIndex", {"Index": "-1"})
        return {"subtitles": "off"}
    subs = [s for s in streams if s.type == "Subtitle"]
    stream = pick_stream(subs, choice, prefer_forced=forced_only)
    if stream is None:
        return {"changed": False, "available": [s.as_dict() for s in subs]}
    await client().general_command(session["Id"], "SetSubtitleStreamIndex", {"Index": str(stream.index)})
    return {"changed": True, "subtitles": stream.as_dict()}


@mcp.tool()
async def set_audio(choice: str) -> dict[str, Any]:
    """Switch the audio track (dubbing) on the TV. choice: a language ("slovenčina", "cz", "eng") or a track index."""
    session, _item, streams = await _now_playing()
    audio = [s for s in streams if s.type == "Audio"]
    stream = pick_stream(audio, choice)
    if stream is None:
        return {"changed": False, "available": [s.as_dict() for s in audio]}
    await client().general_command(session["Id"], "SetAudioStreamIndex", {"Index": str(stream.index)})
    return {"changed": True, "audio": stream.as_dict()}


@mcp.tool()
async def control(
    action: Literal["pause", "resume", "toggle", "stop", "seek", "forward", "back", "next", "previous"],
    seconds: float | None = None,
) -> dict[str, Any]:
    """Playback control on the TV.

    seek: jump to an absolute position in seconds (e.g. 1h 5min = 3900).
    forward/back: relative jump by `seconds` (default: the app's skip step).
    next/previous: next or previous episode.
    """
    c = client()
    session = await c.target_session()
    sid = session["Id"]
    simple = {
        "pause": "Pause",
        "resume": "Unpause",
        "toggle": "PlayPause",
        "stop": "Stop",
        "next": "NextTrack",
        "previous": "PreviousTrack",
    }
    if action in simple:
        await c.playstate(sid, simple[action])
    elif action == "seek":
        if seconds is None:
            raise JellyfinError("seek needs seconds")
        await c.playstate(sid, "Seek", max(seconds, 0))
    else:
        if seconds is None:
            await c.playstate(sid, "FastForward" if action == "forward" else "Rewind")
        else:
            pos = ((session.get("PlayState") or {}).get("PositionTicks") or 0) / TICKS_PER_SECOND
            target = pos + seconds if action == "forward" else pos - seconds
            await c.playstate(sid, "Seek", max(target, 0))
    return {"ok": True, "action": action}


class SecretPathMiddleware:
    """Serve the MCP app only under /<secret>/...; everything else is 404 (except /health)."""

    def __init__(self, app: ASGIApp, secret: str) -> None:
        if len(secret) < 24:
            raise RuntimeError("MCP_SECRET must be at least 24 characters")
        self.app = app
        self.secret = secret

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] not in ("http", "websocket"):
            await self.app(scope, receive, send)  # lifespan
            return
        path: str = scope["path"]
        if path == "/health":
            await _plain(send, 200, b"ok")
            return
        first, _, rest = path.lstrip("/").partition("/")
        method = scope.get("method", "-")
        ua = dict(scope.get("headers") or []).get(b"user-agent", b"").decode("latin-1")[:80]
        if not hmac.compare_digest(first.encode(), self.secret.encode()):
            # Never log the secret itself: only a 3-char prefix and its length
            log.warning("404 %s /%s...(len %d)/%s ua=%s", method, first[:3], len(first), rest.replace(self.secret, "<secret>"), ua)
            await _plain(send, 404, b"not found")
            return
        log.info("%s /<secret>/%s ua=%s", method, rest, ua)
        # Strip trailing slashes: /<secret>/mcp/ would otherwise get a 307 to /mcp, dropping the secret
        new_path = "/" + rest.rstrip("/")
        scope = dict(scope, path=new_path, raw_path=new_path.encode())
        await self.app(scope, receive, send)


async def _plain(send: Send, status: int, body: bytes) -> None:
    await send(
        {
            "type": "http.response.start",
            "status": status,
            "headers": [(b"content-type", b"text/plain"), (b"content-length", str(len(body)).encode())],
        }
    )
    await send({"type": "http.response.body", "body": body})


def create_app() -> ASGIApp:
    return SecretPathMiddleware(mcp.streamable_http_app(), _env("MCP_SECRET"))


def main() -> None:
    import uvicorn

    logging.basicConfig(level=logging.INFO)
    # Don't log full request paths: they contain the secret
    uvicorn.run(
        create_app(),
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "8765")),
        access_log=False,
    )


if __name__ == "__main__":
    main()
