package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SeasonalDailyShuffleTest {
    private fun tip(
        id: Int,
        inLibrary: Boolean,
    ) = CsfdTvTip(
        csfdId = id,
        title = "Film $id",
        year = 2000,
        itemId = if (inLibrary) UUID.randomUUID() else null,
        ratingPercent = 100 - id,
        isSeries = false,
        titles = listOf(),
        poster = null,
    )

    private val tips = (1..60).map { tip(it, inLibrary = it <= 40) }
    private val day = LocalDate.of(2026, 12, 22)

    @Test
    fun `Same order all day`() {
        assertEquals(
            dailyShuffle(tips, "christmas", day, 20, 10),
            dailyShuffle(tips, "christmas", day, 20, 10),
        )
    }

    @Test
    fun `Different order the next day`() {
        assertNotEquals(
            dailyShuffle(tips, "christmas", day, 20, 10).map { it.csfdId },
            dailyShuffle(tips, "christmas", day.plusDays(1), 20, 10).map { it.csfdId },
        )
    }

    @Test
    fun `Library titles stay first and the groups are cut`() {
        val result = dailyShuffle(tips, "christmas", day, 20, 10)
        assertEquals(30, result.size)
        assertTrue(result.take(20).all { it.itemId != null })
        assertTrue(result.drop(20).all { it.itemId == null })
    }
}
