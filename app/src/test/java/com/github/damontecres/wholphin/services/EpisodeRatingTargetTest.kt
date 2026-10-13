package com.github.damontecres.wholphin.services

import org.junit.Assert
import org.junit.Test
import java.util.UUID

class EpisodeRatingTargetTest {
    private val season = RatingCandidate(UUID.randomUUID(), csfdId = 1002, title = "Dark", seasonNumber = 2)
    private val seasonWithoutId = season.copy(csfdId = null)
    private val series = RatingCandidate(UUID.randomUUID(), csfdId = 1000, title = "Dark")

    @Test
    fun `Last episode of a season rates the season`() {
        val pending = episodeRatingTarget(lastOfSeason = true, season = season, series = series, myRatings = mapOf())

        Assert.assertEquals(PendingRating(season.itemId, 1002, "Dark", 2), pending)
    }

    @Test
    fun `Episode in the middle of a season asks nothing`() {
        val pending = episodeRatingTarget(lastOfSeason = false, season = season, series = series, myRatings = mapOf())

        Assert.assertNull(pending)
    }

    @Test
    fun `Season without a ČSFD id falls back to the series`() {
        val pending =
            episodeRatingTarget(lastOfSeason = true, season = seasonWithoutId, series = series, myRatings = mapOf())

        Assert.assertEquals(PendingRating(series.itemId, 1000, "Dark", null), pending)
    }

    @Test
    fun `Already rated season asks nothing`() {
        val pending =
            episodeRatingTarget(lastOfSeason = true, season = season, series = series, myRatings = mapOf(1002 to 4))

        Assert.assertNull(pending)
    }

    @Test
    fun `Rated series does not stop asking about an unrated season`() {
        val pending =
            episodeRatingTarget(lastOfSeason = true, season = season, series = series, myRatings = mapOf(1000 to 3))

        Assert.assertEquals(season.itemId, pending?.itemId)
    }

    @Test
    fun `Already rated series asks nothing when the season has no ČSFD id`() {
        val pending =
            episodeRatingTarget(
                lastOfSeason = true,
                season = seasonWithoutId,
                series = series,
                myRatings = mapOf(1000 to 0),
            )

        Assert.assertNull(pending)
    }

    @Test
    fun `Neither season nor series with a ČSFD id asks nothing`() {
        val pending =
            episodeRatingTarget(
                lastOfSeason = true,
                season = seasonWithoutId,
                series = series.copy(csfdId = null),
                myRatings = mapOf(),
            )

        Assert.assertNull(pending)
    }
}
