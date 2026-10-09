import asyncio
import json
import logging
import socket
import threading
import time

import httpx
import pytest
import uvicorn
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client as streamablehttp_client

from jellyfin_mcp import server
from jellyfin_mcp.seerr import SeerrClient
from jellyfin_mcp.jellyfin import (
    JellyfinClient,
    Stream,
    normalize_language,
    pick_stream,
    validate_id,
)

SECRET = "s3cret-path-token-0123456789abcdef"
USER = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
OTHER_USER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
SESSION = "sess1"
# Jellyfin ids are 32-hex GUIDs
M1 = "11111111111111111111111111111111"
S1 = "22222222222222222222222222222222"
E = {n: f"{n:032x}" for n in (1, 2, 3, 7)}
SRC_4K = "44444444444444444444444444444444"


class FakeJellyfin:
    """Records remote-control calls and serves canned library data."""

    def __init__(self) -> None:
        self.calls: list[tuple[str, str, dict, object]] = []
        self.position_ticks = 600 * 10_000_000  # 10 min
        self.media_source_id: str | None = None
        self.wholphin_user = USER
        self.tips_status = 200
        self.tips_params: dict = {}
        self.items_params: dict = {}

    def handler(self, request: httpx.Request) -> httpx.Response:
        path = request.url.path
        params = dict(request.url.params)
        body = json.loads(request.content) if request.content else None
        if request.method == "POST":
            self.calls.append((request.method, path, params, body))
            return httpx.Response(204)
        if path == "/Users":
            return httpx.Response(200, json=[{"Name": "Roman", "Id": USER},
                                             {"Name": "Kids", "Id": OTHER_USER}])
        if path == "/Csfd/TvTips":
            self.tips_params = params
            return httpx.Response(self.tips_status, json=[
                {"CsfdId": 10, "Title": "Pelíšky", "Year": 1999, "Time": "20:15", "Channel": "JOJ",
                 "ItemId": "1111111111111111111111111111111a", "InLibrary": True, "RatingPercent": 90},
                {"CsfdId": 11, "Title": "Vesničko má středisková", "Year": 1985, "Time": "21:00",
                 "Channel": "ČT1", "ItemId": None, "InLibrary": False, "RatingPercent": 88,
                 "MediaType": "movie", "Genres": ["Komédia"], "Overview": "x" * 500, "Poster": "p"},
            ])
        if path == "/Items" and params.get("isPlayed") == "false":
            self.items_params = params
            return httpx.Response(200, json={"Items": [
                {"Id": f"{n:032x}", "Name": name, "ProductionYear": 2000 + n, "CommunityRating": rating,
                 "Genres": genres, "RunTimeTicks": minutes * 60 * 10_000_000, "Overview": "o"}
                for n, (name, rating, genres, minutes) in enumerate([
                    ("Dlhá dráma", 9.1, ["Dráma"], 190),
                    ("Akčňák", 8.5, ["Akčný", "Thriller"], 110),
                    ("Komédia 1", 8.0, ["Komédia"], 95),
                    ("Komédia 2", 7.5, ["Komédia", "Rodinný"], 100),
                    ("Horor", 7.0, ["Horor"], 90),
                    ("Ďalší", 6.0, ["Dráma"], 100),
                    ("Posledný", 5.0, ["Dráma"], 100),
                ], start=1)
            ]})
        if path == "/Items" and params.get("searchTerm"):
            return httpx.Response(
                200,
                json={
                    "Items": [
                        {"Id": M1, "Name": "Pulp Fiction", "Type": "Movie", "ProductionYear": 1994,
                         "UserData": {"PlaybackPositionTicks": 1200 * 10_000_000}},
                    ]
                },
            )
        if path == f"/Items/{M1}":
            return httpx.Response(
                200,
                json={
                    "Id": M1, "Name": "Pulp Fiction", "Type": "Movie",
                    "UserData": {"PlaybackPositionTicks": 1200 * 10_000_000},
                    "MediaSources": [{"Id": M1, "MediaStreams": [
                        {"Index": 0, "Type": "Video"},
                        {"Index": 1, "Type": "Audio", "Language": "eng", "DisplayTitle": "English 5.1", "IsDefault": True},
                        {"Index": 2, "Type": "Audio", "Language": "cze", "DisplayTitle": "Czech"},
                        {"Index": 3, "Type": "Subtitle", "Language": "slo", "DisplayTitle": "Slovak Forced", "IsForced": True},
                        {"Index": 4, "Type": "Subtitle", "Language": "slo", "DisplayTitle": "Slovak"},
                        {"Index": 5, "Type": "Subtitle", "Language": "eng", "DisplayTitle": "English"},
                    ]}, {"Id": SRC_4K, "MediaStreams": [
                        {"Index": 0, "Type": "Video"},
                        {"Index": 1, "Type": "Audio", "Language": "cze", "DisplayTitle": "Czech 4K"},
                        {"Index": 2, "Type": "Subtitle", "Language": "slo", "DisplayTitle": "Slovak 4K"},
                    ]}],
                },
            )
        if path == f"/Items/{S1}":
            return httpx.Response(200, json={"Id": S1, "Name": "Breaking Bad", "Type": "Series"})
        if path == "/Shows/NextUp":
            return httpx.Response(200, json={"Items": [
                {"Id": E[7], "Name": "Ep", "Type": "Episode", "SeriesName": "Breaking Bad",
                 "ParentIndexNumber": 2, "IndexNumber": 3, "UserData": {}}]})
        if path == f"/Shows/{S1}/Episodes":
            return httpx.Response(200, json={"Items": [
                {"Id": E[n], "Name": f"Ep {n}", "Type": "Episode", "ParentIndexNumber": 2,
                 "IndexNumber": n, "UserData": {}} for n in (1, 2, 3)]})
        if path == "/Sessions":
            return httpx.Response(200, json=[
                {"Id": "web", "Client": "Jellyfin Web", "SupportsRemoteControl": True,
                 "UserId": USER, "LastActivityDate": "2026-10-08T10:00:00Z"},
                # Another household member's TV, more recently active: must never be picked
                {"Id": "kids-tv", "Client": "Wholphin", "DeviceName": "kids-tv",
                 "UserId": OTHER_USER, "SupportsRemoteControl": True,
                 "LastActivityDate": "2026-10-08T11:00:00Z"},
                {"Id": SESSION, "Client": "Wholphin", "DeviceName": "raspberry-tv",
                 "UserId": self.wholphin_user,
                 "SupportsRemoteControl": True, "LastActivityDate": "2026-10-08T09:00:00Z",
                 "NowPlayingItem": {"Id": M1, "Name": "Pulp Fiction", "Type": "Movie"},
                 "PlayState": {"PositionTicks": self.position_ticks, "IsPaused": False,
                               "MediaSourceId": self.media_source_id,
                               "AudioStreamIndex": 1, "SubtitleStreamIndex": -1}},
            ])
        return httpx.Response(404)


