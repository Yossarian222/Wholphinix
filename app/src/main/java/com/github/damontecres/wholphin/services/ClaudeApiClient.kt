package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.preferences.ClaudeModel
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal client for the Anthropic Messages API (`POST /v1/messages`), for short one-shot texts.
 *
 * Uses the plain [OkHttpClient] without the Jellyfin auth interceptor, so no Jellyfin token ever goes to Anthropic.
 * The API key is only sent in the `x-api-key` header and never logged.
 */
@Singleton
class ClaudeApiClient
    @Inject
    constructor(
        @param:StandardOkHttpClient okHttpClient: OkHttpClient,
    ) {
        private val client =
            okHttpClient
                .newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

        /**
         * Returns Claude's text reply, or null on any error (logged without the key) or a refusal.
         */
        suspend fun complete(
            apiKey: String,
            model: ClaudeModel,
            system: String,
            prompt: String,
            maxTokens: Int = 150,
        ): String? =
            withContext(Dispatchers.IO) {
                val modelId = modelId(model)
                val body =
                    buildJsonObject {
                        put("model", modelId)
                        put("max_tokens", maxTokens)
                        // A short quip: no extended thinking, so the whole max_tokens goes to the answer
                        when (model) {
                            ClaudeModel.CLAUDE_SONNET -> {
                                putJsonObject("thinking") { put("type", "between_tools") }
                                // Server-side fallback on a safeguard refusal (Claude API only)
                                put("fallbacks", "default")
                            }

                            else -> {
                                putJsonObject("thinking") { put("type", "disabled") }
                            }
                        }
                        putJsonObject("output_config") { put("effort", "low") }
                        // The system prompt is the same in every request: cache it (only takes effect once it is
                        // above the model's minimum cacheable length, otherwise it is ignored)
                        putJsonArray("system") {
                            addJsonObject {
                                put("type", "text")
                                put("text", system)
                                putJsonObject("cache_control") { put("type", "ephemeral") }
                            }
                        }
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "user")
                                put("content", prompt)
                            }
                        }
                    }
                val request =
                    Request
                        .Builder()
                        .url(API_URL)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", API_VERSION)
                        .apply {
                            if (model == ClaudeModel.CLAUDE_SONNET) header("anthropic-beta", FALLBACK_BETA)
                        }.post(body.toString().toRequestBody(JSON))
                        .build()
                try {
                    client.newCall(request).execute().use { response ->
                        val text = response.body.string()
                        if (!response.isSuccessful) {
                            Timber.w("Claude API: HTTP %s %s", response.code, errorSummary(text))
                            return@withContext null
                        }
                        parseText(text)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // The exception never contains the key (it is only in a header)
                    Timber.w("Claude API request failed: %s", ex.javaClass.simpleName)
                    null
                }
            }

        companion object {
            private const val API_URL = "https://api.anthropic.com/v1/messages"
            private const val API_VERSION = "2023-06-01"
            private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
            private val JSON = "application/json".toMediaType()

            fun modelId(model: ClaudeModel): String =
                when (model) {
                    ClaudeModel.CLAUDE_SONNET -> "claude-sonnet-5-5"
                    else -> "claude-haiku-5-5"
                }

            /** Text blocks of a Messages API response; null for a refusal or no text */
            fun parseText(json: String): String? {
                val obj = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
                val stopReason = (obj["stop_reason"] as? JsonPrimitive)?.contentOrNull
                if (stopReason == "refusal") {
                    Timber.i("Claude API: refusal")
                    return null
                }
                // Read blocks by type: a response may also contain (empty) thinking blocks
                return (obj["content"] as? JsonArray)
                    ?.mapNotNull { block ->
                        (block as? JsonObject)
                            ?.takeIf { (it["type"] as? JsonPrimitive)?.contentOrNull == "text" }
                            ?.let { (it["text"] as? JsonPrimitive)?.contentOrNull }
                    }?.joinToString("")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }

            /** Error type and a short message, for the log */
            private fun errorSummary(json: String): String {
                val error =
                    runCatching { (Json.parseToJsonElement(json) as? JsonObject)?.get("error") as? JsonObject }
                        .getOrNull()
                val type = (error?.get("type") as? JsonPrimitive)?.contentOrNull
                val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull?.take(160)
                return listOfNotNull(type, message).joinToString(": ")
            }
        }
    }
