package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.discover.pickLibraryMatch
import org.junit.Assert
import org.junit.Test

class TestDiscoverLibraryMatch {
    private fun item(
        name: String,
        vararg ids: Pair<String, String>,
    ) = BaseItem(movie(name = name).copy(providerIds = mapOf(*ids)))

    private val candidates =
        listOf(
            item("other", "Tmdb" to "1", "Imdb" to "tt1"),
            item("by imdb", "Imdb" to "tt0060196"),
            item("by tmdb", "tmdb" to "429", "Imdb" to "tt9"),
        )

    @Test
    fun `TMDb id wins`() {
        Assert.assertEquals("by tmdb", pickLibraryMatch(candidates, 429, "tt0060196")?.name)
    }

    @Test
    fun `IMDb id is the fallback`() {
        Assert.assertEquals("by imdb", pickLibraryMatch(candidates, 999, "TT0060196")?.name)
        Assert.assertEquals("by imdb", pickLibraryMatch(candidates, null, "tt0060196")?.name)
    }

    @Test
    fun `Not in library`() {
        Assert.assertNull(pickLibraryMatch(candidates, 999, "tt999"))
        Assert.assertNull(pickLibraryMatch(candidates, null, null))
    }
}
