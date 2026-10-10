package com.github.damontecres.wholphin.ui.detail

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences

/**
 * A tab of a movie or TV library page, identified by a stable [key] which is stored in the preferences
 */
enum class LibraryTab(
    val key: String,
    @param:StringRes val title: Int,
) {
    LIBRARY("library", R.string.library),
    COLLECTIONS("collections", R.string.collections),
    GENRES("genres", R.string.genres),
    STUDIOS("studios", R.string.studios),
    CSFD_RANKINGS("csfd_rankings", R.string.csfd_rankings),
}

/**
 * The kinds of libraries whose tabs can be reordered
 */
enum class LibraryTabsKind(
    /** The tabs in their default order */
    val defaultTabs: List<LibraryTab>,
) {
    MOVIES(
        listOf(
            LibraryTab.LIBRARY,
            LibraryTab.COLLECTIONS,
            LibraryTab.GENRES,
            LibraryTab.CSFD_RANKINGS,
        ),
    ),
    TV(
        listOf(
            LibraryTab.LIBRARY,
            LibraryTab.GENRES,
            LibraryTab.STUDIOS,
            LibraryTab.CSFD_RANKINGS,
        ),
    ),
}

/**
 * Orders [available] tabs by the [savedKeys] from the preferences.
 *
 * Unknown or duplicate keys are ignored, tabs missing from [savedKeys] (e.g. added in a newer version)
 * are appended in their default order.
 */
fun resolveLibraryTabOrder(
    savedKeys: List<String>,
    available: List<LibraryTab>,
): List<LibraryTab> {
    val byKey = available.associateBy { it.key }
    val ordered = savedKeys.mapNotNull { byKey[it] }.distinct()
    return ordered + available.filterNot { it in ordered }
}

/**
 * The stored tab order keys for the given kind of library
 */
fun AppPreferences.libraryTabOrderKeys(kind: LibraryTabsKind): List<String> =
    when (kind) {
        LibraryTabsKind.MOVIES -> interfacePreferences.movieLibraryTabOrderList
        LibraryTabsKind.TV -> interfacePreferences.tvLibraryTabOrderList
    }

/**
 * The tabs of the given kind of library in the user's order
 */
fun AppPreferences.libraryTabs(kind: LibraryTabsKind): List<LibraryTab> =
    resolveLibraryTabOrder(libraryTabOrderKeys(kind), kind.defaultTabs)

/**
 * Returns a copy of the preferences with the tab order for the given kind of library replaced
 */
fun AppPreferences.withLibraryTabOrder(
    kind: LibraryTabsKind,
    tabs: List<LibraryTab>,
): AppPreferences {
    val keys = tabs.map { it.key }
    return updateInterfacePreferences {
        when (kind) {
            LibraryTabsKind.MOVIES -> {
                clearMovieLibraryTabOrder()
                addAllMovieLibraryTabOrder(keys)
            }

            LibraryTabsKind.TV -> {
                clearTvLibraryTabOrder()
                addAllTvLibraryTabOrder(keys)
            }
        }
    }
}

/**
 * Key used to remember the selected tab of a library page.
 *
 * It includes the tab order, so an index remembered for another order (or saved by an older version
 * which had a "Recommended" tab first) is not applied to a different tab.
 */
fun libraryTabsRememberKey(
    itemId: String,
    tabs: List<LibraryTab>,
): String = itemId + "#" + tabs.joinToString(",") { it.key }

/**
 * Moves the element at [index] one position up (towards the start) or down; out of range moves are ignored
 */
fun <T> List<T>.moveByOne(
    index: Int,
    up: Boolean,
): List<T> {
    val target = if (up) index - 1 else index + 1
    if (index !in indices || target !in indices) return this
    return toMutableList().apply {
        val item = removeAt(index)
        add(target, item)
    }
}
