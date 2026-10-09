package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
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
