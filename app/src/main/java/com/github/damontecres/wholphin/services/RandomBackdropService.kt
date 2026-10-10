package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * Provides a fallback backdrop of a random movie or series from the library
 *
 * It is shown on pages which do not have a specific item backdrop (home, library tabs, settings, etc).
 * A handful of random items with backdrops is fetched and cached, then [next] picks another one.
 * How often it changes while staying on the same page is set by [com.github.damontecres.wholphin.preferences.AppPreference.BackdropRotatePref].
 */
@Singleton
class RandomBackdropService
    @Inject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val backdropService: BackdropService,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
    ) {
        private val mutex = Mutex()
        private var pool: List<RandomBackdrop> = emptyList()
        private var poolUserId: UUID? = null
        private var poolFetchedAt = 0L
        private var lastItemId: UUID? = null

        private val _current = MutableStateFlow<BackdropResult?>(null)

        /**
         * The current random backdrop or null if there isn't one (yet)
         */
        val current: StateFlow<BackdropResult?> = _current

        /**
         * Switch to another random backdrop
         */
        suspend fun next() =
            withContext(WholphinDispatchers.IO) {
                try {
                    val backdropStyle =
                        preferences.data
                            .firstOrNull()
                            ?.interfacePreferences
                            ?.backdropStyle
                    if (backdropStyle == BackdropStyle.BACKDROP_NONE) {
                        _current.value = null
                        return@withContext
                    }
                    val candidates = getPool()
                    if (candidates.isEmpty()) {
                        _current.value = null
                        return@withContext
                    }
                    val choice =
                        candidates
                            .filter { it.itemId != lastItemId }
                            .ifEmpty { candidates }
                            .random()
                    lastItemId = choice.itemId
                    val dynamicEnabled =
                        backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR ||
                            backdropStyle == BackdropStyle.UNRECOGNIZED
                    val colors =
                        if (dynamicEnabled) {
                            backdropService.extractColorsFromBackdrop(choice.imageUrl)
                        } else {
                            ExtractedColors.DEFAULT
                        }
                    _current.value =
                        BackdropResult(
                            itemId = RANDOM_PREFIX + choice.itemId,
                            imageUrl = choice.imageUrl,
                            primaryColor = colors.primary,
                            secondaryColor = colors.secondary,
                            tertiaryColor = colors.tertiary,
                        )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Error picking a random backdrop")
                }
            }

        private suspend fun getPool(): List<RandomBackdrop> =
            mutex.withLock {
                val userId = serverRepository.currentUser?.id
                val now = System.currentTimeMillis()
                if (userId != poolUserId || pool.isEmpty() || now - poolFetchedAt > POOL_MAX_AGE) {
                    pool = fetchPool()
                    poolUserId = userId
                    poolFetchedAt = now
                }
                pool
            }

        private suspend fun fetchPool(): List<RandomBackdrop> {
            if (api.baseUrl.isNullOrBlank() || serverRepository.currentUser == null) return emptyList()
            val request =
                GetItemsRequest(
                    recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    sortBy = listOf(ItemSortBy.RANDOM),
                    imageTypes = listOf(ImageType.BACKDROP),
                    imageTypeLimit = 1,
                    limit = POOL_SIZE,
                    enableTotalRecordCount = false,
                )
            return api.itemsApi
                .getItems(request)
                .content.items
                .mapNotNull { item ->
                    imageUrlService
                        .getItemImageUrl(
                            itemId = item.id,
                            itemType = item.type,
                            seriesId = null,
                            useSeriesForPrimary = true,
                            imageType = ImageType.BACKDROP,
                            imageTags = item.imageTags.orEmpty(),
                            backdropTags = item.backdropImageTags.orEmpty(),
                        )?.let { RandomBackdrop(item.id, it) }
                }
        }

        companion object {
            const val RANDOM_PREFIX = "random_"
            private const val POOL_SIZE = 20
            private val POOL_MAX_AGE = 30.minutes.inWholeMilliseconds
        }
    }

private data class RandomBackdrop(
    val itemId: UUID,
    val imageUrl: String,
)
