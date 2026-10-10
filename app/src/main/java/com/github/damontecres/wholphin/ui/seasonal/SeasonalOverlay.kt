package com.github.damontecres.wholphin.ui.seasonal

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.nav.CollapsedDrawerItemWidth
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.ExpandedDrawerItemWidth
import com.github.damontecres.wholphin.ui.theme.LocalNavScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** ~30 fps at most: a frame plus this pause */
private const val FRAME_MS = 25L

@HiltViewModel
class SeasonalOverlayViewModel
    @Inject
    constructor(
        screensaverService: ScreensaverService,
    ) : ViewModel() {
        val screensaverState = screensaverService.state
    }

/**
 * The animated seasonal decorations (snow, hearts, ghost...) drawn above the pages with the nav drawer.
 *
 * Draws nothing on full screen pages (player, slideshow, music now playing, settings...), while the screensaver is shown,
 * in power saving mode or with animations turned off. It only draws (no focusable or clickable elements), so it never
 * takes the focus or key presses. Animations redraw only this layer, at most ~30 times a second, and the occasional
 * ones (ghost, Cupid's arrow...) do not run at all in between.
 */
@Composable
fun SeasonalOverlay(
    state: SeasonalState?,
    topDestination: Destination?,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SeasonalOverlayViewModel = hiltViewModel(),
) {
    // Only in the menus, never in the player or on other full screen pages
    if (state == null || topDestination == null || topDestination.fullScreen) return
    val screensaver by viewModel.screensaverState.collectAsStateWithLifecycle()
    if (screensaver.show) return
    if (!rememberSeasonalAnimationsAllowed()) return

    val navScale = LocalNavScale.current
    val drawerWidth =
        animateDpAsState(
            targetValue = if (drawerOpen) ExpandedDrawerItemWidth * navScale else CollapsedDrawerItemWidth * navScale,
            label = "seasonal_drawer_width",
        )
    val closedDrawerWidth = CollapsedDrawerItemWidth * navScale
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        when (state.holiday) {
            Holiday.CHRISTMAS -> {
                Snowfall()
                DrawerLights(drawerWidth)
            }

            Holiday.NICHOLAS -> {
                PeriodicEvent(initialDelayMs = 20_000, minIntervalMs = 120_000, maxIntervalMs = 240_000, durationMs = 5_000) {
                    NicholasPeek(closedDrawerWidth)
                }
            }

            Holiday.NEW_YEAR -> {
                // A burst each time the home page is shown
                val homeKey = topDestination.takeIf { it is Destination.Home }
                var burst by remember { mutableIntStateOf(0) }
                var showing by remember { mutableStateOf(false) }
                LaunchedEffect(homeKey) {
                    if (homeKey != null) {
                        burst++
                        showing = true
                        delay(FIREWORKS_MS)
                        showing = false
                    }
                }
                if (showing) {
                    key(burst) { Fireworks(burst, drawerWidth) }
                }
            }

            Holiday.VALENTINE -> {
                RisingHearts(drawerWidth)
                PeriodicEvent(initialDelayMs = 25_000, minIntervalMs = 180_000, maxIntervalMs = 300_000, durationMs = 5_500) {
                    CupidArrow(state.clockBounds, origin)
                }
            }

            Holiday.HALLOWEEN -> {
                Bats()
                PeriodicEvent(initialDelayMs = 30_000, minIntervalMs = 180_000, maxIntervalMs = 300_000, durationMs = 9_000) {
                    Ghost(it)
                }
            }

            Holiday.EASTER -> {
                PeriodicEvent(initialDelayMs = 15_000, minIntervalMs = 120_000, maxIntervalMs = 180_000, durationMs = 6_000) {
                    BunnyPeek()
                }
            }
        }
    }
}

/**
 * Milliseconds since this was first composed, updated at most ~30 times a second. Read it only while drawing, so only
 * the drawing is repeated (no recomposition).
 */
@Composable
internal fun rememberTicker(frameMs: Long = FRAME_MS): LongState {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(frameMs) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            time.longValue = now - start
            delay(frameMs)
        }
    }
    return time
}

/**
 * Shows [content] for [durationMs] every few minutes, nothing (and no animation running) in between
 */
