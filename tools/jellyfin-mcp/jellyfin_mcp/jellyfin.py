"""Thin async client for the parts of the Jellyfin API the MCP tools need.

Targets Jellyfin 10.9+ (uses the /Items?userId=... and /UserItems/Resume routes).
"""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass
from typing import Any

import httpx

TICKS_PER_SECOND = 10_000_000


class JellyfinError(Exception):
    pass


class NoTargetSession(JellyfinError):
    pass


# Language aliases -> ISO 639-2 codes Jellyfin typically stores in MediaStream.Language
_LANG_ALIASES: dict[str, set[str]] = {
    "slo": {"sk", "svk", "slk", "slo", "slovak", "slovencina", "slovensky", "slovenske", "slovenska"},
    "cze": {"cs", "cz", "ces", "cze", "czech", "cestina", "cesky", "ceske", "ceska"},
    "eng": {"en", "eng", "english", "anglictina", "anglicky", "anglicke", "anglicka"},
    "ger": {"de", "deu", "ger", "german", "nemcina", "nemecky", "nemecke", "nemecka"},
    "hun": {"hu", "hun", "hungarian", "madarcina", "madarsky"},
    "pol": {"pl", "pol", "polish", "polstina", "polsky"},
    "fre": {"fr", "fra", "fre", "french", "francuzstina", "francuzsky"},
    "spa": {"es", "spa", "spanish", "spanielcina", "spanielsky"},
    "ita": {"it", "ita", "italian", "taliancina", "taliansky"},
    "rus": {"ru", "rus", "russian", "rustina", "rusky"},
    "jpn": {"ja", "jpn", "japanese", "japoncina", "japonsky"},
}
# Jellyfin may store either the bibliographic (slo) or terminological (slk) code
_EQUIVALENT_CODES: dict[str, set[str]] = {
    "slo": {"slo", "slk", "sk"},
    "cze": {"cze", "ces", "cs", "cz"},
    "eng": {"eng", "en"},
    "ger": {"ger", "deu", "de"},
    "hun": {"hun", "hu"},
    "pol": {"pol", "pl"},
    "fre": {"fre", "fra", "fr"},
    "spa": {"spa", "es"},
    "ita": {"ita", "it"},
    "rus": {"rus", "ru"},
    "jpn": {"jpn", "ja"},
}


def _fold(text: str) -> str:
    """Lowercase and strip diacritics so 'Slovenčina' == 'slovencina'."""
    norm = unicodedata.normalize("NFKD", text.lower().strip())
    return "".join(c for c in norm if not unicodedata.combining(c))


def normalize_language(text: str) -> set[str]:
    """Return the set of language codes a user phrase may refer to (empty if unknown)."""
    folded = _fold(text)
    for canonical, aliases in _LANG_ALIASES.items():
        if folded in aliases:
            return _EQUIVALENT_CODES[canonical]
    return {folded}


@dataclass
class Stream:
    index: int
    type: str  # "Audio" | "Subtitle"
    language: str | None
    title: str
    is_default: bool
    is_forced: bool
    is_external: bool

    @classmethod
    def from_api(cls, s: dict[str, Any]) -> Stream:
        return cls(
            index=s["Index"],
            type=s.get("Type", ""),
            language=s.get("Language"),
            title=s.get("DisplayTitle") or s.get("Title") or "",
            is_default=bool(s.get("IsDefault")),
            is_forced=bool(s.get("IsForced")),
            is_external=bool(s.get("IsExternal")),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "index": self.index,
            "language": self.language,
            "title": self.title,
            "forced": self.is_forced,
            "default": self.is_default,
        }


def pick_stream(streams: list[Stream], wanted: str, *, prefer_forced: bool = False) -> Stream | None:
    """Pick a stream by index ("3"), language ("slovenčina", "cz", "eng") or title substring."""
    wanted = wanted.strip()
    if wanted.lstrip("-").isdigit():
        idx = int(wanted)
        return next((s for s in streams if s.index == idx), None)

    codes = normalize_language(wanted)
    by_lang = [s for s in streams if s.language and _fold(s.language) in codes]
    if not by_lang:
        folded = _fold(wanted)
        by_lang = [s for s in streams if folded in _fold(s.title)]
    if not by_lang:
        return None
    # Full subtitles before forced-only ones unless asked otherwise; then default first
    by_lang.sort(key=lambda s: (s.is_forced != prefer_forced, not s.is_default))
    return by_lang[0]


def summarize_item(item: dict[str, Any]) -> dict[str, Any]:
    """Compact representation for the model: enough to identify and choose an item."""
    user_data = item.get("UserData") or {}
    out: dict[str, Any] = {
        "id": item["Id"],
        "name": item.get("Name"),
        "type": item.get("Type"),
        "year": item.get("ProductionYear"),
    }
    if item.get("Type") == "Episode":
        out["series"] = item.get("SeriesName")
        out["season"] = item.get("ParentIndexNumber")
        out["episode"] = item.get("IndexNumber")
        out["series_id"] = item.get("SeriesId")
    if user_data.get("Played"):
        out["watched"] = True
    pos = user_data.get("PlaybackPositionTicks") or 0
    if pos:
        out["resume_at_min"] = round(pos / TICKS_PER_SECOND / 60, 1)
    if item.get("RunTimeTicks"):
        out["runtime_min"] = round(item["RunTimeTicks"] / TICKS_PER_SECOND / 60)
    return out


