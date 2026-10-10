package com.github.damontecres.wholphin.ui.seasonal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin

// Simple vector drawings of the seasonal decorations, all drawn with basic shapes (no image assets).
// Each one is drawn into a square of side `s` whose top left corner is `o`, unless stated otherwise.

internal val HeartRed = Color(0xFFE53961)
internal val HeartPink = Color(0xFFFF7EA8)

/**
 * A heart centered at [c], [w] wide
 */
internal fun DrawScope.drawHeart(
    c: Offset,
    w: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val h = w * 0.9f
    val path =
        Path().apply {
            moveTo(c.x, c.y + h * 0.45f)
            cubicTo(c.x - w * 0.6f, c.y + h * 0.02f, c.x - w * 0.5f, c.y - h * 0.6f, c.x, c.y - h * 0.22f)
            cubicTo(c.x + w * 0.5f, c.y - h * 0.6f, c.x + w * 0.6f, c.y + h * 0.02f, c.x, c.y + h * 0.45f)
            close()
        }
    drawPath(path = path, color = color, alpha = alpha)
}

/**
 * Jack-o'-lantern
 */
internal fun DrawScope.drawPumpkin(
    o: Offset,
    s: Float,
) {
    val cx = o.x + s / 2
    val cy = o.y + s * 0.56f
    val bodyH = s * 0.7f
    drawOval(Color(0xFFD35400), topLeft = Offset(cx - s * 0.46f, cy - bodyH / 2), size = Size(s * 0.5f, bodyH))
    drawOval(Color(0xFFD35400), topLeft = Offset(cx - s * 0.04f, cy - bodyH / 2), size = Size(s * 0.5f, bodyH))
    drawOval(Color(0xFFF39C12), topLeft = Offset(cx - s * 0.25f, cy - bodyH / 2), size = Size(s * 0.5f, bodyH))
    drawRoundRect(
        color = Color(0xFF3E7B27),
        topLeft = Offset(cx - s * 0.05f, o.y + s * 0.06f),
        size = Size(s * 0.1f, s * 0.18f),
        cornerRadius = CornerRadius(s * 0.03f, s * 0.03f),
    )
    val face = Color(0xFF3B1600)
    val eyes =
        Path().apply {
            moveTo(cx - s * 0.22f, cy - s * 0.02f)
            lineTo(cx - s * 0.08f, cy - s * 0.02f)
            lineTo(cx - s * 0.15f, cy - s * 0.14f)
            close()
            moveTo(cx + s * 0.08f, cy - s * 0.02f)
            lineTo(cx + s * 0.22f, cy - s * 0.02f)
            lineTo(cx + s * 0.15f, cy - s * 0.14f)
            close()
            // Zig-zag grin
            moveTo(cx - s * 0.22f, cy + s * 0.07f)
            lineTo(cx - s * 0.11f, cy + s * 0.12f)
            lineTo(cx, cy + s * 0.07f)
            lineTo(cx + s * 0.11f, cy + s * 0.12f)
            lineTo(cx + s * 0.22f, cy + s * 0.07f)
            lineTo(cx, cy + s * 0.22f)
            close()
        }
    drawPath(eyes, face)
}

/**
 * Envelope with a heart (Valentine's search button)
 */
internal fun DrawScope.drawLoveLetter(
    o: Offset,
    s: Float,
) {
    val left = o.x + s * 0.06f
    val top = o.y + s * 0.2f
    val w = s * 0.88f
    val h = s * 0.6f
    drawRoundRect(
        color = Color(0xFFFFF1F4),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(s * 0.06f, s * 0.06f),
    )
    val flap =
        Path().apply {
            moveTo(left, top + s * 0.02f)
            lineTo(left + w / 2, top + h * 0.58f)
            lineTo(left + w, top + s * 0.02f)
        }
    drawPath(flap, Color(0xFFC77A8E), style = Stroke(width = s * 0.06f, cap = StrokeCap.Round))
    drawHeart(Offset(left + w / 2, top + h * 0.6f), s * 0.34f, HeartRed)
}

/**
 * Painted Easter egg
 */
