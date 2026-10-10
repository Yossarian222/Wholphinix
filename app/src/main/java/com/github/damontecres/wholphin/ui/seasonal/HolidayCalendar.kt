package com.github.damontecres.wholphin.ui.seasonal

import java.time.LocalDate
import java.time.MonthDay

/**
 * A holiday period with its own seasonal theme and recommendations
 *
 * @param key the event key of the ČSFD plugin endpoint `/Csfd/Seasonal?event=`
 */
enum class Holiday(
    val key: String,
) {
    NEW_YEAR("newyear"),
    VALENTINE("valentine"),
    EASTER("easter"),
    HALLOWEEN("halloween"),
    NICHOLAS("nicholas"),
    CHRISTMAS("christmas"),
}

/**
 * Which [Holiday] period a date falls into, all computed locally from the date
 *
 * - New Year's Eve/Day 28. 12.–4. 1.
 * - Valentine's Day 10.–18. 2.
 * - Easter: Easter Sunday −4/+4 days
 * - Halloween 26. 10.–3. 11.
 * - St. Nicholas 2.–8. 12.
 * - Christmas 20.–29. 12.
 *
 * Christmas and New Year overlap on 28.–29. 12., Christmas wins there (the Christmas holidays are still on).
 */
object HolidayCalendar {
    /**
     * Returns the holiday period [date] belongs to, or null outside of all periods
     */
    fun holidayOn(date: LocalDate): Holiday? {
        val day = MonthDay.from(date)
        return when {
            day.between(12, 20, 12, 29) -> Holiday.CHRISTMAS
            day.between(12, 28, 12, 31) || day.between(1, 1, 1, 4) -> Holiday.NEW_YEAR
            day.between(12, 2, 12, 8) -> Holiday.NICHOLAS
            day.between(10, 26, 11, 3) -> Holiday.HALLOWEEN
            day.between(2, 10, 2, 18) -> Holiday.VALENTINE
            isEasterPeriod(date) -> Holiday.EASTER
            else -> null
        }
    }

    /**
     * Whether [date] is within 4 days of Easter Sunday
     */
    fun isEasterPeriod(date: LocalDate): Boolean {
        val easter = easterSunday(date.year)
        return !date.isBefore(easter.minusDays(EASTER_DAYS)) && !date.isAfter(easter.plusDays(EASTER_DAYS))
    }

    /**
     * Western (Gregorian) Easter Sunday of [year], computed with the anonymous Gregorian algorithm (Meeus/Jones/Butcher)
     */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private const val EASTER_DAYS = 4L

    private fun MonthDay.between(
        startMonth: Int,
        startDay: Int,
        endMonth: Int,
        endDay: Int,
    ): Boolean = !isBefore(MonthDay.of(startMonth, startDay)) && !isAfter(MonthDay.of(endMonth, endDay))
}
