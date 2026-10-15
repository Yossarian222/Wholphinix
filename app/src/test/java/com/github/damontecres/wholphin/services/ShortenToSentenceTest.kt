package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortenToSentenceTest {
    @Test
    fun `short text stays`() {
        assertEquals("Krátka veta.", "  Krátka veta. ".shortenToSentence(100))
    }

    @Test
    fun `cut after the last whole sentence`() {
        val text = "Prvá veta je tu. Druhá veta je tiež tu! Tretia veta je už príliš dlhá na limit."
        assertEquals("Prvá veta je tu. Druhá veta je tiež tu!", text.shortenToSentence(50))
    }

    @Test
    fun `one long sentence is cut at a word`() {
        val text = "Jedna veľmi dlhá veta bez bodky, ktorá pokračuje a pokračuje a nekončí"
        assertEquals("Jedna veľmi dlhá veta bez bodky…", text.shortenToSentence(33))
    }
}