@Composable
private fun PeriodicEvent(
    initialDelayMs: Long,
    minIntervalMs: Long,
    maxIntervalMs: Long,
    durationMs: Long,
    content: @Composable (occurrence: Int) -> Unit,
) {
    var occurrence by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(initialDelayMs)
        while (true) {
            occurrence++
            running = true
            delay(durationMs)
            running = false
            delay(Random.nextLong(minIntervalMs, maxIntervalMs))
        }
    }
    if (running) {
        key(occurrence) { content(occurrence) }
    }
}

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private class Flake(
    random: Random,
) {
    val x = random.nextFloat()
    val y = random.nextFloat()
    val radiusDp = 1.5f + random.nextFloat() * 4.5f
    val speedDp = 18f + random.nextFloat() * 30f
    val swayDp = 8f + random.nextFloat() * 18f
    val swaySpeed = 0.3f + random.nextFloat() * 0.6f
    val phase = random.nextFloat() * 2 * PI.toFloat()
    val alpha = 0.35f + random.nextFloat() * 0.4f
}

/**
 * Gentle snowfall over the whole page
 */
@Composable
private fun Snowfall(count: Int = 36) {
    val time = rememberTicker()
    val flakes = remember { Random(24).let { r -> List(count) { Flake(r) } } }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue / 1000f
        val margin = 20.dp.toPx()
        val fall = size.height + 2 * margin
        flakes.forEach { f ->
            val y = (f.y * fall + t * f.speedDp.dp.toPx()) % fall - margin
            val x = f.x * size.width + sin(t * f.swaySpeed + f.phase) * f.swayDp.dp.toPx()
            drawSnowflake(Offset(x, y), f.radiusDp.dp.toPx(), f.alpha)
        }
    }
}

private val LightColors = listOf(Color(0xFFFF5252), Color(0xFF69F0AE), Color(0xFFFFD740), Color(0xFF40C4FF))

/**
 * A string of small twinkling lights along the right edge of the nav drawer
 */
@Composable
private fun DrawerLights(drawerWidth: State<Dp>) {
    val time = rememberTicker(frameMs = 100L)
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue / 1000f
        val x = drawerWidth.value.toPx()
        val spacing = 46.dp.toPx()
        val sag = 7.dp.toPx()
        val top = 24.dp.toPx()
        val count = ((size.height - top) / spacing).toInt() + 1
        val wire = Path()
        wire.moveTo(x, top)
        for (i in 1 until count) {
            val y0 = top + (i - 1) * spacing
            val y1 = top + i * spacing
            wire.cubicTo(x + sag, y0 + spacing * 0.3f, x + sag, y1 - spacing * 0.3f, x, y1)
        }
        drawPath(wire, Color(0xFF1B3B1F), alpha = 0.8f, style = Stroke(width = 1.5.dp.toPx()))
        val radius = 3.5.dp.toPx()
        for (i in 0 until count) {
            val color = LightColors[i % LightColors.size]
            val glow = 0.5f + 0.5f * sin(t * 1.6f + i * 2.3f)
            val center = Offset(x + 1.dp.toPx(), top + i * spacing + 4.dp.toPx())
            drawCircle(color, radius = radius * 2.2f, center = center, alpha = 0.18f * glow)
            drawCircle(color, radius = radius, center = center, alpha = 0.45f + 0.55f * glow)
        }
    }
}

/**
 * St. Nicholas's head slides down from the top edge above the nav drawer, winks and hides again
 */
@Composable
private fun NicholasPeek(closedDrawerWidth: Dp) {
    val time = rememberTicker()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue
        val s = 54.dp.toPx()
        val shown =
            when {
                t < 900 -> smoothStep(t / 900f)
                t < 4_100 -> 1f
                else -> 1f - smoothStep((t - 4_100) / 900f)
            }
        val x = closedDrawerWidth.toPx() / 2 - s / 2
        val y = -s + shown * s * 0.95f
        val tilt = if (t in 900L..4_100L) sin((t - 900) / 1000f * 2f) * 6f else 0f
        rotate(tilt, pivot = Offset(x + s / 2, y + s / 2)) {
            drawNicholasHead(Offset(x, y), s, wink = t in 2_000L..2_500L)
        }
    }
}

