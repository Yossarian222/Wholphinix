package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The ČSFD home rows give up waiting for a slow plugin, keep loading in the background and remember the result
 */
class CsfdTvTipsRowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = AtomicInteger()
    private val release = CountDownLatch(1)

    @After
    fun tearDown() {
        release.countDown()
        scope.cancel()
    }

    private fun service(code: Int = 200): CsfdTvTipsService {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    calls.incrementAndGet()
                    release.await(30, TimeUnit.SECONDS)
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("x")
                        .body("[]".toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        val api = mockk<ApiClient>(relaxed = true)
        every { api.baseUrl } returns "http://localhost"
        val seerrService = mockk<SeerrService>(relaxed = true)
        every { seerrService.active } returns flowOf(false)
        val serverRepository = mockk<ServerRepository>(relaxed = true)
        every { serverRepository.current } returns MutableStateFlow(null)
        return CsfdTvTipsService(api, client, seerrService, serverRepository, scope)
    }

    @Test
    fun slowRow_isAnnouncedAndKeptWhenItFinishes() =
        runTest {
            val service = service()
            val userId = UUID.randomUUID()

            // The test clock skips the timeout right away, while the request is still blocked
            val first =
                runCatching { service.getRowItems(userId, useSeries = false, limit = 10, throwIfUnavailable = true) }
            assertTrue(first.exceptionOrNull() is CsfdRowUnavailableException)
            // Without the flag the row is empty like before
            assertEquals(
                listOf<Any>(),
                service.getRowItems(userId, useSeries = false, limit = 10),
            )
            // The second call shared the running request
            assertTrue(calls.get() <= 1)

            val update =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { service.rowUpdates.first() }
            release.countDown()
            withContext(Dispatchers.Default) { withTimeout(10_000) { update.await() } }

            // Now it is there right away without asking the plugin again
            assertEquals(
                listOf<Any>(),
                service.getRowItems(userId, useSeries = false, limit = 10, throwIfUnavailable = true),
            )
            assertEquals(1, calls.get())
        }

    @Test
    fun failedRow_withoutPreviousVersion() =
        runTest {
            release.countDown()
            val service = service(code = 500)
            val userId = UUID.randomUUID()
            val result =
                runCatching {
                    withContext(Dispatchers.Default) {
                        service.getWatchlistRowItems(userId, useSeries = false, limit = 10, throwIfUnavailable = true)
                    }
                }
            assertTrue(result.exceptionOrNull() is CsfdRowUnavailableException)
            assertEquals(
                listOf<Any>(),
                withContext(Dispatchers.Default) { service.getWatchlistRowItems(userId, useSeries = false, limit = 10) },
            )
        }
}
