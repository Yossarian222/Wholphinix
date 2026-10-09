"""MCP server that lets Claude control Jellyfin playback on the Wholphinix TV session.

Exposed over streamable HTTP at /<MCP_SECRET>/mcp (and the optional WhatsApp webhook at
/<MCP_SECRET>/whatsapp, see whatsapp.py). The secret path segment is the only
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
    _fold,
    pick_stream,
    summarize_item,
    validate_id,
)
from . import whatsapp
from .seerr import MEDIA_STATUS, SeerrClient, SeerrNotConfigured, pick_result

log = logging.getLogger("jellyfin_mcp")

INSTRUCTIONS = """\
You control the Jellyfin media server and the Wholphinix app on the user's living-room TV.
The user usually writes or speaks in Slovak (often by voice from the Claude mobile app, so
names may be misheard - search with variants). Typical requests: play a movie or the next
episode of a series, switch subtitles or dubbing (audio language), pause, rewind, stop.

Workflow:
- To play something, call search_library first, then play with the chosen id. If several
  results fit equally (e.g. remakes), ask which one, mentioning the year.
- For a series without a specific episode, play(series_id) continues with the next unwatched
  episode. For "S2E5" style requests use play_episode.
- For subtitles/dubbing call set_subtitles / set_audio with a language ("slovenčina", "cz",
  "eng") or an index from whats_playing. "titulky preč/vypni" means set_subtitles("off").
- "dabing" means audio track language. "Pusti X s českým dabingom": play, then set_audio("cz").
- "Napíš na telku / daj vedieť na TV ...": show_message with a short text (max 300 chars). It
  pops up in a corner of the TV, also over a running film, without interrupting it.
- "Čo dnes dávajú v telke / v TV?": tv_tips_today. Mention channel and time, and point out
  which ones are already in the library (those can be played right away with play(item_id)).
