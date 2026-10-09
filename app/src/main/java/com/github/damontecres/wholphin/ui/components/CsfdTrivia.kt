package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CsfdTriviaViewModel
    @Inject
    constructor(
        private val csfdTvTipsService: CsfdTvTipsService,
    ) : ViewModel() {
        private val _items = MutableStateFlow<List<String>>(listOf())
        val items: StateFlow<List<String>> = _items

        fun load(
            csfdId: Int,
            limit: Int,
        ) {
            viewModelScope.launchIO {
                _items.update { csfdTvTipsService.getTrivia(csfdId, limit) }
            }
        }
    }

/**
 * Height for the trivia: the same as the description above it ([lines] of bodyMedium plus its padding), so the text of the
 * details is split about 50/50 between the description and the trivia
 */
@Composable
fun csfdTriviaHeight(lines: Int = 5): Dp {
    val style = MaterialTheme.typography.bodyMedium
    val lineHeight =
        when {
            style.lineHeight.isSp -> style.lineHeight
            style.fontSize.isSp -> style.fontSize * 1.4f
            else -> 20.sp
        }
    return with(LocalDensity.current) { lineHeight.toDp() } * lines + 16.dp
}

/**
 * "Zaujímavosti": interesting facts about [item] from ČSFD, shown under the credits. Nothing if the item has no ČSFD id or
 * ČSFD has no trivia for it.
 *
 * Facts are never cut off: of the [candidates] loaded, only as many as fit whole into [maxHeight] are shown (at most
 * [maxItems]), the shorter ones first, in ČSFD order.
 */
@Composable
fun CsfdTrivia(
    item: BaseItem,
    modifier: Modifier = Modifier,
    maxHeight: Dp = csfdTriviaHeight(),
    maxItems: Int = 4,
    candidates: Int = 8,
    viewModel: CsfdTriviaViewModel = hiltViewModel(key = "csfd_trivia_${item.id}"),
) {
    val csfdId = remember(item.id) { item.csfdId } ?: return
    LaunchedEffect(csfdId) { viewModel.load(csfdId, candidates) }
    val items by viewModel.items.collectAsState()
    if (items.isEmpty()) return
    SubcomposeLayout(modifier) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val gap = 2.dp.roundToPx()
        val title = subcompose("title") { TriviaTitle() }.map { it.measure(childConstraints) }
        val titleHeight = title.maxOfOrNull { it.height } ?: 0
        val facts =
            items.mapIndexed { index, fact ->
                subcompose(index) { TriviaFact(fact) }.map { it.measure(childConstraints) }
            }
        val heights = facts.map { placeables -> placeables.sumOf { it.height } }
        // Shortest first so as many as possible fit, then back in ČSFD order
        var remaining = maxHeight.roundToPx() - titleHeight
        val chosen =
            buildList {
                for (index in heights.indices.sortedBy { heights[it] }) {
                    if (size >= maxItems || heights[index] + gap > remaining) break
                    remaining -= heights[index] + gap
                    add(index)
                }
            }.sorted()
        if (chosen.isEmpty()) {
            layout(0, 0) {}
        } else {
            val placed = title + chosen.flatMap { facts[it] }
            val width = placed.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = (titleHeight + chosen.sumOf { heights[it] + gap }).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                title.forEach { it.placeRelative(0, y) }
                y += titleHeight + gap
                chosen.forEach { index ->
                    facts[index].forEach { it.placeRelative(0, y) }
                    y += heights[index] + gap
                }
            }
        }
    }
}

@Composable
private fun TriviaTitle() {
    Text(
        text = stringResource(R.string.csfd_trivia),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TriviaFact(fact: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = fact,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
