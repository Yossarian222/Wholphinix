package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CreditsText
import com.github.damontecres.wholphin.ui.components.CsfdMyRating
import com.github.damontecres.wholphin.ui.components.CsfdTrivia
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.PremiereDateText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.detail.DetailHeaderFocus
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PersonKind

@Composable
fun MovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    focus: DetailHeaderFocus,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val scope = rememberCoroutineScope()
    val bringIntoView =
        Modifier.onFocusChanged {
            if (it.hasFocus) {
                scope.launch(ExceptionHandler()) {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        // Title
        TitleOrLogo(
            item = movie,
            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            QuickDetails(
                movie.ui.quickDetails,
                movie.timeRemainingOrRuntime,
                Modifier.padding(start = HeaderUtils.startPadding),
            )

            PremiereDateText(dto.premiereDate, Modifier.padding(start = HeaderUtils.startPadding))

            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = HeaderUtils.startPadding))
            }

            CsfdMyRating(
                movie,
                Modifier
                    .padding(start = HeaderUtils.startPadding, top = 4.dp)
                    .then(focus.chainGroup(focus.rating))
                    .focusRequester(focus.rating)
                    .then(bringIntoView),
            )

            VideoStreamDetails(
                chosenStreams = chosenStreams,
                numberOfVersions = movie.data.mediaSourceCount ?: 0,
                modifier =
                    Modifier.padding(
                        start = HeaderUtils.startPadding,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
            )
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }

            // Description (upper half of the text), one line each of director & actors, then trivia (lower half)
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 5,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier = focus.chain(focus.overview).then(bringIntoView),
                )
            }

            CreditsText(
                movie.data.people,
                Modifier.padding(start = HeaderUtils.startPadding),
                clickableDirectors = true,
                directorModifier = focus.chain(focus.director).then(bringIntoView),
            )
            CsfdTrivia(movie, Modifier.padding(start = HeaderUtils.startPadding, top = 8.dp))
        }
    }
}
