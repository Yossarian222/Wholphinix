package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.model.TvShowStatus
import com.github.damontecres.wholphin.data.filter.CommunityRatingFilter
import com.github.damontecres.wholphin.data.filter.DecadeFilter
import com.github.damontecres.wholphin.data.filter.DiscoverFilter
import com.github.damontecres.wholphin.data.filter.DiscoverFilterBy
import com.github.damontecres.wholphin.data.filter.DiscoverMovieContentRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverMovieGenreFilter
import com.github.damontecres.wholphin.data.filter.DiscoverMovieStudiosFilter
import com.github.damontecres.wholphin.data.filter.DiscoverRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvContentRatingFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvGenreFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvStatusFilter
import com.github.damontecres.wholphin.data.filter.DiscoverTvStudiosFilter
import com.github.damontecres.wholphin.data.filter.FavoriteFilter
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.FilterVideoType
import com.github.damontecres.wholphin.data.filter.GenreFilter
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.OfficialRatingFilter
import com.github.damontecres.wholphin.data.filter.PlayedFilter
import com.github.damontecres.wholphin.data.filter.StudioFilter
import com.github.damontecres.wholphin.data.filter.VideoTypeFilter
import com.github.damontecres.wholphin.data.filter.YearFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID

/**
 * Button for filtering data.
 *
 * Clicking on it will show a drop-down menu of filterable options. Many of these show a second drop-down menu when clicked.
 *
 * @see GetItemsFilter
 * @see ItemFilterBy
 */
