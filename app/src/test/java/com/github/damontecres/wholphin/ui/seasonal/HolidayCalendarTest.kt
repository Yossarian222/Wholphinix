package com.github.damontecres.wholphin.ui.seasonal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HolidayCalendarTest {
    @Test
    fun easterSundays() {
        assertEquals(LocalDate.of(2024, 3, 31), HolidayCalendar.easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), HolidayCalendar.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), HolidayCalendar.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), HolidayCalendar.easterSunday(2027))
        assertEquals(LocalDate.of(2028, 4, 16), HolidayCalendar.easterSunday(2028))
        // Earliest and latest possible dates
        assertEquals(LocalDate.of(2285, 3, 22), HolidayCalendar.easterSunday(2285))
        assertEquals(LocalDate.of(2038, 4, 25), HolidayCalendar.easterSunday(2038))
    }

    @Test
    fun easterBoundaries() {
        // Easter Sunday 2026-04-05
        assertNull(holiday(2026, 3, 31))
        assertEquals(Holiday.EASTER, holiday(2026, 4, 1))
        assertEquals(Holiday.EASTER, holiday(2026, 4, 5))
        assertEquals(Holiday.EASTER, holiday(2026, 4, 9))
        assertNull(holiday(2026, 4, 10))
        // Easter Sunday 2027-03-28
        assertNull(holiday(2027, 3, 23))
        assertEquals(Holiday.EASTER, holiday(2027, 3, 24))
        assertEquals(Holiday.EASTER, holiday(2027, 4, 1))
        assertNull(holiday(2027, 4, 2))
    }

    @Test
    fun newYearBoundaries() {
        assertEquals(Holiday.NEW_YEAR, holiday(2026, 12, 30))
        assertEquals(Holiday.NEW_YEAR, holiday(2026, 12, 31))
        assertEquals(Holiday.NEW_YEAR, holiday(2027, 1, 1))
        assertEquals(Holiday.NEW_YEAR, holiday(2027, 1, 4))
        assertNull(holiday(2027, 1, 5))
    }

    @Test
    fun christmasBoundariesAndOverlap() {
        assertNull(holiday(2026, 12, 19))
        assertEquals(Holiday.CHRISTMAS, holiday(2026, 12, 20))
        assertEquals(Holiday.CHRISTMAS, holiday(2026, 12, 24))
        // Overlaps with New Year, Christmas wins
        assertEquals(Holiday.CHRISTMAS, holiday(2026, 12, 28))
        assertEquals(Holiday.CHRISTMAS, holiday(2026, 12, 29))
        assertEquals(Holiday.NEW_YEAR, holiday(2026, 12, 30))
    }

    @Test
    fun nicholasBoundaries() {
        assertNull(holiday(2026, 12, 1))
        assertEquals(Holiday.NICHOLAS, holiday(2026, 12, 2))
        assertEquals(Holiday.NICHOLAS, holiday(2026, 12, 6))
        assertEquals(Holiday.NICHOLAS, holiday(2026, 12, 8))
        assertNull(holiday(2026, 12, 9))
    }

    @Test
    fun halloweenBoundaries() {
        assertNull(holiday(2026, 10, 25))
        assertEquals(Holiday.HALLOWEEN, holiday(2026, 10, 26))
        assertEquals(Holiday.HALLOWEEN, holiday(2026, 10, 31))
        assertEquals(Holiday.HALLOWEEN, holiday(2026, 11, 3))
        assertNull(holiday(2026, 11, 4))
    }

    @Test
    fun valentineBoundaries() {
        assertNull(holiday(2027, 2, 9))
        assertEquals(Holiday.VALENTINE, holiday(2027, 2, 10))
        assertEquals(Holiday.VALENTINE, holiday(2027, 2, 14))
        assertEquals(Holiday.VALENTINE, holiday(2027, 2, 18))
        assertNull(holiday(2027, 2, 19))
        // Leap year
        assertNull(holiday(2028, 2, 29))
    }

    @Test
    fun outsideOfAllPeriods() {
        assertNull(holiday(2026, 10, 10))
        assertNull(holiday(2026, 7, 1))
        assertNull(holiday(2026, 11, 15))
    }

    private fun holiday(
        year: Int,
        month: Int,
        day: Int,
    ) = HolidayCalendar.holidayOn(LocalDate.of(year, month, day))
}