- "Čo si dnes pozrieť / niečo na večer / mám chuť na komédiu": recommend_tonight (mood = the
  user's words, max_minutes when they say how much time they have). Pick 1-3 of the
  candidates yourself and say briefly why; offer to play the one they choose.
- "Stiahni / chcem / objednaj film X" for something not in the library: request_on_seerr.
  Confirm the title and year first if it is ambiguous.
- If a tool says Wholphinix is not connected, tell the user to open the app on the TV.
Reply in Slovak, informally (tykanie), and keep replies short - they are often read aloud.
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


_seerr: SeerrClient | None = None


def seerr() -> SeerrClient:
    """Seerr/Jellyseerr client, only if SEERR_URL and SEERR_API_KEY are set."""
    global _seerr
    if _seerr is None:
        url = os.environ.get("SEERR_URL")
        key = os.environ.get("SEERR_API_KEY")
        if not url or not key:
            raise SeerrNotConfigured(
                "Seerr nie je nastavený (chýba SEERR_URL alebo SEERR_API_KEY v prostredí MCP servera)"
            )
        _seerr = SeerrClient(url, key)
    return _seerr


def set_seerr(c: SeerrClient | None) -> None:
    """For tests."""
    global _seerr
    _seerr = c


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
    validate_id(item_id, "item_id")
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
    validate_id(series_id, "series_id")
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
    media_source_id = (session.get("PlayState") or {}).get("MediaSourceId")
    return session, item, await c.streams(item["Id"], media_source_id)


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
            # PositionTicks is the position from the client's last progress report (sent every
            # few seconds), so the jump can be off by that much; good enough for "back 30 s".
            pos = ((session.get("PlayState") or {}).get("PositionTicks") or 0) / TICKS_PER_SECOND
            target = pos + seconds if action == "forward" else pos - seconds
            await c.playstate(sid, "Seek", max(target, 0))
    return {"ok": True, "action": action}


MAX_MESSAGE_CHARS = 300
MAX_MESSAGE_SECONDS = 30


@mcp.tool()
async def show_message(text: str, title: str = "Claude", seconds: float = 8) -> dict[str, Any]:
    """Show a short message on the TV, e.g. "Večera je hotová!".

    It appears in a corner over whatever is on screen (also over a playing film) without pausing
    it or taking focus, and disappears after `seconds` (1-30). Max 300 characters.
    """
    text = text.strip()
    if not text:
        raise ValueError("text is empty")
    if len(text) > MAX_MESSAGE_CHARS:
        raise ValueError(
            f"text is {len(text)} characters long, max {MAX_MESSAGE_CHARS}: shorten it"
        )
    title = (title or "Claude").strip()[:60] or "Claude"
    seconds = min(max(float(seconds), 1.0), MAX_MESSAGE_SECONDS)
    c = client()
    session = await c.target_session()
    await c.display_message(session["Id"], text, header=title, timeout_ms=int(seconds * 1000))
    return {"shown": True, "seconds": seconds}


def _short(text: Any, limit: int = 220) -> str | None:
    if not text:
        return None
    text = " ".join(str(text).split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


@mcp.tool()
async def tv_tips_today() -> dict[str, Any]:
    """What good films/series are on TV today (ČSFD "TV tipy"), and which of them are in the library.

    in_library items have an id usable with play(); not_in_library ones can be requested with
    request_on_seerr (title, year, media_type).
    """
    tips = await client().tv_tips(limit=10, missing=5)
    in_library: list[dict[str, Any]] = []
    missing: list[dict[str, Any]] = []
    for t in tips:
        out = {
            "title": t.get("Title"),
            "year": t.get("Year"),
            "time": t.get("Time"),
            "channel": t.get("Channel"),
            "csfd_percent": t.get("RatingPercent"),
        }
        if t.get("InLibrary") and t.get("ItemId"):
            out["id"] = str(t["ItemId"]).replace("-", "")
            in_library.append(out)
        else:
            out["media_type"] = t.get("MediaType") or "movie"
            out["genres"] = t.get("Genres") or []
            if t.get("DurationMinutes"):
                out["runtime_min"] = t.get("DurationMinutes")
            out["overview"] = _short(t.get("Overview"))
            missing.append({k: v for k, v in out.items() if v not in (None, [])})
    return {"in_library": in_library, "not_in_library": missing}


# Slovak mood words (folded, prefixes) -> genre name fragments (folded; Slovak, Czech and English
# names, the ČSFD plugin writes Slovak/Czech genres, other metadata providers English ones)
_MOOD_GENRES: list[tuple[tuple[str, ...], tuple[str, ...]]] = [
    (("vesel", "vtip", "smie", "smia", "zasmi", "komed", "odlah", "pohod", "funny", "comedy"),
     ("komed", "comedy")),
    (("napat", "napin", "thrill", "krimi", "detekt", "zahad", "mystery"),
     ("thriller", "krimi", "crime", "mysteri", "zahad", "detektiv")),
    (("akci", "akcn", "adrenal", "action"), ("akcn", "action", "dobrodruz", "adventure")),
    (("bat", "strach", "horor", "horror", "desiv"), ("horor", "horror")),
    (("roman", "lask", "rande", "zamil"), ("romant",)),
    (("vazn", "smut", "dram", "dojim", "rozmysl"), ("dram",)),
    (("sci", "vesmir", "buduc", "fantas"), ("sci-fi", "science", "fantas", "vedecko")),
    (("rodin", "deti", "detm", "detsk", "family", "anim", "rozpravk"),
     ("rodinn", "family", "animovan", "animation", "rozpravk")),
    (("dokument", "pouc", "skutoc"), ("dokument", "documentary", "zivotopis", "biograf", "histor")),
    (("vojn", "war", "histor"), ("vojnov", "valecn", "war", "histor")),
]


def mood_genres(mood: str | None) -> list[str]:
    """Genre name fragments a mood phrase refers to; unknown words are used as genre fragments."""
    if not mood:
        return []
    words = [w for w in _fold(mood).replace(",", " ").split() if len(w) >= 3]
    out: list[str] = []
    for w in words:
        hits = [genres for keys, genres in _MOOD_GENRES if any(w.startswith(k) for k in keys)]
        if hits:
            for genres in hits:
                out.extend(g for g in genres if g not in out)
    if not out:
        out = words
    return out


@mcp.tool()
async def recommend_tonight(mood: str | None = None, max_minutes: int | None = None) -> dict[str, Any]:
    """Candidates for tonight: unwatched movies from the library, best ČSFD rating first.

    mood: the user's words ("niečo vtipné", "napätie", "romantika", "s deťmi"...), matched to
    genres. max_minutes: skip longer films. Returns up to 5 candidates with a short reason;
    choose and recommend yourself, then play(id) on request.
    """
    movies = await client().unwatched_movies()
    if max_minutes:
        movies = [
            m for m in movies
            if not m.get("RunTimeTicks") or m["RunTimeTicks"] / TICKS_PER_SECOND / 60 <= max_minutes
        ]
    wanted = mood_genres(mood)

    def genre_match(m: dict[str, Any]) -> list[str]:
        return [g for g in (m.get("Genres") or []) if any(w in _fold(g) for w in wanted)]

    matching = [m for m in movies if genre_match(m)] if wanted else movies
    mood_matched = bool(matching) or not wanted
    picked = (matching or movies)[:5]

    candidates = []
    for m in picked:
        rating = m.get("CommunityRating")
        runtime = round(m["RunTimeTicks"] / TICKS_PER_SECOND / 60) if m.get("RunTimeTicks") else None
        genres = m.get("Genres") or []
        reason = ", ".join(
            x
            for x in [
                f"ČSFD {round(rating * 10)} %" if rating else None,
                f"sedí na náladu ({', '.join(genre_match(m))})" if wanted and genre_match(m) else None,
                f"{runtime} min" if runtime else None,
            ]
            if x
        )
        candidates.append(
            {
                "id": m["Id"],
                "name": m.get("Name"),
                "year": m.get("ProductionYear"),
                "csfd_percent": round(rating * 10) if rating else None,
                "genres": genres,
                "runtime_min": runtime,
                "overview": _short(m.get("Overview"), 180),
                "reason": reason,
            }
        )
    out: dict[str, Any] = {"candidates": candidates, "unwatched_considered": len(movies)}
    if not mood_matched:
        out["note"] = f"Nothing unwatched matches the mood '{mood}', these are the best rated ones"
    return out


@mcp.tool()
async def request_on_seerr(
    title: str, year: int | None = None, media_type: Literal["movie", "tv"] = "movie"
) -> dict[str, Any]:
    """Request a film or series that is not in the library through Seerr/Jellyseerr (it gets downloaded).

    Finds the title (original or Czech/Slovak name) and creates the request; if it is already
    available or requested, says so instead.
    """
    s = seerr()
    results = await s.search(title)
    match = pick_result(results, media_type, year)
    if match is None:
        return {
            "requested": False,
            "message": f"'{title}' ({media_type}{f', {year}' if year else ''}) not found in Seerr",
            "other_results": [
                {"title": r.get("title") or r.get("name"), "media_type": r.get("mediaType"),
                 "year": (r.get("releaseDate") or r.get("firstAirDate") or "")[:4] or None}
                for r in results[:5]
                if r.get("mediaType") in ("movie", "tv")
            ],
        }
    found = {
        "title": match.get("title") or match.get("name"),
        "year": (match.get("releaseDate") or match.get("firstAirDate") or "")[:4] or None,
        "media_type": media_type,
        "tmdb_id": match.get("id"),
    }
    status = (match.get("mediaInfo") or {}).get("status")
    if status and status >= 2:
        return {"requested": False, "already": MEDIA_STATUS.get(status, str(status)), **found}
    req = await s.request(media_type, int(match["id"]))
    return {"requested": True, "request_id": req.get("id"), **found}


# WhatsApp webhook (GET/POST /<MCP_SECRET>/whatsapp); 404 unless the WHATSAPP_* env is set
whatsapp.register(mcp, INSTRUCTIONS)


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
    # Default 127.0.0.1: the container shares the tailscale container's network namespace
    # (network_mode: service:ts), Funnel proxies to 127.0.0.1:8765, so nothing else needs to
    # reach it. Set MCP_HOST=0.0.0.0 only for a different network setup.
    uvicorn.run(
        create_app(),
        host=os.environ.get("MCP_HOST", "127.0.0.1"),
        port=int(os.environ.get("PORT", "8765")),
        access_log=False,
    )


if __name__ == "__main__":
    main()