private const val FIREWORKS_MS = 3_200L

private val FireworkColors =
    listOf(Color(0xFFFFD54F), Color(0xFFFF5252), Color(0xFF40C4FF), Color(0xFF69F0AE), Color(0xFFE040FB))

/**
 * A few firework bursts in the upper part of the page
 */
@Composable
private fun Fireworks(
    seed: Int,
    drawerWidth: State<Dp>,
) {
    val time = rememberTicker()
    val random = remember(seed) { Random(seed * 7919 + 1) }
    val bursts =
        remember(seed) {
            List(4) { i ->
                Triple(
                    // Start delay, position (fractions), color
                    i * 380L + random.nextLong(0, 150),
                    Offset(0.15f + random.nextFloat() * 0.7f, 0.12f + random.nextFloat() * 0.3f),
                    FireworkColors[(i + seed) % FireworkColors.size],
                )
            }
        }
    Canvas(Modifier.fillMaxSize()) {
        val now = time.longValue
        val left = drawerWidth.value.toPx() + 60.dp.toPx()
        val width = (size.width - left - 60.dp.toPx()).coerceAtLeast(1f)
        val particles = 28
        val life = 1.7f
        bursts.forEach { (start, pos, color) ->
            val t = (now - start) / 1000f
            if (t < 0f || t > life) return@forEach
            val c = Offset(left + pos.x * width, pos.y * size.height)
            val fade = 1f - (t / life) * (t / life)
            if (t < 0.15f) {
                drawCircle(Color.White, radius = 14.dp.toPx() * (t / 0.15f), center = c, alpha = 0.5f)
            }
            val speed = 150.dp.toPx()
            val gravity = 45.dp.toPx()
            for (p in 0 until particles) {
                val angle = p * 2 * PI.toFloat() / particles
                val dist = speed * (1 - exp(-2.2f * t)) / 2.2f
                val prev = speed * (1 - exp(-2.2f * (t - 0.07f).coerceAtLeast(0f))) / 2.2f
                val drop = gravity * t * t
                val end = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist + drop)
                val tail = Offset(c.x + cos(angle) * prev, c.y + sin(angle) * prev + drop)
                drawLine(color, start = tail, end = end, strokeWidth = 2.dp.toPx(), alpha = fade * 0.8f)
                drawCircle(color, radius = 2.dp.toPx(), center = end, alpha = fade)
            }
        }
    }
}

/**
 * 2–3 hearts slowly rising from the bottom like balloons, swaying and fading out
 */
@Composable
private fun RisingHearts(drawerWidth: State<Dp>) {
    val time = rememberTicker()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue / 1000f
        val period = 16f
        val left = drawerWidth.value.toPx() + 40.dp.toPx()
        val width = (size.width - left - 60.dp.toPx()).coerceAtLeast(1f)
        for (k in 0 until 3) {
            val shifted = t + k * period / 3f
            val cycle = floor(shifted / period).toInt()
            val p = shifted / period - cycle
            val random = Random(k * 1_000 + cycle)
            val heartSize = (26f + random.nextFloat() * 14f).dp.toPx()
            val x0 = left + random.nextFloat() * width
            val phase = random.nextFloat() * 2 * PI.toFloat()
            val y = size.height + heartSize - p * size.height * 0.65f
            val x = x0 + sin(p * 2 * PI.toFloat() * 1.5f + phase) * 18.dp.toPx()
            val alpha =
                when {
                    p < 0.1f -> p / 0.1f
                    p > 0.5f -> 1f - (p - 0.5f) / 0.5f
                    else -> 1f
                } * 0.6f
            val color = if (k % 2 == 0) HeartRed else HeartPink
            // Balloon string
            val string =
                Path().apply {
                    moveTo(x, y + heartSize * 0.4f)
                    cubicTo(
                        x - 6.dp.toPx(),
                        y + heartSize * 0.9f,
                        x + 6.dp.toPx(),
                        y + heartSize * 1.3f,
                        x,
                        y + heartSize * 1.8f,
                    )
                }
            drawPath(string, Color.White, alpha = alpha * 0.6f, style = Stroke(width = 1.dp.toPx()))
            drawHeart(Offset(x, y), heartSize, color, alpha)
        }
    }
}

