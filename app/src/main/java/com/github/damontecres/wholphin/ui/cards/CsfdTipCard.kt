package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.csfdColor

/**
 * Small "not available" badge (a red circle with a diagonal slash) for posters of items that are not in the Jellyfin
 * library (eg ČSFD TV tips found in Seerr). Meant for the bottom end corner, so it does not cover the watched mark or
 * the ČSFD rating at the top. The dark outline and shadow keep it readable on both light and dark posters.
 */
@Composable
fun NotInLibraryBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.not_in_library)
    Canvas(
        modifier =
            modifier
                .size(24.dp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f
        // Dark outline, then the red disc
        drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = radius)
        drawCircle(color = csfdColor(100), radius = radius - 1.dp.toPx())
        // White prohibited sign: a ring with a diagonal slash
        val stroke = 2.dp.toPx()
        val signRadius = radius * 0.55f
        drawCircle(color = Color.White, radius = signRadius, style = Stroke(width = stroke))
        val offset = signRadius * 0.7071f
        drawLine(
            color = Color.White,
            start = Offset(center.x - offset, center.y - offset),
            end = Offset(center.x + offset, center.y + offset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private val PlaceholderGradient =
    Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A), Color(0xFF0A0A0A)))

/**
 * Poster placeholder for items that are not in the library and have no poster (eg older ČSFD TV tips Seerr does not
 * know): the title and year in large text on a dark gradient instead of the generic icon
 */
@Composable
fun NotInLibraryPlaceholder(
    title: String?,
    year: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .background(PlaceholderGradient)
                .padding(10.dp),
    ) {
        Text(
            text = title.orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (year != null) {
            Text(
                text = year.toString(),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
