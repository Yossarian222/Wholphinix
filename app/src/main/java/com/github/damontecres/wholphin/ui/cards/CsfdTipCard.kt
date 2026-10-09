package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.csfdColor

/** Same as the default card shape, so the frame follows the rounded corners */
private val CardShape = RoundedCornerShape(8.dp)

/**
 * Card border: a ČSFD red frame for items that are not in the Jellyfin library (eg ČSFD TV tips found in Seerr), the
 * default one otherwise. The focused border stays the default one, so the focus is still visible.
 */
@Composable
fun libraryCardBorder(item: BaseItem?): CardBorder =
    if (item?.inLibrary == false) {
        CardDefaults.border(
            border =
                Border(
                    border = BorderStroke(width = 3.dp, color = csfdColor(100)),
                    shape = CardShape,
                ),
        )
    } else {
        CardDefaults.border()
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
