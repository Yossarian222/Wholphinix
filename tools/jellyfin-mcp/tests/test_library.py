import asyncio

import httpx
import pytest

from jellyfin_mcp import server
from jellyfin_mcp.jellyfin import JellyfinClient
from test_server import USER

MOVIE = "a" * 32
SERIES = "b" * 32
HANKS = "c" * 32


class FakeLibrary:
    def __init__(self) -> None:
        self.items_params: list[dict] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        path, params = request.url.path, dict(request.url.params)
        if path == "/Users":
            return httpx.Response(200, json=[{"Name": "Roman", "Id": USER}])
        if path == "/Genres":
            return httpx.Response(200, json={"Items": [{"Name": "Horor"}, {"Name": "Komédia"}, {"Name": "Dráma"}]})
        if path == "/Persons":
            return httpx.Response(200, json={"Items": [{"Name": "Tom Hanks", "Id": HANKS},
                                                       {"Name": "Colin Hanks", "Id": "d" * 32}]})
        if path == "/Items/Counts":
            return httpx.Response(200, json={"MovieCount": 412, "SeriesCount": 37, "EpisodeCount": 2210})
        if path == "/Items":
            self.items_params.append(params)
            if params.get("sortBy") == "DatePlayed":
                return httpx.Response(200, json={"Items": [
                    {"Id": MOVIE, "Name": "Forrest Gump", "Type": "Movie", "ProductionYear": 1994,
                     "UserData": {"Played": True, "LastPlayedDate": "2026-10-09T19:05:00.1234567Z"}},
                    {"Id": "e" * 32, "Name": "Pilot", "Type": "Episode", "SeriesName": "Dark",
                     "ParentIndexNumber": 1, "IndexNumber": 1,
                     "UserData": {"PlayedPercentage": 42.4, "LastPlayedDate": "2026-10-08T20:00:00Z"}},
                    {"Id": "f" * 32, "Name": "Never played", "Type": "Movie", "UserData": {}},
                ]})
            played = params.get("isPlayed")
            total = {"true": 300, "false": 112}.get(played, 3)
            return httpx.Response(200, json={"TotalRecordCount": total, "Items": [
                {"Id": MOVIE, "Name": "Forrest Gump", "Type": "Movie", "ProductionYear": 1994,
                 "CommunityRating": 9.3, "Genres": ["Dráma", "Komédia", "Romantický", "Iný"],
                 "RunTimeTicks": 142 * 60 * 10_000_000, "UserData": {"Played": True}},
            ]})
        if path == f"/Items/{MOVIE}":
            return httpx.Response(200, json={
                "Id": MOVIE, "Name": "Forrest Gump", "OriginalTitle": "Forrest Gump", "Type": "Movie",
                "ProductionYear": 1994, "CommunityRating": 9.3, "OfficialRating": "12",
                "ProviderIds": {"CsfdVotes": "180000"}, "RunTimeTicks": 142 * 60 * 10_000_000,
                "Genres": ["Dráma"], "Overview": "Život je ako bonboniéra.",
                "People": [{"Name": "Robert Zemeckis", "Type": "Director"},
                           {"Name": "Tom Hanks", "Type": "Actor", "Role": "Forrest Gump"},
                           {"Name": "Robin Wright", "Type": "Actor"}],
                "UserData": {"Played": True, "PlayCount": 2, "LastPlayedDate": "2026-10-09T19:05:00Z"},
                "MediaSources": [{"Name": "4K", "MediaStreams": [
                    {"Type": "Video", "Width": 3840, "Height": 2160, "VideoRangeType": "HDR10"},
                    {"Type": "Audio", "Language": "cze", "DisplayTitle": "Czech 5.1"},
                    {"Type": "Audio", "Language": "eng", "DisplayTitle": "English 5.1"},
                    {"Type": "Subtitle", "Language": "slo"}, {"Type": "Subtitle", "Language": "slo"},
                ]}],
            })
        if path == f"/Items/{SERIES}":
            return httpx.Response(200, json={"Id": SERIES, "Name": "Dark", "Type": "Series",
                                             "Status": "Ended", "UserData": {}})
        if path == f"/Shows/{SERIES}/Episodes":
            return httpx.Response(200, json={"Items": [
                {"Id": f"{n:032x}", "ParentIndexNumber": s, "IndexNumber": n,
                 "UserData": {"Played": s == 1}}
                for s, n in [(1, 1), (1, 2), (2, 1), (2, 2), (2, 3)]]})
        if path == "/Shows/NextUp":
            return httpx.Response(200, json={"Items": [{"Id": "9" * 32, "Name": "Ghosts",
                                                        "ParentIndexNumber": 2, "IndexNumber": 1}]})
        return httpx.Response(404)