@pytest.fixture
def fake():
    f = FakeJellyfin()
    server.set_client(
        JellyfinClient("http://jf", "key", "Roman", transport=httpx.MockTransport(f.handler))
    )
    return f


class FakeSeerr:
    def __init__(self) -> None:
        self.requests: list[dict] = []
        self.queries: list[str] = []
        self.raw_queries: list[bytes] = []
        self.api_keys: set[str] = set()

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.api_keys.add(request.headers.get("x-api-key", ""))
        if request.url.path == "/api/v1/search":
            self.queries.append(request.url.params["query"])
            self.raw_queries.append(request.url.query)
            return httpx.Response(200, json={"results": [
                {"id": 1, "mediaType": "person", "name": "Someone"},
                {"id": 500, "mediaType": "movie", "title": "Cosy Dens", "releaseDate": "1999-04-01"},
                {"id": 501, "mediaType": "movie", "title": "Cosy Dens", "releaseDate": "2019-01-01",
                 "mediaInfo": {"status": 5}},
                {"id": 600, "mediaType": "tv", "name": "Cosy Dens", "firstAirDate": "2005-01-01"},
            ]})
        if request.url.path == "/api/v1/request" and request.method == "POST":
            body = json.loads(request.content)
            self.requests.append(body)
            return httpx.Response(201, json={"id": 77, "status": 1})
        return httpx.Response(404)


@pytest.fixture
def fake_seerr():
    f = FakeSeerr()
    server.set_seerr(SeerrClient("http://seerr", "seerr-key", transport=httpx.MockTransport(f.handler)))
    yield f
    server.set_seerr(None)


