package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeCompanionServiceTest {
    @Test
    fun `short quip stays at least 10 s`() {
        assertEquals(10_000L, ClaudeCompanionService.displayMs(""))
        assertEquals(10_000L, ClaudeCompanionService.displayMs("Krátka hláška 🍿"))
    }

    @Test
    fun `100 characters stay about 13 s`() {
        assertEquals(13_000L, ClaudeCompanionService.displayMs("x".repeat(100)))
    }

    @Test
    fun `long text reaches the 20 s cap from about 180 characters`() {
        assertEquals(19_840L, ClaudeCompanionService.displayMs("x".repeat(176)))
        assertEquals(20_000L, ClaudeCompanionService.displayMs("x".repeat(180)))
        assertEquals(20_000L, ClaudeCompanionService.displayMs("x".repeat(200)))
        assertEquals(20_000L, ClaudeCompanionService.displayMs("x".repeat(300)))
    }

    @Test
    fun `each extra line adds 1500 ms within the cap`() {
        val oneLine = "x".repeat(100)
        val twoLines = "x".repeat(49) + "\n" + "x".repeat(50)
        assertEquals(13_000L, ClaudeCompanionService.displayMs(oneLine))
        assertEquals(14_500L, ClaudeCompanionService.displayMs(twoLines))
        val manyLines = List(10) { "x".repeat(15) }.joinToString("\n")
        assertEquals(20_000L, ClaudeCompanionService.displayMs(manyLines))
    }

    @Test
    fun `longer text never stays shorter`() {
        var previous = 0L
        for (length in 0..300) {
            val ms = ClaudeCompanionService.displayMs("x".repeat(length))
            assertTrue(ms >= previous)
            assertTrue(ms in 10_000L..20_000L)
            previous = ms
        }
    }

    @Test
    fun `companion is called Jean on the TV`() {
        assertEquals("Jean", ClaudeCompanionService.COMPANION_NAME)
        assertTrue(ClaudeCompanionService.SYSTEM_PROMPT.startsWith("Si Jean"))
    }
}
