package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.CsfdRankingViewModel
import com.github.damontecres.wholphin.ui.components.OutsideTopHeader
import com.github.damontecres.wholphin.ui.components.RankedItem
import com.github.damontecres.wholphin.ui.components.RankingEntry
import org.junit.Assert
import org.junit.Test

class TestCsfdRanking {
    private fun item(
        name: String,
        csfdId: Int?,
        rating: Float?,
        votes: Int? = null,
    ) = BaseItem(
        movie(name = name).copy(
            providerIds =
                (csfdId?.let { mapOf("Csfd" to it.toString()) } ?: mapOf("Tmdb" to "1")) +
                    (votes?.let { mapOf("CsfdVotes" to it.toString()) } ?: mapOf()),
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

    private fun names(entries: List<RankingEntry>) = entries.map { (it as? RankedItem)?.item?.name ?: "---" }

    // Sorted by rating as the server returns them
    private val byRating =
        listOf(
            item("pirates", 10, 9.6f),
            item("first", 1, 9.5f),
            item("alpinist", 11, 9.4f),
            item("eighth", 8, 9.3f),
            item("second", 2, 9.0f),
            item("other", 12, 8.0f),
        )
    private val ranks = mapOf(1 to 1, 2 to 2, 8 to 8)

    @Test
    fun `Ranked items come first by position, then the others by rating`() {
        val entries = CsfdRankingViewModel.arrange(CsfdRankingViewModel.selectRanked(byRating).items, ranks)

        Assert.assertEquals(listOf("first", "second", "eighth", "---", "pirates", "alpinist", "other"), names(entries))
        Assert.assertEquals(listOf(1, 2, 8), entries.take(3).map { (it as RankedItem).csfdRank })
        Assert.assertSame(OutsideTopHeader, entries[3])
    }

    @Test
    fun `Without ranks the items stay by rating without the header`() {
        val items = CsfdRankingViewModel.selectRanked(byRating).items

        val notLoaded = CsfdRankingViewModel.arrange(items, null, limit = 4)
        val noneRanked = CsfdRankingViewModel.arrange(items, mapOf(999 to 1))

        Assert.assertEquals(listOf("pirates", "first", "alpinist", "eighth"), names(notLoaded))
        Assert.assertEquals(listOf("pirates", "first", "alpinist", "eighth", "second", "other"), names(noneRanked))
    }

    @Test
    fun `Limit applies only to the items outside the top`() {
        val entries = CsfdRankingViewModel.arrange(CsfdRankingViewModel.selectRanked(byRating).items, ranks, limit = 1)

        Assert.assertEquals(listOf("first", "second", "eighth", "---", "pirates"), names(entries))
    }

    @Test
    fun `Header is dropped when a filter leaves nothing on one of its sides`() {
        val entries = CsfdRankingViewModel.arrange(CsfdRankingViewModel.selectRanked(byRating).items, ranks)

        val onlyOthers = CsfdRankingViewModel.filterEntries(entries) { it.csfdRank == null }
        val onlyRanked = CsfdRankingViewModel.filterEntries(entries) { it.csfdRank != null }
        val both = CsfdRankingViewModel.filterEntries(entries) { it.item.name in setOf("eighth", "other") }

        Assert.assertEquals(listOf("pirates", "alpinist", "other"), names(onlyOthers))
        Assert.assertEquals(listOf("first", "second", "eighth"), names(onlyRanked))
        Assert.assertEquals(listOf("eighth", "---", "other"), names(both))
    }

    @Test
    fun `Fetching goes on while the ranking is not full or ratings are high enough for the top`() {
        val high = listOf(item("a", 1, 9f), item("b", 2, 7.5f))
        val low = listOf(item("a", 1, 9f), item("b", 2, 6.5f))
        val full = CsfdRankingViewModel.RANKING_SIZE

        Assert.assertTrue(CsfdRankingViewModel.shouldFetchMore(full - 1, low))
        Assert.assertTrue(CsfdRankingViewModel.shouldFetchMore(full, high))
        Assert.assertFalse(CsfdRankingViewModel.shouldFetchMore(full, low))
        Assert.assertFalse(CsfdRankingViewModel.shouldFetchMore(0, listOf(item("no rating", 1, null))))
    }

    @Test
    fun `Items outside the top rated from few votes are left out`() {
        val items =
            listOf(
                item("few votes", 20, 9.8f, votes = 40),
                item("few votes but ranked", 1, 9.7f, votes = 50),
                item("enough votes", 21, 9.0f, votes = 100),
                item("unknown votes", 22, 8.5f),
            )
        val selection = CsfdRankingViewModel.selectRanked(items).items

        Assert.assertEquals(
            listOf("few votes but ranked", "---", "enough votes", "unknown votes"),
            names(CsfdRankingViewModel.arrange(selection, mapOf(1 to 5))),
        )
        Assert.assertEquals(
            listOf("enough votes", "unknown votes"),
            names(CsfdRankingViewModel.arrange(selection, null)),
        )
    }
}