@pytest.fixture(scope="module")
def base_url():
    import os

    os.environ["MCP_SECRET"] = SECRET
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    config = uvicorn.Config(server.create_app(), host="127.0.0.1", port=port, log_level="warning")
    srv = uvicorn.Server(config)
    t = threading.Thread(target=srv.run, daemon=True)
    t.start()
    for _ in range(100):
        if srv.started:
            break
        time.sleep(0.05)
    yield f"http://127.0.0.1:{port}"
    srv.should_exit = True
    t.join(timeout=5)


async def _call_raw(base_url: str, tool: str, args: dict):
    async with streamablehttp_client(f"{base_url}/{SECRET}/mcp") as (r, w, _):
        async with ClientSession(r, w) as s:
            await s.initialize()
            return await s.call_tool(tool, args)


async def _call(base_url: str, tool: str, args: dict):
    res = await _call_raw(base_url, tool, args)
    assert not res.isError, res.content
    return json.loads(res.content[0].text) if res.content else None


def run(coro):
    return asyncio.run(coro)


# ---- unit ----

def test_language_aliases():
    assert "slk" in normalize_language("Slovenčina")
    assert "cze" in normalize_language("cz")
    assert "eng" in normalize_language("anglicky")


def test_validate_id():
    assert validate_id(M1) == M1
    assert validate_id("0F1E2D3C-4B5A-6978-8796-A5B4C3D2E1F0")
    for bad in ["", "m1", "../Users", f"{M1}/../../Users", f"{M1}?x=1", M1 + "0", f"{M1}\n"]:
        with pytest.raises(ValueError):
            validate_id(bad)


def test_pick_stream_prefers_full_subtitles_over_forced():
    subs = [
        Stream(3, "Subtitle", "slo", "Slovak Forced", False, True, False),
        Stream(4, "Subtitle", "slo", "Slovak", False, False, False),
    ]
    assert pick_stream(subs, "slovensky").index == 4
    assert pick_stream(subs, "sk", prefer_forced=True).index == 3
    assert pick_stream(subs, "4").index == 4
    assert pick_stream(subs, "japonsky") is None


# ---- over real MCP/HTTP ----

def test_auth(base_url):
    assert httpx.get(f"{base_url}/health").status_code == 200
    assert httpx.post(f"{base_url}/mcp", json={}).status_code == 404
    assert httpx.post(f"{base_url}/wrong-secret-wrong-secret-xx/mcp", json={}).status_code == 404


def test_lists_tools(base_url, fake):
    async def go():
        async with streamablehttp_client(f"{base_url}/{SECRET}/mcp") as (r, w, _):
            async with ClientSession(r, w) as s:
                init = await s.initialize()
                assert "Wholphinix" in (init.instructions or "")
                return {t.name for t in (await s.list_tools()).tools}

    names = run(go())
    assert {"search_library", "play", "play_episode", "set_subtitles", "set_audio",
            "control", "whats_playing", "continue_watching", "show_message", "tv_tips_today",
            "recommend_tonight", "request_on_seerr"} <= names


def test_search_and_play_resumes(base_url, fake):
    found = run(_call(base_url, "search_library", {"query": "pulp fiction"}))["results"]
    assert found[0]["id"] == M1 and found[0]["resume_at_min"] == 20.0
    run(_call(base_url, "play", {"item_id": M1}))
    method, path, params, _ = fake.calls[-1]
    assert path == f"/Sessions/{SESSION}/Playing"  # picked Wholphin, not the web client
    assert params["itemIds"] == M1 and params["playCommand"] == "PlayNow"
    assert params["startPositionTicks"] == str(1200 * 10_000_000)


def test_play_series_uses_next_up(base_url, fake):
    out = run(_call(base_url, "play", {"item_id": S1}))
    assert out["playing"]["id"] == E[7]
    assert fake.calls[-1][2]["itemIds"] == E[7]


def test_play_episode(base_url, fake):
    run(_call(base_url, "play_episode", {"series_id": S1, "season": 2, "episode": 2, "from_start": True}))
    assert fake.calls[-1][2]["itemIds"] == E[2]
    assert fake.calls[-1][2]["startPositionTicks"] == "0"


