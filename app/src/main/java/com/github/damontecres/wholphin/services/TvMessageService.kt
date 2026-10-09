package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A message bubble shown over everything (also over the player), e.g. a Jellyfin "DisplayMessage" sent by the
 * Claude MCP server, or a quip of the Claude companion.
 */
data class TvMessage(
    val id: Long,
    val title: String?,
    val text: String,
    val duration: Duration,
)

/**
 * Queue of [TvMessage]s: they are shown one after another, each for its own duration.
 */
@Singleton
class TvMessageService
    @Inject
    constructor(
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        private val _current = MutableStateFlow<TvMessage?>(null)

        /** The message on screen, or null */
        val current: StateFlow<TvMessage?> = _current

        private val queue = Channel<TvMessage>(capacity = MAX_QUEUED)
        private val ids = AtomicLong()

        init {
            scope.launch {
                for (message in queue) {
                    _current.value = message
                    delay(message.duration)
                    _current.value = null
                    // Let the exit animation finish before the next one comes in
                    delay(GAP)
                }
            }
        }

        /**
         * Queue a message. [timeoutMs] defaults to [DEFAULT_DURATION] and is capped at [MAX_DURATION].
         */
        fun show(
            title: String?,
            text: String,
            timeoutMs: Long? = null,
        ) {
            val body = text.trim().take(MAX_TEXT_LENGTH)
            if (body.isEmpty()) return
            val duration =
                (timeoutMs?.takeIf { it > 0 }?.milliseconds ?: DEFAULT_DURATION)
                    .coerceIn(MIN_DURATION, MAX_DURATION)
            val message =
                TvMessage(
                    id = ids.incrementAndGet(),
                    title = title?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE_LENGTH),
                    text = body,
                    duration = duration,
                )
            if (queue.trySend(message).isFailure) {
                Timber.w("Too many TV messages queued, dropping one")
            }
        }

        companion object {
            val DEFAULT_DURATION = 8.seconds
            val MIN_DURATION = 2.seconds
            val MAX_DURATION = 30.seconds
            private val GAP = 400.milliseconds
            private const val MAX_QUEUED = 10
            private const val MAX_TEXT_LENGTH = 400
            private const val MAX_TITLE_LENGTH = 60
        }
    }
