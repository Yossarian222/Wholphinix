package com.github.damontecres.wholphin.ui.audiobookshelf

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.audiobookshelf.AbsChapter
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConnection
import com.github.damontecres.wholphin.services.audiobookshelf.AudiobookshelfHttpException

/** User-friendly error text: a rejected token gets an explanation instead of the raw HTTP message */
internal fun Context.absErrorMessage(
    ex: Exception,
    fallback: String,
): String =
    if (ex is AudiobookshelfHttpException && ex.code == 401) {
        getString(R.string.abs_token_invalid)
    } else {
        ex.message ?: fallback
    }

/** Cover image URL; the token is a query parameter so the image loader needs no extra headers */
internal fun absCoverUrl(
    conn: AbsConnection,
    itemId: String,
): String = "${conn.baseUrl.trimEnd('/')}/api/items/$itemId/cover?token=${conn.token}"

/** Author photo URL, see [absCoverUrl] */
internal fun absAuthorImageUrl(
    conn: AbsConnection,
    authorId: String,
): String = "${conn.baseUrl.trimEnd('/')}/api/authors/$authorId/image?token=${conn.token}"

/** Plain text of a podcast/book description, which is often HTML */
internal fun htmlToText(html: String?): String? =
    html
        ?.takeIf { it.isNotBlank() }
        ?.let {
            HtmlCompat
                .fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }?.takeIf { it.isNotBlank() }

/** "1:02:03" or "2:03" */
internal fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** "3 h 25 min" or "25 min", for runtimes */
internal fun formatRuntime(seconds: Double): String {
    val totalMinutes = (seconds / 60).toLong().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

/** Index of the chapter playing at [positionMs], or -1 */
internal fun chapterIndexAt(
    chapters: List<AbsChapter>,
    positionMs: Long,
): Int {
    val seconds = positionMs / 1000.0
    return chapters.indexOfLast { it.start <= seconds }
}

/** Thin progress bar in the theme colors */
@Composable
internal fun AbsProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .25f), RoundedCornerShape(height / 2)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height)
                    .background(MaterialTheme.colorScheme.border, RoundedCornerShape(height / 2)),
        )
    }
}
