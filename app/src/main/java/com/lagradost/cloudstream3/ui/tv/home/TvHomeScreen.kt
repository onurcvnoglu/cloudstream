package com.lagradost.cloudstream3.ui.tv.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.ui.tv.components.TvCatalogRail
import com.lagradost.cloudstream3.ui.tv.components.TvEmptySurface
import com.lagradost.cloudstream3.ui.tv.components.TvErrorSurface
import com.lagradost.cloudstream3.ui.tv.components.TvHero
import com.lagradost.cloudstream3.ui.tv.components.TvLoadingSurface
import com.lagradost.cloudstream3.ui.tv.components.TvSidebar
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.TvSpacing
import kotlinx.coroutines.delay

@Composable
fun TvHomeScreen(
    state: TvHomeState,
    onEvent: (TvHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvHomeFocusState()
    val focus by focusState
    val listState = rememberLazyListState()
    var focusedItem by remember { mutableStateOf<TvMediaItem?>(null) }
    var heroItem by remember(state.hero?.url) { mutableStateOf(state.hero) }

    LaunchedEffect(state.hero?.url) {
        focusedItem = state.hero
        heroItem = state.hero
    }
    LaunchedEffect(focusedItem?.url) {
        focusedItem?.let { item ->
            // Hızlı D-pad geçişlerinde eski backdrop'un ekranda kalmaması için son odağı geciktirir.
            delay(120)
            heroItem = item
        }
    }
    Row(modifier = modifier.fillMaxSize()) {
        TvSidebar(
            selectedRoute = "home",
            expanded = focus.sidebarExpanded,
            onExpandedChange = { focusState.value = focus.copy(sidebarExpanded = it) },
            onRouteSelected = { route ->
                when (route) {
                    "search" -> onEvent(TvHomeEvent.OpenSearch)
                    "library" -> onEvent(TvHomeEvent.OpenLibrary)
                }
            },
            onLegacySelected = { route ->
                if (route == "downloads") onEvent(TvHomeEvent.OpenDownloads)
                if (route == "settings") onEvent(TvHomeEvent.OpenSettings)
            },
        )
        when (val loadState = state.loadState) {
            TvHomeLoadState.Loading -> TvLoadingSurface(modifier = Modifier.weight(1f))
            is TvHomeLoadState.Error -> TvErrorSurface(
                message = loadState.message,
                onRetry = { onEvent(TvHomeEvent.Refresh) },
                modifier = Modifier.weight(1f),
            )
            TvHomeLoadState.Ready -> {
                if (state.rows.isEmpty()) {
                    TvEmptySurface(message = stringResource(R.string.tv_no_home_content), modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = TvSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
                    ) {
                        item(key = "hero") {
                            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                TvHero(
                                    item = heroItem,
                                    onPlay = heroItem?.let { { onEvent(TvHomeEvent.OpenDetail(it)) } },
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact),
                                ) {
                                    state.providerName?.let { provider ->
                                        Text(text = provider, color = LocalTvColors.current.textSecondary)
                                    }
                                    if (state.providerNames.size > 1) {
                                        Button(onClick = {
                                            val providerIndex = state.providerNames.indexOf(state.providerName)
                                            val nextProvider = state.providerNames[
                                                (providerIndex + 1).coerceAtLeast(0) % state.providerNames.size
                                            ]
                                            onEvent(TvHomeEvent.ChangeProvider(nextProvider))
                                        }) {
                                            Text(text = stringResource(R.string.tv_change_provider))
                                        }
                                    }
                                    Button(onClick = { onEvent(TvHomeEvent.Random) }) {
                                        Text(text = stringResource(R.string.home_random))
                                    }
                                    Button(onClick = { onEvent(TvHomeEvent.Refresh) }) {
                                        Text(text = stringResource(R.string.tv_refresh))
                                    }
                                }
                            }
                        }
                        itemsIndexed(
                            items = state.rows,
                            key = { _, row -> row.title },
                        ) { index, row ->
                            TvCatalogRail(
                                row = row,
                                onItemClick = { item: TvMediaItem ->
                                    focusState.value = focus.copy(row = index, item = row.items.indexOf(item))
                                    onEvent(TvHomeEvent.OpenDetail(item))
                                },
                                onItemFocused = { item ->
                                    focusedItem = item
                                    focusState.value = focus.copy(row = index, item = row.items.indexOf(item))
                                },
                                initialItemIndex = focus.item.takeIf { focus.row == index },
                            )
                        }
                    }
                }
            }
        }
    }
}