def test_subtitles_and_audio(base_url, fake):
    run(_call(base_url, "set_subtitles", {"choice": "slovenčina"}))
    assert fake.calls[-1][1:] == (f"/Sessions/{SESSION}/Command", {},
                                  {"Name": "SetSubtitleStreamIndex", "Arguments": {"Index": "4"}})
    run(_call(base_url, "set_subtitles", {"choice": "vypni"}))
    assert fake.calls[-1][3]["Arguments"] == {"Index": "-1"}
    run(_call(base_url, "set_audio", {"choice": "česky"}))
    assert fake.calls[-1][3] == {"Name": "SetAudioStreamIndex", "Arguments": {"Index": "2"}}
    out = run(_call(base_url, "set_audio", {"choice": "japonsky"}))
    assert out["changed"] is False and len(out["available"]) == 2


def test_control(base_url, fake):
    run(_call(base_url, "control", {"action": "pause"}))
    assert fake.calls[-1][1] == f"/Sessions/{SESSION}/Playing/Pause"
    run(_call(base_url, "control", {"action": "back", "seconds": 30}))
    assert fake.calls[-1][1].endswith("/Seek")
    assert fake.calls[-1][2]["seekPositionTicks"] == str(570 * 10_000_000)


def test_whats_playing(base_url, fake):
    out = run(_call(base_url, "whats_playing", {}))
    assert out["playing"]["name"] == "Pulp Fiction"
    assert out["position_min"] == 10.0
    assert [t["index"] for t in out["subtitle_tracks"]] == [3, 4, 5]


def test_rejects_invalid_ids(base_url, fake):
    for tool, args in [
        ("play", {"item_id": "../Users"}),
        ("play", {"item_id": f"{M1}/../../System/Restart"}),
        ("play_episode", {"series_id": "s1?x=1", "season": 1, "episode": 1}),
    ]:
        res = run(_call_raw(base_url, tool, args))
        assert res.isError
        assert "Invalid" in res.content[0].text
    assert fake.calls == []  # nothing reached the remote-control API


def test_session_filtered_by_user(base_url, fake):
    # The kids' Wholphin session is newer, but belongs to another user
    run(_call(base_url, "control", {"action": "pause"}))
    assert fake.calls[-1][1] == f"/Sessions/{SESSION}/Playing/Pause"
    # No Wholphin session of JELLYFIN_USER -> "not connected", even though another one exists
    fake.wholphin_user = OTHER_USER
    out = run(_call(base_url, "whats_playing", {}))
    assert out["connected"] is False and "not connected" in out["message"]
    res = run(_call_raw(base_url, "control", {"action": "pause"}))
    assert res.isError and "not connected" in res.content[0].text
    assert len(fake.calls) == 1


def test_tracks_from_playing_media_source(base_url, fake):
    fake.media_source_id = SRC_4K
    out = run(_call(base_url, "whats_playing", {}))
    assert [t["title"] for t in out["subtitle_tracks"]] == ["Slovak 4K"]
    run(_call(base_url, "set_audio", {"choice": "cz"}))
    assert fake.calls[-1][3]["Arguments"] == {"Index": "1"}
    fake.media_source_id = "99999999999999999999999999999999"  # unknown -> first source
    out = run(_call(base_url, "whats_playing", {}))
    assert [t["index"] for t in out["subtitle_tracks"]] == [3, 4, 5]


def test_trailing_slash_and_secret_not_logged(base_url, fake, caplog):
    with caplog.at_level(logging.INFO, logger="jellyfin_mcp"):
        # Trailing slash must not redirect (a redirect to /mcp would drop the secret)
        r = httpx.post(f"{base_url}/{SECRET}/mcp/", json={}, follow_redirects=False)
        assert r.status_code not in (301, 302, 307, 308)
        assert httpx.get(f"{base_url}/x/{SECRET}/mcp").status_code == 404
        time.sleep(0.1)
    assert caplog.records
    assert all(SECRET not in rec.getMessage() for rec in caplog.records)


def test_mood_genres():
    assert "komed" in server.mood_genres("niečo vtipné")
    assert "rodinn" in server.mood_genres("s deťmi")
    assert server.mood_genres("western") == ["western"]
    assert server.mood_genres(None) == []