internal fun DrawScope.drawEasterEgg(
    o: Offset,
    s: Float,
) {
    val rect = Rect(o.x + s * 0.2f, o.y + s * 0.06f, o.x + s * 0.8f, o.y + s * 0.94f)
    val shape = Path().apply { addOval(rect) }
    clipPath(shape) {
        drawRect(Color(0xFF64B5F6), topLeft = rect.topLeft, size = rect.size)
        drawRect(
            Color(0xFFFFEB3B),
            topLeft = Offset(rect.left, rect.top + rect.height * 0.3f),
            size = Size(rect.width, rect.height * 0.14f),
        )
        drawRect(
            Color(0xFFF06292),
            topLeft = Offset(rect.left, rect.top + rect.height * 0.6f),
            size = Size(rect.width, rect.height * 0.14f),
        )
        val dot = s * 0.045f
        for (i in 0..2) {
            drawCircle(
                Color.White,
                radius = dot,
                center =
                    Offset(
                        rect.left + rect.width * (0.25f + i * 0.25f),
                        rect.top + rect.height * 0.5f,
                    ),
            )
        }
    }
}

/**
 * Small Christmas tree, its baubles blink with [phase] (radians)
 */
internal fun DrawScope.drawChristmasTree(
    o: Offset,
    s: Float,
    phase: Float = 0f,
) {
    val cx = o.x + s / 2
    drawRect(Color(0xFF6D4C41), topLeft = Offset(cx - s * 0.06f, o.y + s * 0.82f), size = Size(s * 0.12f, s * 0.16f))
    val green = Color(0xFF2E7D32)
    val tiers = listOf(Triple(0.1f, 0.42f, 0.26f), Triple(0.28f, 0.64f, 0.34f), Triple(0.46f, 0.86f, 0.42f))
    tiers.forEach { (top, bottom, half) ->
        val path =
            Path().apply {
                moveTo(cx, o.y + s * top)
                lineTo(cx + s * half, o.y + s * bottom)
                lineTo(cx - s * half, o.y + s * bottom)
                close()
            }
        drawPath(path, green)
    }
    drawStar(Offset(cx, o.y + s * 0.1f), s * 0.11f, Color(0xFFFFD54F))
    val baubles =
        listOf(
            Offset(-0.1f, 0.36f) to Color(0xFFE53935),
            Offset(0.12f, 0.55f) to Color(0xFFFFD54F),
            Offset(-0.16f, 0.72f) to Color(0xFF42A5F5),
            Offset(0.2f, 0.8f) to Color(0xFFE53935),
            Offset(0.02f, 0.68f) to Color(0xFFFFFFFF),
        )
    baubles.forEachIndexed { i, (pos, color) ->
        val alpha = 0.55f + 0.45f * sin(phase + i * 1.7f)
        drawCircle(color, radius = s * 0.045f, center = Offset(cx + s * pos.x, o.y + s * pos.y), alpha = alpha.coerceIn(0f, 1f))
    }
}

/**
 * Five pointed star centered at [c] with outer radius [r]
 */
internal fun DrawScope.drawStar(
    c: Offset,
    r: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val angle = Math.toRadians(-90.0 + i * 36.0)
        val x = c.x + radius * cos(angle).toFloat()
        val y = c.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, alpha = alpha)
}

/**
 * St. Nicholas boot
 */
internal fun DrawScope.drawBoot(
    o: Offset,
    s: Float,
) {
    val red = Color(0xFFC62828)
    // Shaft
    drawRoundRect(
        color = red,
        topLeft = Offset(o.x + s * 0.18f, o.y + s * 0.2f),
        size = Size(s * 0.38f, s * 0.66f),
        cornerRadius = CornerRadius(s * 0.05f, s * 0.05f),
    )
    // Foot
    drawRoundRect(
        color = red,
        topLeft = Offset(o.x + s * 0.18f, o.y + s * 0.62f),
        size = Size(s * 0.7f, s * 0.26f),
        cornerRadius = CornerRadius(s * 0.13f, s * 0.13f),
    )
    // Sole
    drawRoundRect(
        color = Color(0xFF3E2723),
        topLeft = Offset(o.x + s * 0.18f, o.y + s * 0.84f),
        size = Size(s * 0.7f, s * 0.08f),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
    )
    // Fur cuff
    drawRoundRect(
        color = Color(0xFFF5F5F5),
        topLeft = Offset(o.x + s * 0.12f, o.y + s * 0.1f),
        size = Size(s * 0.5f, s * 0.16f),
        cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
    )
    // A candy cane peeking out
    drawLine(
        Color(0xFFFFFFFF),
        start = Offset(o.x + s * 0.48f, o.y + s * 0.12f),
        end = Offset(o.x + s * 0.48f, o.y + s * 0.0f),
        strokeWidth = s * 0.06f,
        cap = StrokeCap.Round,
    )
    drawCircle(Color(0xFFE53935), radius = s * 0.035f, center = Offset(o.x + s * 0.48f, o.y + s * 0.04f))
}

