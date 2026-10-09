package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.CsfdRankingViewModel
import org.junit.Assert
import org.junit.Test

class TestCsfdRanking {
    private fun item(
        name: String,
        csfdId: Int?,
        rating: Float?,
    ) = BaseItem(
        movie(name = name).copy(
            providerIds = csfdId?.let { mapOf("Csfd" to it.toString()) } ?: mapOf("Tmdb" to "1"),
            communityRating = rating,
        ),
    )

    @Test
    fun `Items without a ČSFD rating are skipped`() {
        val items =
            listOf(
                item("no rating", 1, null),
                item("best", 2, 9.5f),
                item("tmdb only", null, 9.0f),
                item("good", 3, 8.0f),
                item("also no rating", 4, null),
            )

        val selection = CsfdRankingViewModel.selectRanked(items, 10)

        Assert.assertEquals(listOf(2, 3), selection.items.map { it.first })
        Assert.assertEquals(2, selection.skippedWithoutRating)
    }

    @Test
    fun `Selection stops at the limit`() {
        val items = (1..5).map { item("film $it", it, 10f - it) }

        val selection = CsfdRankingViewModel.selectRanked(items, 3)

        Assert.assertEquals(listOf(1, 2, 3), selection.items.map { it.first })
        Assert.assertEquals(0, selection.skippedWithoutRating)
    }
}
