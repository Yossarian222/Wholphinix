package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.jellyfinId
import com.github.damontecres.wholphin.services.jellyfinIdAsString
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

suspend fun getDiscoverRating(
    id: Int,
    func: suspend () -> DiscoverRating,
): DiscoverRating? =
    try {
        func.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ClientException) {
        if (ex.statusCode == 404) {
            Timber.w("No rating for %s", id)
        } else {
            Timber.e(ex, "Error fetching rating for %s", id)
        }
        null
    } catch (ex: Exception) {
        Timber.e(ex, "Error fetching rating for %s", id)
        null
    }

/**
 * Navigates to the Jellyfin media item if the [mediaInfo] has a valid [jellyfinId]
 *
 * If not, a error toast is shown
 */
suspend fun goToButtonDiscover(
    mediaInfo: MediaInfo?,
    type: BaseItemKind,
    context: Context,
    navigationManager: NavigationManager,
) {
    val id = mediaInfo?.jellyfinId
    if (id != null) {
        navigationManager.navigateTo(
            Destination.MediaItem(
                itemId = id,
                type = type,
            ),
        )
    } else {
        val stringId = mediaInfo?.jellyfinIdAsString
        val msg =
            if (stringId.isNotNullOrBlank()) {
                "Unknown Jellyfin ID: $stringId"
            } else {
                "No Jellyfin ID found"
            }
        Timber.w(
            "Unknown jellyfinId for tmdb=%s: %s/%s",
            mediaInfo?.tmdbId,
            mediaInfo?.jellyfinMediaId,
            mediaInfo?.jellyfinMediaId4k,
        )
        showToast(context, msg)
    }
}

/**
 * Finds a Seerr movie/series in the Jellyfin library: by TMDb id, falling back to the IMDb id.
 *
 * The Jellyfin items API cannot filter by a provider id value (only `hasTmdbId`/`hasImdbId`), so candidates are
 * searched by [titles] and then by [year], and matched by their provider ids. Returns null if it is not in the library.
 */
suspend fun findInJellyfinLibrary(
    api: ApiClient,
    userId: UUID?,
    type: BaseItemKind,
    tmdbId: Int?,
    imdbId: String?,
    titles: List<String?>,
    year: Int?,
): BaseItem? {
    if (tmdbId == null && imdbId.isNullOrBlank()) return null
    val base =
        GetItemsRequest(
            userId = userId,
            recursive = true,
            includeItemTypes = listOf(type),
            fields = listOf(ItemFields.PROVIDER_IDS),
            enableTotalRecordCount = false,
        )
    val searches =
        titles
            .mapNotNull { it?.trim()?.takeIf { title -> title.isNotEmpty() } }
            .distinct()
            .take(4)
            .map { base.copy(searchTerm = it, limit = 20) } +
            // The library may use another title (eg the Slovak one from ČSFD)
            listOfNotNull(year?.let { base.copy(years = listOf(it - 1, it, it + 1), limit = 1000) })
    for (request in searches) {
        val candidates = GetItemsRequestHandler.execute(api, request).toBaseItems()
        pickLibraryMatch(candidates, tmdbId, imdbId)?.let { return it }
    }
    return null
}

/**
 * The item of [candidates] with the TMDb id [tmdbId], otherwise the one with the IMDb id [imdbId]
 */
fun pickLibraryMatch(
    candidates: List<BaseItem>,
    tmdbId: Int?,
    imdbId: String?,
): BaseItem? {
    fun BaseItem.providerId(name: String) =
        data.providerIds
            ?.entries
            ?.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.trim()
    return tmdbId?.let { id -> candidates.firstOrNull { it.providerId("Tmdb") == id.toString() } }
        ?: imdbId?.takeIf { it.isNotBlank() }?.let { id ->
            candidates.firstOrNull { it.providerId("Imdb").equals(id.trim(), ignoreCase = true) }
        }
}