@Composable
fun FilterByButton(
    filterOptions: List<ItemFilterBy<*>>,
    current: GetItemsFilter,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
    onShow: ((Boolean) -> Unit)? = null,
) {
    var dropDown by remember { mutableStateOf(false) }
    var nestedDropDown by remember { mutableStateOf<ItemFilterBy<*>?>(null) }
    // Year is picked in two steps: first the decade, then a year of it (or the whole decade)
    var decade by remember { mutableStateOf<Int?>(null) }
    val filterCount = remember(current, filterOptions) { current.countFilters(filterOptions) }

    Box(modifier = modifier) {
        ExpandableFilterButton(
            filterCount = filterCount,
            onClick = {
                onShow?.invoke(true)
                dropDown = true
            },
            modifier = Modifier,
        )

        DropdownMenu(
            expanded = dropDown,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            onDismissRequest = {
                onShow?.invoke(false)
                dropDown = false
                nestedDropDown = null
            },
        ) {
            if (filterCount > 0) {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                TvDropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = .8f),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.remove),
                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onFilterChange.invoke(current.delete(filterOptions))
                        dropDown = false
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
                HorizontalDivider()
            }
            // Decades are offered inside the year filter
            filterOptions
                .filter { it != DecadeFilter }
                .forEachIndexed { index, filterOption ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }
                    val currentValue =
                        remember(current) {
                            filterOption.get(current) ?: if (filterOption == YearFilter) DecadeFilter.get(current) else null
                        }
                    TvDropdownMenuItem(
                        leadingIcon = {
                            if (currentValue != null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Filter active",
                                )
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(filterOption.stringRes),
                            )
                        },
                        onClick = {
                            nestedDropDown = filterOption
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
        }

        DropdownMenu(
            expanded = nestedDropDown != null,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            offset = DpOffset(80.dp, 16.dp),
            onDismissRequest = {
                // Back from a decade's years goes to the decades
                if (decade != null) decade = null else nestedDropDown = null
            },
        ) {
            if (nestedDropDown == YearFilter) {
                YearMenu(
                    current = current,
                    decade = decade,
                    onDecade = { decade = it },
                    onFilterChange = {
                        onFilterChange(it)
                        decade = null
                        nestedDropDown = null
                    },
                    getPossibleValues = getPossibleValues,
                )
            } else {
                nestedDropDown?.let { filterOption ->
                    filterOption as ItemFilterBy<Any>
                    val currentValue = remember(current) { filterOption.get(current) }

                    var possibleValues by remember { mutableStateOf<List<FilterValueOption>?>(null) }
                    LaunchedEffect(Unit) {
                        possibleValues = getPossibleValues.invoke(filterOption)
                    }

                    if (currentValue != null) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        TvDropdownMenuItem(
                            elevation = 5.dp,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.Red.copy(alpha = .8f),
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.remove),
                                    color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                onFilterChange.invoke(filterOption.set(null, current))
                                nestedDropDown = null
                            },
                            interactionSource = interactionSource,
                            modifier = Modifier,
                        )
                        HorizontalDivider()
                    }

                    if (possibleValues == null) {
                        CircularProgress(Modifier.size(48.dp))
                    } else if (possibleValues?.isEmpty() == true) {
                        Text(
                            text = stringResource(R.string.no_results),
                        )
                    } else {
                        possibleValues.orEmpty().forEachIndexed { index, value ->
                            val focusRequester = remember { FocusRequester() }
                            if (index == 0) {
                                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                            }

                            val isSelected =
                                remember(currentValue) {
                                    when (filterOption) {
                                        GenreFilter,
                                        StudioFilter,
                                        -> {
                                            (currentValue as? List<UUID>)
                                                .orEmpty()
                                                .contains(value.value)
                                        }

                                        FavoriteFilter,
                                        PlayedFilter,
                                        -> {
                                            (currentValue as? Boolean) == value.name.toBoolean()
                                        }

                                        OfficialRatingFilter -> {
                                            (currentValue as? List<String>)
                                                .orEmpty()
                                                .contains(value.name)
                                        }

                                        VideoTypeFilter -> {
                                            (currentValue as? List<FilterVideoType>)
                                                .orEmpty()
                                                .contains(value.value)
                                        }

                                        YearFilter,
                                        DecadeFilter,
                                        -> {
                                            (currentValue as? List<Int>)
                                                .orEmpty()
                                                .contains(value.value)
                                        }

                                        CommunityRatingFilter -> {
                                            (currentValue as? Int) == value.value
                                        }
                                    }
                                }
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused by interactionSource.collectIsFocusedAsState()
                            TvDropdownMenuItem(
                                elevation = 8.dp,
                                interactionSource = interactionSource,
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Filter active",
                                        )
                                    }
                                },
                                text = {
                                    if (filterOption == CommunityRatingFilter) {
                                        SimpleStarRating(
                                            "${value.name}+",
                                            starColor = if (focused) EmptyStarColor else FilledStarColor,
                                        )
                                    } else {
                                        Text(
                                            text = value.name,
                                        )
                                    }
                                },
                                onClick = {
                                    val newFilter =
                                        when (filterOption) {
                                            GenreFilter,
                                            StudioFilter,
                                            -> {
                                                val list = (currentValue as? List<UUID>).orEmpty()
                                                val newValue =
                                                    list
                                                        .toMutableList()
                                                        .apply {
                                                            if (isSelected) {
                                                                remove(value.value!!)
                                                            } else {
                                                                add(value.value!! as UUID)
                                                            }
                                                        }.takeIf { it.isNotEmpty() }
                                                filterOption.set(newValue, current)
                                            }

                                            FavoriteFilter,
                                            PlayedFilter,
                                            -> {
                                                val played = value.name.toBoolean()
                                                filterOption.set(played, current)
                                            }

                                            OfficialRatingFilter -> {
                                                val list = (currentValue as? List<String>).orEmpty()
                                                val newValue =
                                                    list
                                                        .toMutableList()
                                                        .apply {
                                                            if (isSelected) {
                                                                remove(value.name)
                                                            } else {
                                                                add(value.name)
                                                            }
                                                        }.takeIf { it.isNotEmpty() }
                                                filterOption.set(newValue, current)
                                            }

                                            VideoTypeFilter -> {
                                                val list =
                                                    (currentValue as? List<FilterVideoType>).orEmpty()
                                                val newValue =
                                                    list
                                                        .toMutableList()
                                                        .apply {
                                                            if (isSelected) {
                                                                remove(value.value)
                                                            } else {
                                                                add(value.value as FilterVideoType)
                                                            }
                                                        }.takeIf { it.isNotEmpty() }
                                                filterOption.set(newValue, current)
                                            }

                                            YearFilter,
                                            DecadeFilter,
                                            -> {
                                                val list =
                                                    (currentValue as? List<Int>).orEmpty()
                                                val newValue =
                                                    list
                                                        .toMutableList()
                                                        .apply {
                                                            if (isSelected) {
                                                                remove(value.value)
                                                            } else {
                                                                add(value.value as Int)
                                                            }
                                                        }.takeIf { it.isNotEmpty() }
                                                filterOption.set(newValue, current)
                                            }

                                            CommunityRatingFilter -> {
                                                filterOption.set(
                                                    value.value as? Int,
                                                    current,
                                                )
                                            }
                                        }

                                    onFilterChange.invoke(newFilter)
                                    if (!filterOption.supportMultiple) {
                                        nestedDropDown = null
                                    }
                                },
                                modifier = Modifier.focusRequester(focusRequester),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standalone "Year" button in the library header (#70): opens [YearMenu] right away and shows the active
 * year or decade, e.g. "Rok: 1994" / "Rok: 90. roky". The same filter stays reachable in [FilterByButton].
 */
@Composable
fun YearFilterButton(
    current: GetItemsFilter,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
    onShow: ((Boolean) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var decade by remember { mutableStateOf<Int?>(null) }
    val active =
        current.years?.singleOrNull()?.toString()
            ?: current.decades?.singleOrNull()?.let { decadeName(it) }
    Box(modifier = modifier) {
        Button(
            onClick = {
                onShow?.invoke(true)
                open = true
            },
            modifier = Modifier.requiredSizeIn(minHeight = MinButtonSize, maxHeight = MinButtonSize),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.year) + (active?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        DropdownMenu(
            expanded = open,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            onDismissRequest = {
                if (decade != null) {
                    decade = null
                } else {
                    onShow?.invoke(false)
                    open = false
                }
            },
        ) {
            YearMenu(
                current = current,
                decade = decade,
                onDecade = { decade = it },
                onFilterChange = {
                    onFilterChange(it)
                    decade = null
                    onShow?.invoke(false)
                    open = false
                },
                getPossibleValues = getPossibleValues,
            )
        }
    }
}

/** "1990s" style name of a decade; from 2000 on as a range, e.g. "2010 – 2019" */
@Composable
private fun decadeName(decade: Int) = if (decade < 2000) stringResource(R.string.decade_name, decade % 100) else "$decade – ${decade + 9}"

/**
 * Year filter in two steps: the decades in the library, then the years of the picked decade with
 * "Whole decade" on top. A year sets [YearFilter], the whole decade sets [DecadeFilter].
 */
@Composable
private fun YearMenu(
    current: GetItemsFilter,
    decade: Int?,
    onDecade: (Int?) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
) {
    var years by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(Unit) {
        years = getPossibleValues(YearFilter).mapNotNull { it.value as? Int }
    }
    val selectedYears = current.years.orEmpty()
    val selectedDecades = current.decades.orEmpty()
    val cleared = DecadeFilter.set(null, YearFilter.set(null, current))
    val focusRequester = remember(decade) { FocusRequester() }
    LaunchedEffect(decade, years) { focusRequester.tryRequestFocus() }

    @Composable
    fun Item(
        text: String,
        selected: Boolean,
        first: Boolean,
        onClick: () -> Unit,
    ) = TvDropdownMenuItem(
        elevation = 8.dp,
        leadingIcon = { if (selected) Icon(imageVector = Icons.Default.Check, contentDescription = "Filter active") },
        text = { Text(text = text) },
        onClick = onClick,
        modifier = if (first) Modifier.focusRequester(focusRequester) else Modifier,
    )

    if (selectedYears.isNotEmpty() || selectedDecades.isNotEmpty()) {
        TvDropdownMenuItem(
            elevation = 5.dp,
            leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = .8f)) },
            text = { Text(text = stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) },
            onClick = { onFilterChange(cleared) },
        )
        HorizontalDivider()
    }
    val all = years
    when {
        all == null -> {
            CircularProgress(Modifier.size(48.dp))
        }

        all.isEmpty() -> {
            Text(text = stringResource(R.string.no_results))
        }

        decade == null -> {
            all.map { it / 10 * 10 }.distinct().sorted().forEachIndexed { index, d ->
                Item(
                    text = decadeName(d),
                    selected = d in selectedDecades || selectedYears.any { it / 10 * 10 == d },
                    first = index == 0,
                    onClick = { onDecade(d) },
                )
            }
        }

        else -> {
            Item(
                text = stringResource(R.string.whole_decade, decadeName(decade)),
                selected = decade in selectedDecades,
                first = true,
                onClick = { onFilterChange(DecadeFilter.set(listOf(decade), cleared)) },
            )
            all.filter { it / 10 * 10 == decade }.sorted().forEach { year ->
                Item(
                    text = year.toString(),
                    selected = year in selectedYears,
                    first = false,
                    onClick = { onFilterChange(YearFilter.set(listOf(year), cleared)) },
                )
            }
        }
    }
}

@Composable
fun ExpandableFilterButton(
    filterCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconColor: Color = Color.Unspecified,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier =
            modifier.requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        contentPadding = PaddingValues(0.dp), // DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = if (filterCount > 0 || isFocused) (MinButtonSize - 12.dp) else MinButtonSize),
        ) {
            Text(
                text = stringResource(R.string.fa_filter),
                style = MaterialTheme.typography.titleSmall,
                color = iconColor,
                fontSize = 16.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        AnimatedVisibility(filterCount > 0) {
            Text(
                text = filterCount.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        AnimatedVisibility(isFocused) {
            Text(
                text = stringResource(R.string.filter),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        }
    }
}

// TODO this all duplicated code from FilterByButton
@Composable
fun DiscoverFilterByButton(
    filterOptions: List<DiscoverFilterBy<*>>,
    current: DiscoverFilter,
    onFilterChange: (DiscoverFilter) -> Unit,
    getPossibleValues: suspend (DiscoverFilterBy<*>) -> List<FilterValueOption>,
    modifier: Modifier = Modifier,
    onShow: ((Boolean) -> Unit)? = null,
) {
    var dropDown by remember { mutableStateOf(false) }
    var nestedDropDown by remember { mutableStateOf<DiscoverFilterBy<*>?>(null) }
    val filterCount = remember(current, filterOptions) { current.countFilters(filterOptions) }

    Box(modifier = modifier) {
        ExpandableFilterButton(
            filterCount = filterCount,
            onClick = {
                onShow?.invoke(true)
                dropDown = true
            },
            modifier = Modifier,
        )

        DropdownMenu(
            expanded = dropDown,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            onDismissRequest = {
                onShow?.invoke(false)
                dropDown = false
                nestedDropDown = null
            },
        ) {
            if (filterCount > 0) {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                TvDropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = .8f),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.remove),
                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        onFilterChange.invoke(current.delete(filterOptions))
                        dropDown = false
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier,
                )
                HorizontalDivider()
            }
            filterOptions
                .forEachIndexed { index, filterOption ->
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                    }
                    val currentValue = remember(current) { filterOption.get(current) }
                    TvDropdownMenuItem(
                        leadingIcon = {
                            if (currentValue != null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Filter active",
                                )
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(filterOption.stringRes),
                            )
                        },
                        onClick = {
                            nestedDropDown = filterOption
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
        }

        DropdownMenu(
            expanded = nestedDropDown != null,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp),
            offset = DpOffset(80.dp, 16.dp),
            onDismissRequest = {
                nestedDropDown = null
            },
        ) {
            nestedDropDown?.let { filterOption ->
                filterOption as DiscoverFilterBy<Any>
                val currentValue = remember(current) { filterOption.get(current) }

                var possibleValues by remember { mutableStateOf<List<FilterValueOption>?>(null) }
                LaunchedEffect(Unit) {
                    possibleValues = getPossibleValues.invoke(filterOption)
                }

                if (currentValue != null) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    TvDropdownMenuItem(
                        elevation = 5.dp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.Red.copy(alpha = .8f),
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.remove),
                                color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            onFilterChange.invoke(filterOption.set(null, current))
                            nestedDropDown = null
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                    )
                    HorizontalDivider()
                }

                if (possibleValues == null) {
                    CircularProgress(Modifier.size(48.dp))
                } else if (possibleValues?.isEmpty() == true) {
                    Text(
                        text = stringResource(R.string.no_results),
                    )
                } else {
                    possibleValues.orEmpty().forEachIndexed { index, value ->
                        val focusRequester = remember { FocusRequester() }
                        if (index == 0) {
                            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                        }

                        val isSelected =
                            remember(currentValue) {
                                when (filterOption) {
                                    DiscoverMovieGenreFilter,
                                    DiscoverTvGenreFilter,
                                    DiscoverMovieStudiosFilter,
                                    DiscoverTvStudiosFilter,
                                    -> {
                                        (currentValue as? List<Int>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    DiscoverTvStatusFilter -> {
                                        (currentValue as? List<TvShowStatus>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    DiscoverMovieContentRatingFilter,
                                    DiscoverTvContentRatingFilter,
                                    -> {
                                        (currentValue as? List<String>)
                                            .orEmpty()
                                            .contains(value.value)
                                    }

                                    DiscoverRatingFilter -> {
                                        (currentValue as? Int) == value.value
                                    }
                                }
                            }
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        TvDropdownMenuItem(
                            elevation = 8.dp,
                            interactionSource = interactionSource,
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Filter active",
                                    )
                                }
                            },
                            text = {
                                if (filterOption == DiscoverRatingFilter) {
                                    SimpleStarRating(
                                        "${value.name}+",
                                        starColor = if (focused) EmptyStarColor else FilledStarColor,
                                    )
                                } else {
                                    Text(
                                        text = value.name,
                                    )
                                }
                            },
                            onClick = {
                                val newFilter =
                                    when (filterOption) {
                                        DiscoverMovieGenreFilter,
                                        DiscoverTvGenreFilter,
                                        DiscoverMovieStudiosFilter,
                                        DiscoverTvStudiosFilter,
                                        -> {
                                            val list = (currentValue as? List<Int>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value!!)
                                                        } else {
                                                            add(value.value!! as Int)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        DiscoverTvStatusFilter -> {
                                            val list =
                                                (currentValue as? List<TvShowStatus>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value!!)
                                                        } else {
                                                            add(value.value!! as TvShowStatus)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        DiscoverMovieContentRatingFilter,
                                        DiscoverTvContentRatingFilter,
                                        -> {
                                            val list = (currentValue as? List<String>).orEmpty()
                                            val newValue =
                                                list
                                                    .toMutableList()
                                                    .apply {
                                                        if (isSelected) {
                                                            remove(value.value!!)
                                                        } else {
                                                            add(value.value!! as String)
                                                        }
                                                    }.takeIf { it.isNotEmpty() }
                                            filterOption.set(newValue, current)
                                        }

                                        DiscoverRatingFilter -> {
                                            filterOption.set(
                                                value.value as? Int,
                                                current,
                                            )
                                        }
                                    }

                                onFilterChange.invoke(newFilter)
                                if (!filterOption.supportMultiple) {
                                    nestedDropDown = null
                                }
                            },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun ExpandableFilterButtonPreview() {
    WholphinTheme {
        Column {
            ExpandableFilterButton(
                filterCount = 2,
                onClick = {},
            )

            ExpandableFilterButton(
                filterCount = 0,
                onClick = {},
            )
        }
    }
}
