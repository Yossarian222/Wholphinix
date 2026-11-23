package com.github.damontecres.wholphin.services

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CsfdRatingSenderTest {
    private val tvTips = mockk<CsfdTvTipsService>()

    private val store =
        object : CsfdRatingQueueStore {
            var saved: Map<Int, QueuedCsfdRating> = emptyMap()

            override fun load() = saved

            override fun save(queue: Map<Int, QueuedCsfdRating>) {
                saved = queue
            }
        }

    private fun TestScope.sender(
        messages: MutableList<String>,
        results: MutableList<CsfdRatingResult>,
    ): CsfdRatingSender {
        val sender = CsfdRatingSender(mockk<Context>(relaxed = true), tvTips, backgroundScope)
        sender.showMessage = { messages.add(it ?: "OK") }
        sender.queuedText = { "later: $it" }
        sender.gaveUpText = { "gave up: $it" }
        sender.store = store
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sender.results.toList(results) }
        return sender
    }

    @Test
    fun `Only the last of quick changes is sent`() =
        runTest {
            coEvery { tvTips.rate(any(), any()) } returns null
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(42, 2, previous = null)
            testScheduler.advanceTimeBy(200)
            sender.rate(42, 3, previous = 2)
            testScheduler.advanceTimeBy(200)
            sender.rate(42, 4, previous = 3)
            assertTrue(42 in sender.sending.value)
            assertEquals(4, sender.pendingStars(42))
            // TestScope.advanceUntilIdle() ignores backgroundScope, where the sender runs
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()

            coVerify(exactly = 1) { tvTips.rate(any(), any()) }
            coVerify { tvTips.rate(42, 4) }
            assertEquals(listOf(CsfdRatingResult(42, 4, null, null)), results)
            assertEquals(1, messages.size)
            assertTrue(sender.sending.value.isEmpty())
            assertEquals(null, sender.pendingStars(42))
        }

    @Test
    fun `A failure is queued and keeps the new stars`() =
        runTest {
            coEvery { tvTips.rate(any(), any()) } returns "Prihlásenie na ČSFD nedávno zlyhalo"
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(7, 1, previous = 5)
            sender.rate(7, 0, previous = 1)
            // TestScope.advanceUntilIdle() ignores backgroundScope, where the sender runs
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()

            val message = "later: Prihlásenie na ČSFD nedávno zlyhalo"
            assertEquals(listOf(CsfdRatingResult(7, 0, 5, message, queued = true)), results)
            assertEquals(listOf(message), messages)
            assertTrue(sender.sending.value.isEmpty())
            // Survives a restart and is still shown on the detail page
            assertEquals(mapOf(7 to QueuedCsfdRating(0, 1)), store.saved)
            assertEquals(0, sender.pendingStars(7))
        }

    @Test
    fun `A queued rating is sent again after the login pause`() =
        runTest {
            var fail = true
            coEvery { tvTips.rate(any(), any()) } answers { if (fail) "zlyhalo" else null }
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(7, 4, previous = null)
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()
            fail = false
            testScheduler.advanceTimeBy(CsfdRatingSender.RETRY_DELAY.inWholeMilliseconds + 10_000)
            testScheduler.runCurrent()

            coVerify(exactly = 2) { tvTips.rate(7, 4) }
            assertEquals(CsfdRatingResult(7, 4, null, null), results.last())
            assertEquals("OK", messages.last())
            assertEquals(emptyMap<Int, QueuedCsfdRating>(), store.saved)
            assertEquals(null, sender.pendingStars(7))
        }

    @Test
    fun `A rating is dropped after the last attempt`() =
        runTest {
            coEvery { tvTips.rate(any(), any()) } returns "Nesprávne heslo"
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(7, 4, previous = 2)
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()
            repeat(CsfdRatingSender.MAX_ATTEMPTS) {
                testScheduler.advanceTimeBy(CsfdRatingSender.RETRY_DELAY.inWholeMilliseconds + 10_000)
                testScheduler.runCurrent()
            }

            coVerify(exactly = CsfdRatingSender.MAX_ATTEMPTS) { tvTips.rate(7, 4) }
            assertEquals("gave up: Nesprávne heslo", messages.last())
            assertEquals(false, results.last().queued)
            assertEquals(emptyMap<Int, QueuedCsfdRating>(), store.saved)
            assertEquals(null, sender.pendingStars(7))
        }

    @Test
    fun `Ratings queued in the last run are sent after the start`() =
        runTest {
            store.saved = mapOf(9 to QueuedCsfdRating(3, 1))
            coEvery { tvTips.rate(any(), any()) } returns null
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            sender(messages, results)

            testScheduler.advanceTimeBy(CsfdRatingSender.STARTUP_DELAY.inWholeMilliseconds + 1_000)
            testScheduler.runCurrent()

            coVerify(exactly = 1) { tvTips.rate(9, 3) }
            assertEquals(emptyMap<Int, QueuedCsfdRating>(), store.saved)
        }

    @Test
    fun `Requests are spaced for the plugin throttle`() =
        runTest {
            val sentAt = mutableListOf<Long>()
            coEvery { tvTips.rate(any(), any()) } coAnswers {
                sentAt.add(testScheduler.currentTime)
                null
            }
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(1, 3, previous = null)
            testScheduler.advanceTimeBy(700)
            sender.rate(2, 4, previous = null)
            // TestScope.advanceUntilIdle() ignores backgroundScope, where the sender runs
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()

            assertEquals(2, sentAt.size)
            assertTrue("Sent ${sentAt[1] - sentAt[0]} ms apart", sentAt[1] - sentAt[0] >= 1000)
            assertEquals(listOf(1, 2), results.map { it.csfdId })
            assertEquals(2, messages.size)
        }
}