def test_show_message(base_url, fake):
    out = run(_call(base_url, "show_message", {"text": "Večera je hotová!", "seconds": 120}))
    assert out == {"shown": True, "seconds": 30.0}
    assert fake.calls[-1][1:] == (
        f"/Sessions/{SESSION}/Command", {},
        {"Name": "DisplayMessage",
         "Arguments": {"Header": "Claude", "Text": "Večera je hotová!", "TimeoutMs": "30000"}},
    )
    run(_call(base_url, "show_message", {"text": "Ahoj", "title": "Mama"}))
    assert fake.calls[-1][3]["Arguments"] == {"Header": "Mama", "Text": "Ahoj", "TimeoutMs": "8000"}


def test_show_message_validates_length(base_url, fake):
    for text in ["x" * 301, "   "]:
        res = run(_call_raw(base_url, "show_message", {"text": text}))
        assert res.isError
    assert fake.calls == []


def test_tv_tips_today(base_url, fake):
    out = run(_call(base_url, "tv_tips_today", {}))
    assert fake.tips_params == {"limit": "10", "missing": "5", "userId": USER}
    assert out["in_library"] == [{"title": "Pelíšky", "year": 1999, "time": "20:15", "channel": "JOJ",
                                  "csfd_percent": 90, "id": "1111111111111111111111111111111a"}]
    miss = out["not_in_library"][0]
    assert miss["title"] == "Vesničko má středisková" and miss["media_type"] == "movie"
    assert len(miss["overview"]) <= 220 and "Poster" not in miss


def test_tv_tips_old_plugin(base_url, fake):
    fake.tips_status = 401
    res = run(_call_raw(base_url, "tv_tips_today", {}))
    assert res.isError and "too old" in res.content[0].text


def test_recommend_tonight(base_url, fake):
    out = run(_call(base_url, "recommend_tonight", {}))
    assert fake.items_params["isPlayed"] == "false" and fake.items_params["userId"] == USER
    assert [c["name"] for c in out["candidates"]] == ["Dlhá dráma", "Akčňák", "Komédia 1", "Komédia 2", "Horor"]
    assert out["candidates"][0]["csfd_percent"] == 91
    assert "ČSFD 91 %" in out["candidates"][0]["reason"]

    out = run(_call(base_url, "recommend_tonight", {"mood": "niečo vtipné", "max_minutes": 97}))
    assert [c["name"] for c in out["candidates"]] == ["Komédia 1"]
    assert "sedí na náladu" in out["candidates"][0]["reason"]

    out = run(_call(base_url, "recommend_tonight", {"mood": "western"}))
    assert "note" in out and len(out["candidates"]) == 5


def test_request_on_seerr_not_configured(base_url, fake, monkeypatch):
    monkeypatch.delenv("SEERR_URL", raising=False)
    monkeypatch.delenv("SEERR_API_KEY", raising=False)
    server.set_seerr(None)
    res = run(_call_raw(base_url, "request_on_seerr", {"title": "Pelíšky"}))
    assert res.isError and "Seerr nie je nastavený" in res.content[0].text


def test_request_on_seerr(base_url, fake, fake_seerr):
    out = run(_call(base_url, "request_on_seerr", {"title": "Pelíšky a pelechy", "year": 1999}))
    assert out["requested"] is True and out["request_id"] == 77 and out["tmdb_id"] == 500
    assert fake_seerr.requests == [{"mediaType": "movie", "mediaId": 500}]
    assert fake_seerr.queries == ["Pelíšky a pelechy"]
    # Seerr rejects '+' for spaces, they must be percent-encoded
    assert b"+" not in fake_seerr.raw_queries[0]
    assert fake_seerr.api_keys == {"seerr-key"}

    out = run(_call(base_url, "request_on_seerr", {"title": "Cosy Dens", "year": 2019}))
    assert out["requested"] is False and out["already"] == "available"

    out = run(_call(base_url, "request_on_seerr", {"title": "Cosy Dens", "media_type": "tv"}))
    assert out["requested"] is True
    assert fake_seerr.requests[-1] == {"mediaType": "tv", "mediaId": 600, "seasons": "all"}

    out = run(_call(base_url, "request_on_seerr", {"title": "Cosy Dens", "year": 1970}))
    assert out["requested"] is False and "not found" in out["message"]
    assert len(fake_seerr.requests) == 2
