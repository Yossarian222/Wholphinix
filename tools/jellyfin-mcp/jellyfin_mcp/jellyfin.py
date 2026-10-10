"""Thin async client for the parts of the Jellyfin API the MCP tools need.

Targets Jellyfin 10.9+ (uses the /Items?userId=... and /UserItems/Resume routes).
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Any

import httpx

TICKS_PER_SECOND = 10_000_000


class JellyfinError(Exception):
    pass


class NoTargetSession(JellyfinError):
    pass


# Jellyfin item/user ids are GUIDs, usually serialized as 32 hex chars (sometimes dashed)
_GUID_RE = re.compile(
    r"[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)


def validate_id(value: str, name: str = "id") -> str:
    """Reject anything that is not a Jellyfin GUID before it is put into a URL path.

    Ids come from the model (tool arguments), so without this e.g. "../Users" or "x?y=z"
    could steer the request to a different API endpoint authenticated with the admin key.
    """
    if not isinstance(value, str) or not _GUID_RE.fullmatch(value):
        raise ValueError(
            f"Invalid {name} {value!r}: expected a Jellyfin id (32 hex characters) "
            "as returned by search_library"
        )
    return value


def _norm_id(value: Any) -> str:
    """Canonical form for comparing ids that may or may not contain dashes."""
    return str(value or "").replace("-", "").lower()


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

    async def image(self, item_id: str, max_height: int = 360) -> tuple[bytes, str] | None:
        """Primary image (poster) of an item, or None if it has none."""
        validate_id(item_id, "item_id")
        r = await self._http.get(
            f"/Items/{item_id}/Images/Primary", params={"maxHeight": max_height, "quality": 85}
        )
        if r.status_code == 404:
            return None
        if r.status_code >= 400:
            raise JellyfinError(f"GET image -> HTTP {r.status_code}")
        ctype = r.headers.get("content-type", "")
        if not ctype.startswith("image/"):
            return None
        return r.content, ctype

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
        validate_id(item_id, "item_id")
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
        validate_id(series_id, "series_id")
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

    async def streams(self, item_id: str, media_source_id: str | None = None) -> list[Stream]:
        """Audio/subtitle streams of the media source being played.

        Items with several versions (e.g. 1080p and 4K files) have several MediaSources whose
        stream indexes differ, so use the one the session reports (PlayState.MediaSourceId)
        and fall back to the first.
        """
        item = await self.item(item_id)
        sources = item.get("MediaSources") or []
        source = None
        if media_source_id:
            wanted = _norm_id(media_source_id)
            source = next((s for s in sources if _norm_id(s.get("Id")) == wanted), None)
        if source is None and sources:
            source = sources[0]
        raw = source.get("MediaStreams", []) if source else item.get("MediaStreams", [])
        return [Stream.from_api(s) for s in raw if s.get("Type") in ("Audio", "Subtitle")]

    # ---- Sessions / remote control ----

    async def target_session(self) -> dict[str, Any]:
        sessions = await self._get("/Sessions", activeWithinSeconds=960)
        # Only control JELLYFIN_USER's own Wholphinix, never another household member's TV
        uid = _norm_id(await self.user_id())

        def matches(s: dict[str, Any]) -> bool:
            if not s.get("SupportsRemoteControl"):
                return False
            if _norm_id(s.get("UserId")) != uid:
                return False
            if self.target_device and self.target_device not in (s.get("DeviceName") or "").lower():
                return False
            return self.target_client in (s.get("Client") or "").lower()

        candidates = [s for s in sessions if matches(s)]
        if not candidates:
            raise NoTargetSession(
                "Wholphinix is not connected. Open the app on the TV (it must be in the foreground"
                f" and signed in as Jellyfin user '{self.username}')."
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

    async def display_message(
        self, session_id: str, text: str, header: str = "Claude", timeout_ms: int = 8000
    ) -> None:
        """Show a message bubble on the TV (Wholphinix draws it over everything, also the player)."""
        await self.general_command(
            session_id,
            "DisplayMessage",
            {"Header": header, "Text": text, "TimeoutMs": str(int(timeout_ms))},
        )

    # ---- ČSFD plugin / recommendations ----

    async def tv_tips(self, limit: int = 10, missing: int = 5) -> list[dict[str, Any]]:
        """Today's ČSFD "TV tips" from the Jellyfin ČSFD plugin, matched against JELLYFIN_USER's library.

        The endpoint normally takes the user from the caller's token. An API key has no user, so
        the user is passed as userId (the plugin allows that only for admins / API keys).
        """
        r = await self._http.get(
            "/Csfd/TvTips",
            params={"limit": limit, "missing": missing, "userId": await self.user_id()},
            # The first call of the day downloads the missing tips' details from ČSFD
            timeout=120.0,
        )
        if r.status_code == 404:
            raise JellyfinError("The ČSFD plugin is not installed on the Jellyfin server (GET /Csfd/TvTips -> 404)")
        if r.status_code in (400, 401, 403):
            raise JellyfinError(
                f"GET /Csfd/TvTips -> HTTP {r.status_code}: the ČSFD plugin is too old for API-key access"
                " (needs the userId parameter), update the plugin"
            )
        if r.status_code >= 400:
            raise JellyfinError(f"GET /Csfd/TvTips -> HTTP {r.status_code}")
        data = r.json()
        return data if isinstance(data, list) else []

    # ---- Library browsing ----

    async def browse(
        self,
        types: list[str],
        *,
        genres: list[str] | None = None,
        years: list[int] | None = None,
        person_ids: list[str] | None = None,
        is_played: bool | None = None,
        min_rating: float | None = None,
        sort_by: str = "SortName",
        sort_order: str = "Ascending",
        limit: int = 20,
    ) -> tuple[list[dict[str, Any]], int]:
        """Filtered library listing; returns (items, total matching count)."""
        data = await self._get(
            "/Items",
            userId=await self.user_id(),
            recursive="true",
            includeItemTypes=",".join(types),
            genres="|".join(genres) if genres else None,
            years=",".join(str(y) for y in years) if years else None,
            personIds=",".join(person_ids) if person_ids else None,
            isPlayed=None if is_played is None else str(is_played).lower(),
            minCommunityRating=min_rating,
            sortBy=sort_by,
            sortOrder=sort_order,
            limit=limit,
            fields="ProductionYear,Genres,UserData,RunTimeTicks",
            enableTotalRecordCount="true",
        )
        return data.get("Items", []), int(data.get("TotalRecordCount") or 0)

    async def genres(self, types: list[str]) -> list[str]:
        data = await self._get(
            "/Genres", userId=await self.user_id(), includeItemTypes=",".join(types), recursive="true"
        )
        return [g["Name"] for g in data.get("Items", []) if g.get("Name")]

    async def persons(self, name: str, limit: int = 5) -> list[dict[str, Any]]:
        data = await self._get("/Persons", userId=await self.user_id(), searchTerm=name, limit=limit)
        return data.get("Items", [])

    async def counts(self) -> dict[str, Any]:
        return await self._get("/Items/Counts", userId=await self.user_id())

    async def history(self, limit: int = 15) -> list[dict[str, Any]]:
        """Recently played movies/episodes (also partially watched), newest first."""
        data = await self._get(
            "/Items",
            userId=await self.user_id(),
            recursive="true",
            includeItemTypes="Movie,Episode",
            sortBy="DatePlayed",
            sortOrder="Descending",
            limit=limit * 2,  # items never played may sneak in; filtered below
            fields="ProductionYear,UserData,RunTimeTicks",
            enableTotalRecordCount="false",
        )
        items = [i for i in data.get("Items", []) if (i.get("UserData") or {}).get("LastPlayedDate")]
        return items[:limit]

    async def unwatched_movies(self, limit: int = 300) -> list[dict[str, Any]]:
        """Unwatched movies of JELLYFIN_USER, best rated (CommunityRating = ČSFD % / 10) first."""
        data = await self._get(
            "/Items",
            userId=await self.user_id(),
            recursive="true",
            includeItemTypes="Movie",
            isPlayed="false",
            sortBy="CommunityRating,SortName",
            sortOrder="Descending,Ascending",
            limit=limit,
            fields="ProductionYear,Genres,Overview,UserData",
            enableTotalRecordCount="false",
        )
        return data.get("Items", [])