/**
 * Red rose
 */
internal fun DrawScope.drawRose(
    o: Offset,
    s: Float,
) {
    val cx = o.x + s / 2
    val green = Color(0xFF388E3C)
    drawLine(
        green,
        start = Offset(cx, o.y + s * 0.4f),
        end = Offset(cx - s * 0.04f, o.y + s * 0.98f),
        strokeWidth = s * 0.07f,
        cap = StrokeCap.Round,
    )
    rotate(-35f, pivot = Offset(cx + s * 0.12f, o.y + s * 0.7f)) {
        drawOval(green, topLeft = Offset(cx + s * 0.0f, o.y + s * 0.64f), size = Size(s * 0.26f, s * 0.12f))
    }
    drawCircle(Color(0xFFB0123B), radius = s * 0.24f, center = Offset(cx, o.y + s * 0.3f))
    drawCircle(Color(0xFFD81B4A), radius = s * 0.16f, center = Offset(cx + s * 0.03f, o.y + s * 0.27f))
    drawArc(
        color = Color(0xFF7A0A26),
        startAngle = 200f,
        sweepAngle = 250f,
        useCenter = false,
        topLeft = Offset(cx - s * 0.08f, o.y + s * 0.2f),
        size = Size(s * 0.16f, s * 0.16f),
        style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
    )
}

/**
 * A snowflake centered at [c] with radius [r]
 */
