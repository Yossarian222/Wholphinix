package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import javax.inject.Inject

@HiltViewModel
class CreditsViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

/**
 * "Réžia: …" and "Hrajú: …" lines. Actors keep the metadata order (TMDb/ČSFD billing), so the first ones are the best known.
 *
 * With [clickableDirectors] the whole "Réžia: …" line is focusable (with [directorModifier]) and opens the first director's
 * page, which lists their movies & series in the library. Each line is a single line, ellipsized.
 */
@Composable
fun CreditsText(
    people: List<BaseItemPerson>?,
    modifier: Modifier = Modifier,
    showDirector: Boolean = true,
    maxActors: Int = 4,
    clickableDirectors: Boolean = false,
    directorModifier: Modifier = Modifier,
) {
    val (directors, actors) =
        remember(people) {
            val named = people.orEmpty().filter { !it.name.isNullOrBlank() }
            named.filter { it.type == PersonKind.DIRECTOR } to
                named
                    .filter { it.type == PersonKind.ACTOR }
                    .take(maxActors)
                    .joinToString(", ") { it.name!! }
        }
    if (directors.isEmpty() && actors.isEmpty()) return
    Column(modifier = modifier) {
        if (showDirector && directors.isNotEmpty()) {
            if (clickableDirectors) {
                ClickableDirectors(directors, directorModifier)
            } else {
                CreditLine(stringResource(R.string.directed_by, directors.joinToString(", ") { it.name!! }))
            }
        }
        if (actors.isNotEmpty()) {
            CreditLine(stringResource(R.string.starring, actors))
        }
    }
}

@Composable
private fun ClickableDirectors(
    directors: List<BaseItemPerson>,
    modifier: Modifier = Modifier,
    viewModel: CreditsViewModel = hiltViewModel(),
) {
    Surface(
        onClick = {
            viewModel.navigationManager.navigateTo(Destination.MediaItem(directors.first().id, BaseItemKind.PERSON, null))
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        // The text stays aligned with "Hrajú: …" below, the padding is only for the focus background
        modifier = modifier.offset(x = (-6).dp),
    ) {
        Text(
            text = stringResource(R.string.directed_by, directors.joinToString(", ") { it.name!! }),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CreditLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
