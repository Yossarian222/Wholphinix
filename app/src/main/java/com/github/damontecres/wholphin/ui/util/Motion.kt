package com.github.damontecres.wholphin.ui.util

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared animation timings so the drawer, screen changes and dialogs move the same way (#49).
 *
 * Short tweens with an easing curve instead of springs: they finish in a predictable time and
 * look smoother on the RPi5, where a long animation of two whole screens drops frames.
 */
object Motion {
    const val DRAWER_MILLIS = 260
    const val SCREEN_MILLIS = 240
    const val DIALOG_MILLIS = 200
    const val SCROLL_MILLIS = 220

    /** Drawer width and the content offset next to it, both must use the same spec to stay in sync */
    fun <T> drawer(): FiniteAnimationSpec<T> = tween(DRAWER_MILLIS, easing = FastOutSlowInEasing)

    fun <T> scroll(): FiniteAnimationSpec<T> = tween(SCROLL_MILLIS, easing = FastOutSlowInEasing)

    /** Navigating forward: the new screen fades in with a small slide from the right */
    fun screenForward(): ContentTransform =
        (
            fadeIn(tween(SCREEN_MILLIS, delayMillis = 40, easing = LinearOutSlowInEasing)) +
                slideInHorizontally(tween(SCREEN_MILLIS, easing = FastOutSlowInEasing)) { it / 24 }
        ).togetherWith(
            fadeOut(tween(SCREEN_MILLIS / 2)),
        )

    /** Going back: the previous screen fades in with a small slide from the left */
    fun screenBack(): ContentTransform =
        (
            fadeIn(tween(SCREEN_MILLIS, delayMillis = 40, easing = LinearOutSlowInEasing)) +
                slideInHorizontally(tween(SCREEN_MILLIS, easing = FastOutSlowInEasing)) { -it / 24 }
        ).togetherWith(
            fadeOut(tween(SCREEN_MILLIS / 2)) +
                slideOutHorizontally(tween(SCREEN_MILLIS / 2)) { it / 24 },
        )

    /** Dialogs, popups and small panels appearing */
    val dialogEnter: EnterTransition =
        fadeIn(tween(DIALOG_MILLIS, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(DIALOG_MILLIS, easing = FastOutSlowInEasing), initialScale = .94f)

    val dialogExit: ExitTransition =
        fadeOut(tween(DIALOG_MILLIS * 3 / 4)) +
            scaleOut(tween(DIALOG_MILLIS * 3 / 4), targetScale = .96f)

    /** Content that just appears in place (rows, labels, overlays) */
    val fadeEnter: EnterTransition = fadeIn(tween(DIALOG_MILLIS, easing = LinearOutSlowInEasing))
    val fadeExit: ExitTransition = fadeOut(tween(DIALOG_MILLIS * 3 / 4))
}

/**
 * Progress 0 → 1 of a dialog appearing, started when it enters the composition.
 * Use with [appear] on the dialog's root, Compose dialogs otherwise pop up without any animation.
 */
@Composable
fun rememberAppearProgress(): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Motion.DIALOG_MILLIS, easing = FastOutSlowInEasing))
    }
    return progress
}

/** Fades and slightly zooms in the content while [progress] goes from 0 to 1 */
fun Modifier.appear(progress: Animatable<Float, *>): Modifier =
    graphicsLayer {
        val p = progress.value
        alpha = p
        val scale = .94f + .06f * p
        scaleX = scale
        scaleY = scale
    }
