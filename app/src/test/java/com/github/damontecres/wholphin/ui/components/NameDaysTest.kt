package com.github.damontecres.wholphin.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NameDaysTest {
    @Test
    fun tableHasAllDays() {
        assertEquals(366, NameDays.size)
        // Only the few holidays of a leap year are without a name
        val withoutName =
            (0 until 366)
                .map { LocalDate.of(2024, 1, 1).plusDays(it.toLong()) }
                .filter { NameDays.forDate(it) == null }
        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2024, 11, 2),
                LocalDate.of(2024, 12, 25),
            ),
            withoutName,
        )
    }

    @Test
    fun spotChecks() {
        assertEquals("Koloman", NameDays.forDay(10, 13))
        assertEquals("Adam, Eva", NameDays.forDay(12, 24))
        assertTrue(NameDays.forDay(1, 2)!!.contains("Alexandra"))
        assertNotNull(NameDays.forDay(2, 29))
        assertEquals("Radomír", NameDays.forDay(2, 29))
        assertEquals("Štefan", NameDays.forDay(12, 26))
        assertEquals("Jozef", NameDays.forDay(3, 19))
        assertEquals("Silvester", NameDays.forDay(12, 31))
        assertNull(NameDays.forDay(1, 1))
        assertNull(NameDays.forDay(12, 25))
        assertNull(NameDays.forDay(13, 1))
        assertNull(NameDays.forDay(2, 30))
    }

    @Test
    fun headerDateWithNameDay() {
        assertEquals(
            "pondelok 13. 10. 2026 · Koloman",
            withNameDay("pondelok 13. 10. 2026", LocalDate.of(2026, 10, 13)),
        )
        assertEquals(
            "štvrtok 1. 1. 2026",
            withNameDay("štvrtok 1. 1. 2026", LocalDate.of(2026, 1, 1)),
        )
    }
}