/**
 * Cupid's arrow flies across the page and sticks into the clock
 */
@Composable
private fun CupidArrow(
    clockBounds: State<Rect?>,
    origin: Offset,
) {
    val time = rememberTicker()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue
        val length = 64.dp.toPx()
        val bounds = clockBounds.value
        val target =
            if (bounds != null) {
                Offset(bounds.left + bounds.width * 0.3f, bounds.center.y) - origin
            } else {
                Offset(size.width - 50.dp.toPx(), 48.dp.toPx())
            }
        val start = Offset(-length, size.height * 0.55f)
        val arc = 90.dp.toPx()
        val flight = 1_200f
        val p = (t / flight).coerceIn(0f, 1f)
        val tip = start + (target - start) * p + Offset(0f, -sin(PI.toFloat() * p) * arc)
        val dx = target.x - start.x
        val dy = target.y - start.y - PI.toFloat() * cos(PI.toFloat() * p) * arc
        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (t > flight) {
            // Stuck: quivers a bit, then fades away
            val since = (t - flight) / 1000f
            angle += sin(since * 40f) * 4f * exp(-since * 3f)
        }
        val alpha = if (t > 4_500) (1f - (t - 4_500) / 1_000f).coerceIn(0f, 1f) else 1f
        drawCupidArrow(tip, length, angle, alpha)
    }
}

/**
 * Bats fluttering around a pale moon in the top right corner, below the clock
 */
@Composable
private fun Bats() {
    val time = rememberTicker()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue / 1000f
        val moon = Offset(size.width - 92.dp.toPx(), 140.dp.toPx())
        drawCircle(Color(0xFFFFCC80), radius = 30.dp.toPx(), center = moon, alpha = 0.22f)
        val batColor = Color(0xFF2E1A47)
        val bats =
            listOf(
                Triple(Offset(-26f, -8f), 30f, 0f),
                Triple(Offset(22f, 6f), 24f, 2.1f),
                Triple(Offset(-2f, 30f), 20f, 4.2f),
            )
        bats.forEachIndexed { i, (offset, span, phase) ->
            val w = 0.7f + i * 0.15f
            val c =
                moon +
                    Offset(offset.x.dp.toPx(), offset.y.dp.toPx()) +
                    Offset(cos(t * w + phase) * 14.dp.toPx(), sin(t * w * 1.3f + phase) * 8.dp.toPx())
            drawBat(c, span.dp.toPx(), sin(t * 9f + phase), batColor)
        }
    }
}

/**
 * A ghost floating across the page
 */
@Composable
private fun Ghost(occurrence: Int) {
    val time = rememberTicker()
    val random = remember(occurrence) { Random(occurrence * 31 + 7) }
    val heightFraction = remember(occurrence) { 0.25f + random.nextFloat() * 0.35f }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue / 9_000f
        val s = 70.dp.toPx()
        val x = -s * 1.4f + (size.width + s * 2.8f) * t
        val y = size.height * heightFraction + sin(t * 2 * PI.toFloat() * 2.5f) * 24.dp.toPx()
        val alpha =
            when {
                t < 0.1f -> t / 0.1f
                t > 0.9f -> (1f - t) / 0.1f
                else -> 1f
            }.coerceIn(0f, 1f) * 0.75f
        rotate(sin(t * 2 * PI.toFloat() * 2.5f) * 6f, pivot = Offset(x + s / 2, y + s / 2)) {
            drawGhost(Offset(x, y), s, alpha)
        }
    }
}

/**
 * The Easter bunny peeks out from the bottom edge in the right corner and wiggles its ears
 */
@Composable
private fun BunnyPeek() {
    val time = rememberTicker()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.longValue
        val s = 66.dp.toPx()
        val shown =
            when {
                t < 900 -> smoothStep(t / 900f)
                t < 5_100 -> 1f
                else -> 1f - smoothStep((t - 5_100) / 900f)
            }
        val x = size.width - 150.dp.toPx()
        val y = size.height - shown * s * 0.85f
        val earTilt = if (t in 1_500L..3_200L) sin(t / 1000f * 14f) * 8f else 0f
        drawBunny(Offset(x, y), s, earTilt)
    }
}
