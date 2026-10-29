package com.github.damontecres.wholphin.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YearQueryTest {
    @Test
    fun years() {
        assertEquals(listOf(1994), YearQuery.parse("1994"))
        assertEquals(listOf(2021), YearQuery.parse(" 2021 "))
        assertEquals((1990..1999).toList(), YearQuery.parse("90. roky"))
        assertEquals((1990..1999).toList(), YearQuery.parse("90s"))
        assertEquals((1980..1989).toList(), YearQuery.parse("1980s"))
        assertEquals((2010..2019).toList(), YearQuery.parse("2010s"))
        assertEquals((2000..2009).toList(), YearQuery.parse("00. roky"))
    }

    @Test
    fun notYears() {
        assertNull(YearQuery.parse("Pelíšky"))
        assertNull(YearQuery.parse("123"))
        assertNull(YearQuery.parse("1850"))
        assertNull(YearQuery.parse("Blade Runner 2049"))
    }
}
