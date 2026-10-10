package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.preferences.BACKDROP_ROTATE_MINUTES
import com.github.damontecres.wholphin.preferences.DEFAULT_BACKDROP_ROTATE_MINUTES
import com.github.damontecres.wholphin.preferences.toBackdropRotateMinutes
import org.junit.Assert
import org.junit.Test

class TestBackdropRotateMinutes {
    @Test
    fun `Unset or unknown values use the default`() {
        Assert.assertEquals(5, DEFAULT_BACKDROP_ROTATE_MINUTES)
        Assert.assertEquals(DEFAULT_BACKDROP_ROTATE_MINUTES, 0.toBackdropRotateMinutes())
        Assert.assertEquals(DEFAULT_BACKDROP_ROTATE_MINUTES, 3.toBackdropRotateMinutes())
        Assert.assertEquals(DEFAULT_BACKDROP_ROTATE_MINUTES, (-1).toBackdropRotateMinutes())
    }

    @Test
    fun `Supported values are kept`() {
        Assert.assertEquals(listOf(1, 5, 10, 30, 60), BACKDROP_ROTATE_MINUTES)
        BACKDROP_ROTATE_MINUTES.forEach { Assert.assertEquals(it, it.toBackdropRotateMinutes()) }
    }
}
