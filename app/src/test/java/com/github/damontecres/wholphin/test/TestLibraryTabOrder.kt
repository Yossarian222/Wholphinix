package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.detail.LibraryTab
import com.github.damontecres.wholphin.ui.detail.LibraryTabsKind
import com.github.damontecres.wholphin.ui.detail.libraryTabsRememberKey
import com.github.damontecres.wholphin.ui.detail.moveByOne
import com.github.damontecres.wholphin.ui.detail.resolveLibraryTabOrder
import org.junit.Assert
import org.junit.Test

class TestLibraryTabOrder {
    private val movieTabs = LibraryTabsKind.MOVIES.defaultTabs
    private val tvTabs = LibraryTabsKind.TV.defaultTabs

    @Test
    fun `No saved order uses the default order`() {
        Assert.assertEquals(movieTabs, resolveLibraryTabOrder(emptyList(), movieTabs))
        Assert.assertEquals(tvTabs, resolveLibraryTabOrder(emptyList(), tvTabs))
        Assert.assertEquals(LibraryTab.LIBRARY, movieTabs.first())
    }

    @Test
    fun `Saved order is applied`() {
        val saved = listOf("csfd_rankings", "genres", "collections", "library")
        Assert.assertEquals(
            listOf(LibraryTab.CSFD_RANKINGS, LibraryTab.GENRES, LibraryTab.COLLECTIONS, LibraryTab.LIBRARY),
            resolveLibraryTabOrder(saved, movieTabs),
        )
    }

    @Test
    fun `Missing tabs are appended in default order`() {
        Assert.assertEquals(
            listOf(LibraryTab.GENRES, LibraryTab.LIBRARY, LibraryTab.COLLECTIONS, LibraryTab.CSFD_RANKINGS),
            resolveLibraryTabOrder(listOf("genres"), movieTabs),
        )
    }

    @Test
    fun `Unknown, duplicate and unavailable keys are ignored`() {
        // "studios" is not a movie library tab, "recommended" was removed
        val saved = listOf("recommended", "studios", "genres", "genres", "foo", "library")
        Assert.assertEquals(
            listOf(LibraryTab.GENRES, LibraryTab.LIBRARY, LibraryTab.COLLECTIONS, LibraryTab.CSFD_RANKINGS),
            resolveLibraryTabOrder(saved, movieTabs),
        )
        Assert.assertEquals(
            listOf(LibraryTab.STUDIOS, LibraryTab.GENRES, LibraryTab.LIBRARY, LibraryTab.CSFD_RANKINGS),
            resolveLibraryTabOrder(saved, tvTabs),
        )
    }

    @Test
    fun `Move by one`() {
        val list = listOf("a", "b", "c")
        Assert.assertEquals(listOf("b", "a", "c"), list.moveByOne(1, up = true))
        Assert.assertEquals(listOf("a", "c", "b"), list.moveByOne(1, up = false))
        Assert.assertEquals(list, list.moveByOne(0, up = true))
        Assert.assertEquals(list, list.moveByOne(2, up = false))
        Assert.assertEquals(list, list.moveByOne(5, up = false))
    }

    @Test
    fun `Remember key depends on the order`() {
        val a = libraryTabsRememberKey("id", movieTabs)
        val b = libraryTabsRememberKey("id", movieTabs.reversed())
        Assert.assertNotEquals(a, b)
        Assert.assertNotEquals("id", a)
        Assert.assertEquals(a, libraryTabsRememberKey("id", resolveLibraryTabOrder(emptyList(), movieTabs)))
    }
}
