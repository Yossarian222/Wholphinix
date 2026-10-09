"""Minimal async client for Seerr / Jellyseerr / Overseerr: search a title and create a request."""

from __future__ import annotations

from typing import Any
from urllib.parse import quote

import httpx

# MediaInfo.status in Seerr: 1 unknown, 2 pending, 3 processing, 4 partially available, 5 available
MEDIA_STATUS = {
    1: "unknown",
    2: "pending",
    3: "processing",
    4: "partially_available",
    5: "available",
}


class SeerrError(Exception):
    pass


class SeerrNotConfigured(SeerrError):
    pass


def _year(result: dict[str, Any]) -> int | None:
    date = result.get("releaseDate") or result.get("firstAirDate") or ""
    return int(date[:4]) if date[:4].isdigit() else None


def pick_result(
    results: list[dict[str, Any]], media_type: str, year: int | None
) -> dict[str, Any] | None:
    """First search result of the wanted type whose year matches (±1, release dates differ by country)."""
    for r in results:
        if r.get("mediaType") != media_type:
            continue
        y = _year(r)
        if year is None or y is None or abs(y - year) <= 1:
            return r
    return None


class SeerrClient:
    def __init__(
        self, base_url: str, api_key: str, transport: httpx.AsyncBaseTransport | None = None
    ) -> None:
        self._http = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            headers={"X-Api-Key": api_key, "Accept": "application/json"},
            timeout=20.0,
            transport=transport,
        )

    async def aclose(self) -> None:
        await self._http.aclose()

    async def search(self, query: str) -> list[dict[str, Any]]:
        # Seerr's request validator rejects "+" for spaces (httpx's default), so percent-encode
        r = await self._http.get(f"/api/v1/search?query={quote(query, safe='')}&page=1")
        if r.status_code >= 400:
            raise SeerrError(f"Seerr search -> HTTP {r.status_code}")
        return r.json().get("results", [])

    async def request(self, media_type: str, media_id: int) -> dict[str, Any]:
        body: dict[str, Any] = {"mediaType": media_type, "mediaId": media_id}
        if media_type == "tv":
            body["seasons"] = "all"
        r = await self._http.post("/api/v1/request", json=body)
        if r.status_code >= 400:
            detail = ""
            try:
                detail = str(r.json().get("message") or "")[:200]
            except ValueError:
                pass
            raise SeerrError(f"Seerr request -> HTTP {r.status_code} {detail}".strip())
        return r.json()