class JellyfinClient:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        username: str,
        target_client: str = "Wholphin",
        target_device: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.target_client = target_client.lower()
        self.target_device = target_device.lower() if target_device else None
        self._user_id: str | None = None
        self._http = httpx.AsyncClient(
            base_url=self.base_url,
            headers={
                "Authorization": f'MediaBrowser Client="Claude MCP", Device="jellyfin-mcp", '
                f'DeviceId="jellyfin-mcp", Version="1.0", Token="{api_key}"',
            },
            timeout=15.0,
            transport=transport,
        )

    async def aclose(self) -> None:
        await self._http.aclose()

    # ---- HTTP ----

    async def _get(self, path: str, **params: Any) -> Any:
        r = await self._http.get(path, params={k: v for k, v in params.items() if v is not None})
        if r.status_code >= 400:
            raise JellyfinError(f"GET {path} -> HTTP {r.status_code}")
        return r.json()

    async def _post(self, path: str, json: Any = None, **params: Any) -> None:
        r = await self._http.post(
            path, params={k: v for k, v in params.items() if v is not None}, json=json
        )
        if r.status_code >= 400:
            raise JellyfinError(f"POST {path} -> HTTP {r.status_code}")

    # ---- Users / items ----

    async def user_id(self) -> str:
        if self._user_id is None:
            users = await self._get("/Users")
            match = next(
                (u for u in users if u.get("Name", "").lower() == self.username.lower()), None
            )
            if match is None:
                raise JellyfinError(f"Jellyfin user '{self.username}' not found")
            self._user_id = match["Id"]
        return self._user_id

    async def search(self, query: str, types: list[str], limit: int = 8) -> list[dict[str, Any]]:
        data = await self._get(
            "/Items",
            userId=await self.user_id(),
            searchTerm=query,
            recursive="true",
            includeItemTypes=",".join(types),
            limit=limit,
            fields="ProductionYear,UserData",
        )
        return data.get("Items", [])

    async def item(self, item_id: str) -> dict[str, Any]:
        return await self._get(f"/Items/{item_id}", userId=await self.user_id())

    async def next_up(self, series_id: str | None = None, limit: int = 1) -> list[dict[str, Any]]:
        data = await self._get(
            "/Shows/NextUp",
            userId=await self.user_id(),
            seriesId=series_id,
            limit=limit,
            enableResumable="true",
            fields="UserData",
        )
        return data.get("Items", [])

    async def episodes(self, series_id: str, season: int | None = None) -> list[dict[str, Any]]:
        data = await self._get(
            f"/Shows/{series_id}/Episodes",
            userId=await self.user_id(),
            season=season,
            fields="UserData",
        )
        return data.get("Items", [])

    async def resume_items(self, limit: int = 8) -> list[dict[str, Any]]:
        data = await self._get(
            "/UserItems/Resume",
            userId=await self.user_id(),
            limit=limit,
            mediaTypes="Video",
            fields="UserData",
        )
        return data.get("Items", [])

    async def streams(self, item_id: str) -> list[Stream]:
        item = await self.item(item_id)
        sources = item.get("MediaSources") or []
        raw = sources[0].get("MediaStreams", []) if sources else item.get("MediaStreams", [])
        return [Stream.from_api(s) for s in raw if s.get("Type") in ("Audio", "Subtitle")]

    # ---- Sessions / remote control ----

    async def target_session(self) -> dict[str, Any]:
        sessions = await self._get("/Sessions", activeWithinSeconds=960)

        def matches(s: dict[str, Any]) -> bool:
            if not s.get("SupportsRemoteControl"):
                return False
            if self.target_device and self.target_device not in (s.get("DeviceName") or "").lower():
                return False
            return self.target_client in (s.get("Client") or "").lower()

        candidates = [s for s in sessions if matches(s)]
        if not candidates:
            raise NoTargetSession(
                "Wholphinix is not connected. Open the app on the TV (it must be in the foreground)."
            )
        candidates.sort(key=lambda s: s.get("LastActivityDate") or "", reverse=True)
        return candidates[0]

    async def play(self, session_id: str, item_id: str, start_seconds: float = 0) -> None:
        await self._post(
            f"/Sessions/{session_id}/Playing",
            playCommand="PlayNow",
            itemIds=item_id,
            startPositionTicks=int(start_seconds * TICKS_PER_SECOND),
        )

    async def playstate(self, session_id: str, command: str, seek_seconds: float | None = None) -> None:
        await self._post(
            f"/Sessions/{session_id}/Playing/{command}",
            seekPositionTicks=int(seek_seconds * TICKS_PER_SECOND) if seek_seconds is not None else None,
        )

    async def general_command(self, session_id: str, name: str, arguments: dict[str, str]) -> None:
        await self._post(
            f"/Sessions/{session_id}/Command", json={"Name": name, "Arguments": arguments}
        )

    async def display_message(self, session_id: str, text: str) -> None:
        await self.general_command(
            session_id, "DisplayMessage", {"Header": "Claude", "Text": text, "TimeoutMs": "4000"}
        )