@pytest.fixture
def lib(monkeypatch):
    monkeypatch.setenv("TIMEZONE", "Europe/Bratislava")
    f = FakeLibrary()
    server.set_client(JellyfinClient("http://jf", "key", "Roman", transport=httpx.MockTransport(f.handler)))
    return f


def run(coro):
    return asyncio.run(coro)


def test_browse_genre_person_years_and_total(lib):
    out = run(server.browse_library(genre="komedie", person="hanks", year_from=1990, year_to=1999,
                                    watched=False, min_csfd_percent=80, limit=5))
    p = lib.items_params[-1]
    assert p["genres"] == "Komédia" and p["personIds"] == HANKS and p["isPlayed"] == "false"
    assert p["years"].split(",")[0] == "1990" and p["years"].split(",")[-1] == "1999"
    assert p["minCommunityRating"] == "8.0" and p["limit"] == "5" and p["includeItemTypes"] == "Movie"
    assert p["sortBy"].startswith("CommunityRating") and p["enableTotalRecordCount"] == "true"
    assert out["total"] == 112 and out["person"] == "Tom Hanks" and "Colin Hanks" in out["note"]
    item = out["items"][0]
    assert item == {"id": MOVIE, "name": "Forrest Gump", "year": 1994, "csfd_percent": 93,
                    "genres": ["Dráma", "Komédia", "Romantický"], "runtime_min": 142, "watched": True}


def test_browse_unknown_genre_lists_available(lib):
    out = run(server.browse_library(genre="western"))
    assert out["total"] == 0 and out["available_genres"] == ["Horor", "Komédia", "Dráma"]
    assert lib.items_params == []


def test_browse_horror_english_word_and_limit_cap(lib):
    run(server.browse_library(genre="horror", kind="series", sort="newest_added", limit=500))
    p = lib.items_params[-1]
    assert p["genres"] == "Horor" and p["includeItemTypes"] == "Series"
    assert p["sortBy"].startswith("DateCreated") and p["limit"] == "50"


def test_item_details_movie(lib):
    out = run(server.item_details(MOVIE))
    assert out["csfd_percent"] == 93 and out["csfd_votes"] == "180000" and out["age_rating"] == "12"
    assert out["directors"] == ["Robert Zemeckis"]
    assert out["cast"] == ["Tom Hanks (Forrest Gump)", "Robin Wright"]
    assert out["video"] == "4K HDR10" and out["audio"] == ["Czech 5.1", "English 5.1"]
    assert out["subtitles"] == ["slo"] and out["play_count"] == 2
    assert out["last_played"] == "9. 10. 2026 21:05"  # UTC -> Bratislava (CEST)
    assert "original_title" not in out  # same as the name


def test_item_details_series(lib):
    out = run(server.item_details(SERIES))
    assert out["seasons"] == [{"season": 1, "episodes": 2, "watched": 2},
                              {"season": 2, "episodes": 3, "watched": 0}]
    assert out["episodes_total"] == 5 and out["status"] == "Ended"
    assert out["next_episode"]["season"] == 2 and out["next_episode"]["episode"] == 1


def test_item_details_rejects_bad_id(lib):
    with pytest.raises(ValueError):
        run(server.item_details("../Users"))


def test_watch_history(lib):
    out = run(server.watch_history(limit=10))["history"]
    assert [h["name"] for h in out] == ["Forrest Gump", "Pilot"]  # never played one dropped
    assert out[0]["last_played"] == "9. 10. 2026 21:05" and out[0]["watched"] is True
    assert out[1]["series"] == "Dark" and out[1]["progress_percent"] == 42
    assert lib.items_params[-1]["sortOrder"] == "Descending"


def test_library_stats(lib):
    out = run(server.library_stats())
    assert out == {"movies": 412, "series": 37, "episodes": 2210, "movies_watched": 300,
                   "movies_unwatched": 112, "movie_genres": ["Horor", "Komédia", "Dráma"]}
