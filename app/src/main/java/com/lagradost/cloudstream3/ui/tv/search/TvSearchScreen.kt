package com.lagradost.cloudstream3.ui.tv.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.ui.tv.components.TvEmptySurface
import com.lagradost.cloudstream3.ui.tv.components.TvErrorSurface
import com.lagradost.cloudstream3.ui.tv.components.TvLoadingSurface
import com.lagradost.cloudstream3.ui.tv.components.TvPosterCard
import com.lagradost.cloudstream3.ui.tv.components.TvSidebar
import com.lagradost.cloudstream3.ui.tv.focus.tvFocusable
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.TvShapes
import com.lagradost.cloudstream3.ui.tv.theme.TvSpacing

@Composable
fun TvSearchScreen(
    state: TvSearchState,
    onSearch: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onItemClick: (TvMediaItem) -> Unit,
    onNavigate: (String) -> Unit,
    onLegacyNavigate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<TvType?>(null) }
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var advancedMode by rememberSaveable { mutableStateOf(false) }
    val colors = LocalTvColors.current

    BackHandler {
        if (query.isNotBlank() || state.suggestions.isNotEmpty()) {
            query = ""
            onClearSuggestions()
        } else {
            onBack()
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        TvSidebar(
            selectedRoute = "search",
            expanded = false,
            onExpandedChange = { },
            onRouteSelected = onNavigate,
            onLegacySelected = onLegacyNavigate,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = TvSpacing.screen, vertical = TvSpacing.section),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.compact),
        ) {
            Text(text = stringResource(R.string.title_search), color = colors.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChanged(it)
                        if (it.isBlank()) onClearSuggestions()
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surface, RoundedCornerShape(TvShapes.button))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    decorationBox = { innerTextField ->
                        if (query.isBlank()) Text(text = stringResource(R.string.tv_search_titles), color = colors.textSecondary)
                        innerTextField()
                    },
                )
                Button(onClick = { onSearch(query) }, enabled = query.length > 1) {
                    Text(text = stringResource(R.string.search))
                }
                if (query.isNotBlank()) {
                    Button(onClick = {
                        query = ""
                        onClearSuggestions()
                    }) { Text(text = stringResource(R.string.tv_clear)) }
                }
            }

            if (state.suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(colors.surface, RoundedCornerShape(TvShapes.button)),
                ) {
                    items(state.suggestions.take(6), key = { it }) { suggestion ->
                        Text(
                            text = suggestion,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusable {
                                    query = suggestion
                                    onQueryChanged(suggestion)
                                    onClearSuggestions()
                                    onSuggestionSelected(suggestion)
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                TvFilterPill(stringResource(R.string.tv_all), selectedType == null) { selectedType = null }
                TvFilterPill(stringResource(R.string.movies), selectedType == TvType.Movie) { selectedType = TvType.Movie }
                TvFilterPill(stringResource(R.string.tv_series), selectedType == TvType.TvSeries) { selectedType = TvType.TvSeries }
                TvFilterPill(stringResource(R.string.anime), selectedType == TvType.Anime) { selectedType = TvType.Anime }
                TvFilterPill(stringResource(R.string.advanced_search), advancedMode) { advancedMode = !advancedMode }
                state.providers.take(3).forEach { provider ->
                    TvFilterPill(provider, selectedProvider == provider) {
                        selectedProvider = if (selectedProvider == provider) null else provider
                    }
                }
            }

            when {
                state.loading -> TvLoadingSurface(modifier = Modifier.weight(1f))
                state.error != null -> TvErrorSurface(
                    message = state.error,
                    onRetry = { onSearch(query) },
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    val results = state.results.filter {
                        TvSearchPresenter.matches(it, selectedType, selectedProvider)
                    }
                    if (results.isEmpty()) {
                        if (state.history.isNotEmpty() && query.isBlank()) {
                            TvHistoryList(state.history.map { it.searchText }, onSuggestionSelected)
                        } else {
                            TvEmptySurface(stringResource(R.string.tv_no_results), Modifier.weight(1f))
                        }
                    } else {
                        TvResultsList(results, onItemClick, advancedMode, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTvColors.current
    Text(
        text = label,
        color = if (selected) colors.background else colors.textPrimary,
        modifier = Modifier
            .background(
                if (selected) colors.accent else colors.surface,
                RoundedCornerShape(20.dp),
            )
            .tvFocusable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun TvResultsList(
    results: List<TvMediaItem>,
    onItemClick: (TvMediaItem) -> Unit,
    advancedMode: Boolean,
    modifier: Modifier,
) {
    val groups = if (advancedMode) results.groupBy { it.apiName }.toList()
    else listOf("" to results)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.card),
    ) {
        groups.forEach { (provider, providerItems) ->
            item(key = "provider-$provider") {
                Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                    if (advancedMode) {
                        Text(text = provider, color = LocalTvColors.current.textSecondary)
                    }
                    providerItems.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.card)) {
                            row.forEach { item ->
                                TvPosterCard(item, onClick = { onItemClick(item) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHistoryList(history: List<String>, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
        Text(text = stringResource(R.string.tv_recent_searches), color = LocalTvColors.current.textSecondary)
        history.take(8).forEach { item ->
            Text(
                text = item,
                color = LocalTvColors.current.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable { onSelected(item) }
                    .padding(10.dp),
            )
        }
    }
}
