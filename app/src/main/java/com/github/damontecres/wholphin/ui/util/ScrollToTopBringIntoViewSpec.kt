package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp

/**
 * Overrides scrolling so that the item being scrolled to is at the top of the view offset by the provided pixels
 *
 * Note: the offset is necessary for anything that is focuseable, but has content before (eg a title) that needs to be displayed too
 *
 * Note: this applies to ALL scrollable composables within its scope, so a [LazyColumn] of [androidx.compose.foundation.lazy.LazyRow]s likely needs nested [LocalBringIntoViewSpec] overrides
 *
 * Example:
 * ```kotlin
 * val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
 * CompositionLocalProvider(LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx)) {
 *     LazyColumn{
 *         items(list){
 *             CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
 *                 // Content
 *             }
 *         }
 *     }
 * }
 * ```
 */
class ScrollToTopBringIntoViewSpec(
    val spaceAbovePx: Float = 100f,
    /**
     * Room kept under the item: the focused element is usually only the card's image, the title and subtitle under it
     * (and the focus zoom) would otherwise end up below the bottom of the screen when the row does not fit (#73)
     */
    val spaceBelowPx: Float = 0f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val toTop = offset - spaceAbovePx
        // Scroll further when the item and the room under it would not fit, but never past the item's own top
        val toFitBottom = offset + size + spaceBelowPx - containerSize
        return maxOf(toTop, minOf(toFitBottom, offset))
    }
}

/** Title + subtitle under a card plus its focus zoom, see [ScrollToTopBringIntoViewSpec.spaceBelowPx] */
val CardTextRoom = 64.dp
