package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * "Zaujímavosti": a few interesting facts about [item] from ČSFD, shown under the credits. Nothing if the item has no
 * ČSFD id or ČSFD has no trivia for it.
 */
@Composable
fun CsfdTrivia(
    item: BaseItem,
    modifier: Modifier = Modifier,
    limit: Int = 4,
    viewModel: CsfdTriviaViewModel = hiltViewModel(key = "csfd_trivia_${item.id}"),
) {
    val csfdId =
        remember(item.id) {
            item.data.providerIds
                ?.entries
                ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
                ?.value
                ?.toIntOrNull()
        } ?: return
    LaunchedEffect(csfdId) { viewModel.load(csfdId, limit) }
    val items by viewModel.items.collectAsState()
    if (items.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.csfd_trivia),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        items.take(limit).forEach { fact ->
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
