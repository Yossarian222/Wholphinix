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
            "control", "whats_playing", "continue_watching"} <= names


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
