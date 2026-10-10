package com.github.damontecres.wholphin.ui.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.rememberInt
import kotlinx.coroutines.launch

@Composable
fun PersonRow(
    people: List<Person>,
    onClick: (Person) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = R.string.people_title,
    onLongClick: ((Int, Person) -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    var position by rememberInt()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.then(rememberBringRowIntoViewOnFocus()),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = personRowContentPadding,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(people) { index, person ->
                PersonCard(
                    person = person,
                    onClick = {
                        position = index
                        onClick.invoke(person)
                    },
                    onLongClick = {
                        position = index
                        onLongClick?.invoke(index, person)
                    },
                    modifier =
                        Modifier
                            .width(personRowCardWidth)
                            .ifElse(index == position, Modifier.focusRequester(firstFocus))
                            .animateItem(),
                )
            }
        }
    }
}

@Composable
fun DiscoverPersonRow(
    people: List<DiscoverItem>,
    onClick: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = R.string.people_title,
    onLongClick: ((Int, DiscoverItem) -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.then(rememberBringRowIntoViewOnFocus()),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = personRowContentPadding,
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(people) { index, person ->
                PersonCard(
                    name = person.title,
                    role = person.subtitle,
                    imageUrl = person.posterUrl,
                    favorite = false,
                    onClick = { onClick.invoke(person) },
                    onLongClick = { onLongClick?.invoke(index, person) },
                    modifier =
                        Modifier
                            .width(personRowCardWidth)
                            .ifElse(index == 0, Modifier.focusRequester(firstFocus))
                            .animateItem(),
                )
            }
        }
    }
}

val personRowCardWidth = 108.dp

/**
 * Padding around the cards of a person row. The bottom is larger so the name and role under the photo
 * are never cut off, also with the focused (scaled up) card and a large font scale.
 */
private val personRowContentPadding =
    PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 20.dp)

/**
 * When a card in the row gets focus, scroll the whole row (title, photos, names and roles) into view.
 * Otherwise only the focused photo would be brought into view and the text under it could stay cut off at the bottom.
 */
@Composable
private fun rememberBringRowIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return Modifier
        .bringIntoViewRequester(requester)
        .onFocusChanged {
            if (it.hasFocus) {
                // Runs along with the scroll to the focused photo, the scroll container satisfies both requests
                scope.launch { requester.bringIntoView() }
            }
        }
}
