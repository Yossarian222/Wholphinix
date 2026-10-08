package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind

/**
 * "Réžia: …" and "Hrajú: …" lines. Actors keep the metadata order (TMDb/ČSFD billing), so the first ones are the best known.
 */
@Composable
fun CreditsText(
    people: List<BaseItemPerson>?,
    modifier: Modifier = Modifier,
    showDirector: Boolean = true,
    maxActors: Int = 4,
) {
    val (directors, actors) =
        remember(people) {
            val named = people.orEmpty().filter { !it.name.isNullOrBlank() }
            named.filter { it.type == PersonKind.DIRECTOR }.joinToString(", ") { it.name!! } to
                named
                    .filter { it.type == PersonKind.ACTOR }
                    .take(maxActors)
                    .joinToString(", ") { it.name!! }
        }
    if (directors.isEmpty() && actors.isEmpty()) return
    Column(modifier = modifier) {
        if (showDirector && directors.isNotEmpty()) {
            CreditLine(stringResource(R.string.directed_by, directors))
        }
        if (actors.isNotEmpty()) {
            CreditLine(stringResource(R.string.starring, actors))
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
