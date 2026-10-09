package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.TvMessage
import com.github.damontecres.wholphin.services.TvMessageService

/** Claude's terracotta, for the badge */
private val ClaudeAccent = Color(0xFFD97757)

/**
 * Shows the current [TvMessage] as a card in the top right corner, over everything else.
 *
 * Nothing in here is focusable, so it never takes the focus from the player or the page below it.
 */
@Composable
fun TvMessageOverlay(
    service: TvMessageService,
    modifier: Modifier = Modifier,
) {
    val current by service.current.collectAsState()
    // Keep the last message while the exit animation runs
    val last = remember { mutableStateOf<TvMessage?>(null) }
    if (current != null) {
        SideEffect { last.value = current }
    }
    val shown = current ?: last.value
    Box(modifier = modifier, contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 },
            modifier = Modifier.padding(top = 32.dp, end = 40.dp),
        ) {
            shown?.let { TvMessageCard(it) }
        }
    }
}

@Composable
fun TvMessageCard(
    message: TvMessage,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
        modifier =
            modifier
                .widthIn(min = 240.dp, max = 460.dp)
                .shadow(12.dp, shape)
                .background(Color(0xF01E1E22), shape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(34.dp)
                    .background(ClaudeAccent, CircleShape),
        ) {
            Text(
                text = "✳",
                color = Color.White,
                fontSize = 18.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            message.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ClaudeAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
