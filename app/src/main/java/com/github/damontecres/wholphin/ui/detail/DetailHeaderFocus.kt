package com.github.damontecres.wholphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.csfdId
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.PersonKind

/**
 * D-pad order of a movie/series detail header, top to bottom: my ČSFD rating → description → "Réžia: …" → play [buttons]
 * → the first row below. The default search from a wide row below can otherwise jump to a header element scrolled out of
 * view, so the elements are chained explicitly; missing ones (no ČSFD id, description or director) are skipped.
 */
@Stable
class DetailHeaderFocus(
    item: BaseItem,
    val buttons: FocusRequester,
) {
    val rating = FocusRequester()
    val overview = FocusRequester()
    val director = FocusRequester()

    private val order =
        listOfNotNull(
            rating.takeIf { item.csfdId != null },
            overview.takeIf { item.data.overview != null },
            director.takeIf {
                item.data.people
                    .orEmpty()
                    .any { it.type == PersonKind.DIRECTOR && !it.name.isNullOrBlank() }
            },
            buttons,
        )

    fun above(requester: FocusRequester): FocusRequester? = order.indexOf(requester).takeIf { it > 0 }?.let { order[it - 1] }

    fun below(requester: FocusRequester): FocusRequester? = order.indexOf(requester).takeIf { it >= 0 }?.let { order.getOrNull(it + 1) }

    /** For a focusable element (with its [requester]): up/down go to its neighbours */
    fun chain(requester: FocusRequester): Modifier =
        Modifier
            .focusProperties {
                above(requester)?.let { up = it }
                below(requester)?.let { down = it }
            }.focusRequester(requester)

    /** For a focus group (stars, buttons) with its [requester] set by the caller: up/down leave it to its neighbours */
    fun chainGroup(requester: FocusRequester): Modifier =
        Modifier.focusProperties {
            onExit = {
                when (requestedFocusDirection) {
                    FocusDirection.Up -> {
                        above(requester)?.tryRequestFocus()
                    }

                    FocusDirection.Down -> {
                        below(requester)?.tryRequestFocus()
                    }

                    else -> {}
                }
            }
        }
}

@Composable
fun rememberDetailHeaderFocus(
    item: BaseItem,
    buttons: FocusRequester,
): DetailHeaderFocus = remember(item, buttons) { DetailHeaderFocus(item, buttons) }
