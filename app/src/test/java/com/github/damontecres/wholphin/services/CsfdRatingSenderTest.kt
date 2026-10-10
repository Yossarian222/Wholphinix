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

    private fun TestScope.sender(
        messages: MutableList<String>,
        results: MutableList<CsfdRatingResult>,
    ): CsfdRatingSender {
        val sender = CsfdRatingSender(mockk<Context>(relaxed = true), tvTips, backgroundScope)
        sender.showMessage = { messages.add(it ?: "OK") }
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
    fun `A failure goes back to the rating before the changes`() =
        runTest {
            coEvery { tvTips.rate(any(), any()) } returns "Príliš rýchlo za sebou"
            val messages = mutableListOf<String>()
            val results = mutableListOf<CsfdRatingResult>()
            val sender = sender(messages, results)

            sender.rate(7, 1, previous = 5)
            sender.rate(7, 0, previous = 1)
            // TestScope.advanceUntilIdle() ignores backgroundScope, where the sender runs
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()

            assertEquals(listOf(CsfdRatingResult(7, 0, 5, "Príliš rýchlo za sebou")), results)
            assertEquals(listOf("Príliš rýchlo za sebou"), messages)
            assertTrue(sender.sending.value.isEmpty())
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
