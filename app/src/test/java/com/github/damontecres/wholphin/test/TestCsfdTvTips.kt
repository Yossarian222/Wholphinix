package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.CsfdTvTipsService
import org.junit.Assert
import org.junit.Test
import java.util.UUID

class TestCsfdTvTips {
    @Test
    fun `Parse item ids with and without dashes`() {
        val json =
            """[{"CsfdId":469033,"ItemId":"bd5da175a21379cafa14bda6912038ae"},""" +
                """{"csfdId":9497,"itemId":"5c2db950-1097-1bfc-2128-8ad62bf06e80"},{"CsfdId":1}]"""

        Assert.assertEquals(
            listOf(
                UUID.fromString("bd5da175-a213-79ca-fa14-bda6912038ae"),
                UUID.fromString("5c2db950-1097-1bfc-2128-8ad62bf06e80"),
            ),
            CsfdTvTipsService.parseItemIds(json),
        )
    }
}
