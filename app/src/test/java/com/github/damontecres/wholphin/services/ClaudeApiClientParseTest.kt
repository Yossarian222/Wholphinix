package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeApiClientParseTest {
    @Test
    fun `text blocks without search`() {
        val json = """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":" Ahoj "}]}"""
        assertEquals("Ahoj", ClaudeApiClient.parseText(json))
    }

    @Test
    fun `only the text after the last web search result`() {
        val json =
            """
            {"stop_reason":"end_turn","content":[
              {"type":"text","text":"Pozriem sa na to."},
              {"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"x"}},
              {"type":"web_search_tool_result","tool_use_id":"s1","content":[]},
              {"type":"text","text":"Tarantino tu "},
              {"type":"text","text":"hrá aj sám seba.","citations":[{"type":"web_search_result_location"}]}
            ]}
            """.trimIndent()
        assertEquals("Tarantino tu hrá aj sám seba.", ClaudeApiClient.parseText(json))
    }

    @Test
    fun `refusal or nothing after the search`() {
        assertNull(ClaudeApiClient.parseText("""{"stop_reason":"refusal","content":[{"type":"text","text":"x"}]}"""))
        val json =
            """{"stop_reason":"pause_turn","content":[{"type":"text","text":"Hľadám"},""" +
                """{"type":"server_tool_use","id":"s1","name":"web_search","input":{}}]}"""
        assertNull(ClaudeApiClient.parseText(json))
    }
}
