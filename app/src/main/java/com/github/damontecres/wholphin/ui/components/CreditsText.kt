package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
 * With [clickableDirectors] each director opens their page, which lists their movies & series in the library.
 */
@Composable
fun CreditsText(
    people: List<BaseItemPerson>?,
    modifier: Modifier = Modifier,
    showDirector: Boolean = true,
    maxActors: Int = 4,
    clickableDirectors: Boolean = false,
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
                ClickableDirectors(directors)
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
    viewModel: CreditsViewModel = hiltViewModel(),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CreditLine(stringResource(R.string.directed_by, "").trimEnd())
        directors.forEach { director ->
            Surface(
                onClick = {
                    viewModel.navigationManager.navigateTo(Destination.MediaItem(director.id, BaseItemKind.PERSON, null))
                },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    ),
            ) {
                Text(
                    text = director.name!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
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
