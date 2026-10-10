package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonal.Holiday
import com.github.damontecres.wholphin.ui.util.ResArgStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.io.path.createTempDirectory

class HomeCacheTest {
    private val tempDir = createTempDirectory("home-cache-test").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun cache(): HomeCache {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns tempDir
        return HomeCache(context)
    }

    private val continueWatching = HomeRowConfig.ContinueWatching()
    private val tips = HomeRowConfig.CsfdTvTips()
    private val recentlyAdded = HomeRowConfig.RecentlyAdded(UUID.randomUUID())

    private fun success(
        title: String,
        vararg items: BaseItem,
        rowType: HomeRowConfig? = null,
    ) = HomeRowLoadingState.Success(StringStringProvider(title), items.toList(), rowType = rowType)

    private fun pending(index: Int) = HomeRowLoadingState.Pending(StringStringProvider("pending $index"))

    @Test
    fun restoreRows_noCache() {
        assertNull(HomeCache.restoreRows(null, listOf(continueWatching), null, ::pending))
    }

    @Test
    fun restoreRows_matchesRowsByConfig() {
        val resume = success("resume", BaseItem(movie(name = "A")), rowType = continueWatching)
        val tipsRow = success("tips", BaseItem(movie(name = "B")), rowType = tips)
        val cached =
            HomeCache.toCache(
                rows = listOf(resume, tipsRow, HomeRowLoadingState.Pending(StringStringProvider("x"))),
                settingsRows = listOf(continueWatching, tips, recentlyAdded),
                seasonal = null,
            )
        // Only loaded rows are cached
        assertEquals(2, cached.rows.size)

        // The rows were reordered and one was added since
        val nextUp = HomeRowConfig.NextUp()
        val restored = HomeCache.restoreRows(cached, listOf(tips, nextUp, continueWatching, recentlyAdded), null, ::pending)!!
        assertEquals(tipsRow, restored[0])
        assertEquals(pending(1), restored[1])
        assertEquals(resume, restored[2])
        assertEquals(pending(3), restored[3])
    }

    @Test
    fun restoreRows_nothingMatches() {
        val cached =
            HomeCache.toCache(
                rows = listOf(success("resume", BaseItem(movie()), rowType = continueWatching)),
                settingsRows = listOf(continueWatching),
                seasonal = null,
            )
        assertNull(HomeCache.restoreRows(cached, listOf(tips), null, ::pending))
        // Changed view options are another row
        val changed = continueWatching.updateViewOptions(HomeRowViewOptions(heightDp = 999))
        assertNull(HomeCache.restoreRows(cached, listOf(changed), null, ::pending))
    }

    @Test
    fun restoreRows_seasonal() {
        val seasonalRow = success("halloween", BaseItem(movie()))
        val resume = success("resume", BaseItem(movie()), rowType = continueWatching)
        val cached =
            HomeCache.toCache(
                rows = listOf(seasonalRow, resume),
                settingsRows = listOf(continueWatching),
                seasonal = Holiday.HALLOWEEN,
            )
        assertEquals(Holiday.HALLOWEEN.key, cached.seasonal)

        val same = HomeCache.restoreRows(cached, listOf(continueWatching), Holiday.HALLOWEEN, ::pending)!!
        assertEquals(listOf(seasonalRow, resume), same)

        // Another holiday: its row is not the cached one
        val other = HomeCache.restoreRows(cached, listOf(continueWatching), Holiday.CHRISTMAS, ::pending)!!
        assertEquals(listOf(pending(0), resume), other)

        // No holiday anymore
        val none = HomeCache.restoreRows(cached, listOf(continueWatching), null, ::pending)!!
        assertEquals(listOf(resume), none)
    }

    @Test
    fun mergeRow_keepsPreviousOnError() {
        val previous = success("tips", BaseItem(movie()), rowType = tips)
        val error = HomeRowLoadingState.Error(StringStringProvider("tips"), exception = RuntimeException("boom"))
        assertSame(previous, HomeCache.mergeRow(previous, error))
        val unavailable =
            HomeRowLoadingState.Error(StringStringProvider("tips"), exception = CsfdRowUnavailableException("slow"))
        assertSame(previous, HomeCache.mergeRow(previous, unavailable))
    }

    @Test
    fun mergeRow_newResultReplaces() {
        val previous = success("tips", BaseItem(movie()), rowType = tips)
        val new = success("tips", rowType = tips)
        assertSame(new, HomeCache.mergeRow(previous, new))
        assertSame(new, HomeCache.mergeRow(pending(0), new))
    }

    @Test
    fun mergeRow_unavailableWithoutPreviousIsHidden() {
        val unavailable =
            HomeRowLoadingState.Error(StringStringProvider("tips"), exception = CsfdRowUnavailableException("slow"))
        val merged = HomeCache.mergeRow(pending(0), unavailable)
        assertTrue(merged is HomeRowLoadingState.Success && merged.items.isEmpty())
        // Other errors are still shown
        val error = HomeRowLoadingState.Error(StringStringProvider("x"), exception = RuntimeException("boom"))
        assertSame(error, HomeCache.mergeRow(pending(0), error))
        assertSame(error, HomeCache.mergeRow(null, error))
    }

    @Test
    fun putThenGet_fromDisk() =
        runTest {
            val userId = UUID.randomUUID()
            val discover =
                DiscoverItem(
                    id = 42,
                    type = SeerrItemType.MOVIE,
                    title = "Missing",
                    subtitle = null,
                    overview = "Plot",
                    availability = SeerrAvailability.UNKNOWN,
                    releaseDate = null,
                    posterUrl = "https://example.com/p.jpg",
                    backDropUrl = null,
                    logoUrl = null,
                    jellyfinItemId = null,
                )
            val notInLibrary =
                BaseItem(
                    data = movie(name = "Missing"),
                    imageUrlOverride = "https://example.com/p.jpg",
                    destinationOverride = Destination.DiscoveredItem(discover),
                    inLibrary = false,
                )
            val rows =
                listOf(
                    HomeRowLoadingState.Success(
                        ResArgStringProvider(R.string.csfd_tv_tips_day, "pondelok"),
                        listOf(BaseItem(movie(name = "In library")), notInLibrary),
                        HomeRowViewOptions.csfdTipsDefault,
                        rowType = tips,
                    ),
                    HomeRowLoadingState.Success(
                        ResStringProvider(R.string.continue_watching),
                        listOf(BaseItem(movie(name = "Resume"), useSeriesForPrimary = true)),
                        rowType = continueWatching,
                        showViewMore = false,
                    ),
                )
            val home = HomeCache.toCache(rows, listOf(tips, continueWatching), null)
            cache().put(userId, home)

            // A new instance has only the disk copy
            val loaded = cache().get(userId)
            assertEquals(home, loaded)
            val restored = HomeCache.restoreRows(loaded, listOf(tips, continueWatching), null, ::pending)
            assertEquals(rows, restored)
        }

    @Test
    fun get_unknownUserOrBrokenFile() =
        runTest {
            val userId = UUID.randomUUID()
            assertNull(cache().get(userId))
            val dir = java.io.File(tempDir, "home").apply { mkdirs() }
            java.io.File(dir, "${userId.toString().replace("-", "")}.json").writeText("{not json")
            assertNull(cache().get(userId))
        }

    @Test
    fun putInMemory_isReturned() =
        runTest {
            val userId = UUID.randomUUID()
            val cache = cache()
            val home = CachedHome(listOf(CachedHomeRow(continueWatching, StringStringProvider("a"), listOf())))
            cache.putInMemory(userId, home)
            assertSame(home, cache.get(userId))
        }
}
