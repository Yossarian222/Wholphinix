package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.CsfdTvTipsService
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Test
import java.util.UUID

class TestCsfdTvTips {
    @Test
    fun `Parse library and missing tips`() {
        val json =
            """
            [
              {"CsfdId":469033,"Title":"Sicario 2: Soldado","Year":2018,"ItemId":"bd5da175a21379cafa14bda6912038ae","InLibrary":true,"RatingPercent":70},
              {"csfdId":9497,"title":"Matrix Reloaded","itemId":"5c2db950-1097-1bfc-2128-8ad62bf06e80"},
              {"CsfdId":8208,"Title":"Vrah skrývá tvář","Year":1966,"ItemId":null,"InLibrary":false,"RatingPercent":82,
               "MediaType":"movie","Titles":["Vrah skrývá tvář","Vrah skrývá tvář"],"Poster":"https://x/p.jpg"},
              {"Title":"no id"}
            ]
            """.trimIndent()

        val tips = CsfdTvTipsService.parseTips(json)

        Assert.assertEquals(3, tips.size)
        Assert.assertEquals(UUID.fromString("bd5da175-a213-79ca-fa14-bda6912038ae"), tips[0].itemId)
        Assert.assertEquals(UUID.fromString("5c2db950-1097-1bfc-2128-8ad62bf06e80"), tips[1].itemId)
        Assert.assertNull(tips[2].itemId)
        Assert.assertEquals(82, tips[2].ratingPercent)
        Assert.assertFalse(tips[2].isSeries)
        Assert.assertEquals("https://x/p.jpg", tips[2].poster)
        Assert.assertEquals(2, tips[2].titles.size)
    }

    @Test
    fun `Parse optional thumbnail`() {
        val json =
            """
            [
              {"CsfdId":1,"Title":"With","Thumbnail":"//image.pmgstatic.com/cache/resized/w60h85/files/images/film/posters/a.jpg"},
              {"CsfdId":2,"Title":"Without"},
              {"CsfdId":3,"Title":"Blank","Thumbnail":" "}
            ]
            """.trimIndent()

        val tips = CsfdTvTipsService.parseTips(json)

        Assert.assertEquals(
            "https://image.pmgstatic.com/cache/resized/w420/files/images/film/posters/a.jpg",
            tips[0].thumbnail,
        )
        Assert.assertNull(tips[1].thumbnail)
        Assert.assertNull(tips[2].thumbnail)
    }

    @Test
    fun `ČSFD image URLs`() {
        Assert.assertEquals("https://x/p.jpg", CsfdTvTipsService.csfdImageUrl("https://x/p.jpg"))
        Assert.assertEquals(
            "https://image.pmgstatic.com/cache/resized/w420/files/p.jpg",
            CsfdTvTipsService.csfdImageUrl("https://image.pmgstatic.com/cache/resized/w140/files/p.jpg"),
        )
    }

    @Test
    fun `Parse ranks`() {
        val ranks = CsfdTvTipsService.parseRanks(Json.parseToJsonElement("""{"2294":1,"9499":20,"x":3}"""))

        Assert.assertEquals(mapOf(2294 to 1, 9499 to 20), ranks)
    }
}