internal fun DrawScope.drawSnowflake(
    c: Offset,
    r: Float,
    alpha: Float,
) {
    if (r < 4f) {
        drawCircle(Color.White, radius = r, center = c, alpha = alpha)
        return
    }
    val stroke = (r / 4f).coerceAtLeast(1.5f)
    for (i in 0 until 3) {
        val angle = Math.toRadians(i * 60.0)
        val dx = (r * cos(angle)).toFloat()
        val dy = (r * sin(angle)).toFloat()
        drawLine(
            Color.White,
            start = Offset(c.x - dx, c.y - dy),
            end = Offset(c.x + dx, c.y + dy),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}

/**
 * A ghost [s] wide, its top left corner at [o]
 */
internal fun DrawScope.drawGhost(
    o: Offset,
    s: Float,
    alpha: Float,
) {
    val path =
        Path().apply {
            arcTo(Rect(o.x, o.y, o.x + s, o.y + s * 0.9f), 180f, 180f, true)
            lineTo(o.x + s, o.y + s * 1.1f)
            // Wavy hem
            val bump = s / 3
            for (i in 0 until 3) {
                val x1 = o.x + s - i * bump
                val x2 = x1 - bump
                cubicTo(x1 - bump * 0.3f, o.y + s * 1.28f, x2 + bump * 0.3f, o.y + s * 1.28f, x2, o.y + s * 1.1f)
            }
            close()
        }
    drawPath(path, Color(0xFFF4F1FA), alpha = alpha)
    val dark = Color(0xFF241A33)
    drawOval(dark, topLeft = Offset(o.x + s * 0.27f, o.y + s * 0.34f), size = Size(s * 0.12f, s * 0.18f), alpha = alpha)
    drawOval(dark, topLeft = Offset(o.x + s * 0.58f, o.y + s * 0.34f), size = Size(s * 0.12f, s * 0.18f), alpha = alpha)
    drawOval(dark, topLeft = Offset(o.x + s * 0.43f, o.y + s * 0.6f), size = Size(s * 0.12f, s * 0.15f), alpha = alpha)
}

/**
 * A bat centered at [c] with wingspan [w], [flap] from -1 (wings down) to 1 (wings up)
 */
internal fun DrawScope.drawBat(
    c: Offset,
    w: Float,
    flap: Float,
    color: Color,
) {
    val tipY = c.y - flap * w * 0.22f
    val wing =
        Path().apply {
            moveTo(c.x, c.y - w * 0.04f)
            lineTo(c.x + w * 0.5f, tipY)
            // Scalloped lower edge back to the body
            val step = w * 0.5f / 3
            for (i in 0 until 3) {
                val x1 = c.x + w * 0.5f - i * step
                val x2 = x1 - step
                val y2 = c.y + w * 0.06f
                cubicTo(x1 - step * 0.2f, y2 - w * 0.02f, x2 + step * 0.2f, y2 - w * 0.08f, x2, y2)
            }
            close()
        }
    drawPath(wing, color)
    scale(-1f, 1f, pivot = c) {
        drawPath(wing, color)
    }
    drawOval(color, topLeft = Offset(c.x - w * 0.07f, c.y - w * 0.09f), size = Size(w * 0.14f, w * 0.2f))
    val ears =
        Path().apply {
            moveTo(c.x - w * 0.06f, c.y - w * 0.06f)
            lineTo(c.x - w * 0.05f, c.y - w * 0.15f)
            lineTo(c.x - w * 0.01f, c.y - w * 0.07f)
            close()
            moveTo(c.x + w * 0.06f, c.y - w * 0.06f)
            lineTo(c.x + w * 0.05f, c.y - w * 0.15f)
            lineTo(c.x + w * 0.01f, c.y - w * 0.07f)
            close()
        }
    drawPath(ears, color)
}

/**
 * St. Nicholas's head [s] wide, its top left corner at [o]; [wink] closes his right eye
 */
internal fun DrawScope.drawNicholasHead(
    o: Offset,
    s: Float,
    wink: Boolean,
) {
    val cx = o.x + s / 2
    val white = Color(0xFFF7F7F7)
    // Mitre-like red hat
    val hat =
        Path().apply {
            moveTo(cx - s * 0.36f, o.y + s * 0.36f)
            cubicTo(cx - s * 0.3f, o.y + s * 0.05f, cx + s * 0.1f, o.y - s * 0.02f, cx + s * 0.38f, o.y + s * 0.1f)
            lineTo(cx + s * 0.36f, o.y + s * 0.36f)
            close()
        }
    drawPath(hat, Color(0xFFD32F2F))
    drawCircle(white, radius = s * 0.07f, center = Offset(cx + s * 0.38f, o.y + s * 0.1f))
    // Face
    drawCircle(Color(0xFFFFD3B5), radius = s * 0.3f, center = Offset(cx, o.y + s * 0.6f))
    // Beard
    drawArc(
        color = white,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - s * 0.38f, o.y + s * 0.38f),
        size = Size(s * 0.76f, s * 0.62f),
    )
    // Hat brim
    drawRoundRect(
        color = white,
        topLeft = Offset(cx - s * 0.4f, o.y + s * 0.3f),
        size = Size(s * 0.8f, s * 0.13f),
        cornerRadius = CornerRadius(s * 0.065f, s * 0.065f),
    )
    // Eyes
    val eyeColor = Color(0xFF2B1B12)
    drawCircle(eyeColor, radius = s * 0.035f, center = Offset(cx - s * 0.11f, o.y + s * 0.55f))
    if (wink) {
        drawLine(
            eyeColor,
            start = Offset(cx + s * 0.06f, o.y + s * 0.56f),
            end = Offset(cx + s * 0.16f, o.y + s * 0.55f),
            strokeWidth = s * 0.03f,
            cap = StrokeCap.Round,
        )
    } else {
        drawCircle(eyeColor, radius = s * 0.035f, center = Offset(cx + s * 0.11f, o.y + s * 0.55f))
    }
    // Cheeks, nose & moustache
    drawCircle(Color(0xFFF48FB1), radius = s * 0.05f, center = Offset(cx - s * 0.19f, o.y + s * 0.65f), alpha = 0.6f)
    drawCircle(Color(0xFFF48FB1), radius = s * 0.05f, center = Offset(cx + s * 0.19f, o.y + s * 0.65f), alpha = 0.6f)
    drawCircle(Color(0xFFE59A80), radius = s * 0.05f, center = Offset(cx, o.y + s * 0.64f))
    drawOval(white, topLeft = Offset(cx - s * 0.2f, o.y + s * 0.68f), size = Size(s * 0.2f, s * 0.09f))
    drawOval(white, topLeft = Offset(cx, o.y + s * 0.68f), size = Size(s * 0.2f, s * 0.09f))
}

/**
 * Easter bunny's head [s] wide, its top left corner at [o]; [earTilt] in degrees wiggles the ears
 */
internal fun DrawScope.drawBunny(
    o: Offset,
    s: Float,
    earTilt: Float,
) {
    val fur = Color(0xFFECE7E2)
    val pink = Color(0xFFF8BBD0)
    val cx = o.x + s / 2
    val earTop = o.y
    listOf(-1f, 1f).forEach { side ->
        val earCenterX = cx + side * s * 0.14f
        rotate(side * (8f + earTilt), pivot = Offset(earCenterX, earTop + s * 0.5f)) {
            drawOval(fur, topLeft = Offset(earCenterX - s * 0.09f, earTop), size = Size(s * 0.18f, s * 0.52f))
            drawOval(pink, topLeft = Offset(earCenterX - s * 0.045f, earTop + s * 0.06f), size = Size(s * 0.09f, s * 0.38f))
        }
    }
    drawOval(fur, topLeft = Offset(cx - s * 0.32f, o.y + s * 0.4f), size = Size(s * 0.64f, s * 0.56f))
    val eye = Color(0xFF3E2723)
    drawCircle(eye, radius = s * 0.04f, center = Offset(cx - s * 0.12f, o.y + s * 0.6f))
    drawCircle(eye, radius = s * 0.04f, center = Offset(cx + s * 0.12f, o.y + s * 0.6f))
    drawCircle(Color(0xFFF06292), radius = s * 0.04f, center = Offset(cx, o.y + s * 0.71f))
    drawCircle(pink, radius = s * 0.06f, center = Offset(cx - s * 0.2f, o.y + s * 0.74f), alpha = 0.6f)
    drawCircle(pink, radius = s * 0.06f, center = Offset(cx + s * 0.2f, o.y + s * 0.74f), alpha = 0.6f)
}

/**
 * Cupid's arrow with its tip at [tip], flying in the direction of [angleDegrees], [length] long
 */
internal fun DrawScope.drawCupidArrow(
    tip: Offset,
    length: Float,
    angleDegrees: Float,
    alpha: Float,
) {
    rotate(angleDegrees, pivot = tip) {
        val tail = Offset(tip.x - length, tip.y)
        drawLine(
            Color(0xFFB08850),
            start = tail,
            end = Offset(tip.x - length * 0.08f, tip.y),
            strokeWidth = length * 0.035f,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
        // Heart shaped arrowhead, its point turned to the front
        val headCenter = Offset(tip.x - length * 0.07f, tip.y)
        rotate(-90f, pivot = headCenter) {
            drawHeart(headCenter, length * 0.16f, HeartRed, alpha)
        }
        // Fletching
        val feather = HeartPink
        for (i in 0..1) {
            val x = tail.x + length * (0.04f + i * 0.07f)
            drawLine(
                feather,
                start = Offset(x, tip.y),
                end = Offset(x - length * 0.06f, tip.y - length * 0.07f),
                strokeWidth = length * 0.03f,
                cap = StrokeCap.Round,
                alpha = alpha,
            )
            drawLine(
                feather,
                start = Offset(x, tip.y),
                end = Offset(x - length * 0.06f, tip.y + length * 0.07f),
                strokeWidth = length * 0.03f,
                cap = StrokeCap.Round,
                alpha = alpha,
            )
        }
    }
}
