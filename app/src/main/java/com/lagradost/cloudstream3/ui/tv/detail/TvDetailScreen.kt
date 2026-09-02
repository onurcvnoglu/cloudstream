package com.lagradost.cloudstream3.ui.tv.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.ui.tv.components.TvCatalogRail
import com.lagradost.cloudstream3.ui.tv.components.TvErrorSurface
import com.lagradost.cloudstream3.ui.tv.components.TvHero
import com.lagradost.cloudstream3.ui.tv.components.TvLoadingSurface
import com.lagradost.cloudstream3.ui.tv.components.TvMetadataRow
import com.lagradost.cloudstream3.ui.tv.components.TvPosterCard
import com.lagradost.cloudstream3.ui.tv.components.TvSidebar
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.TvSpacing

@Composable
fun TvDetailScreen(
    state: TvDetailState,
    onPlay: (ResultEpisode) -> Unit,
    onEpisodeClick: (ResultEpisode) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onDubSelected: (DubStatus) -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
    onTrailer: () -> Unit,
    onRecommendationClick: (TvMediaItem) -> Unit,
    onNavigate: (String) -> Unit,
    onLegacyNavigate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Row(modifier = modifier.fillMaxSize()) {
        TvSidebar(
            selectedRoute = "",
            expanded = false,
            onExpandedChange = { },
            onRouteSelected = onNavigate,
            onLegacySelected = onLegacyNavigate,
        )
        when (val loadState = state.loadState) {
            TvDetailLoadState.Loading -> TvLoadingSurface(Modifier.weight(1f))
            is TvDetailLoadState.Error -> TvErrorSurface(loadState.message, onBack, Modifier.weight(1f))
            TvDetailLoadState.Ready -> {
                LazyColumn(
                    modifier = modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = TvSpacing.screen, vertical = TvSpacing.section),
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
                ) {
                    item(key = "detail-hero") {
                        TvHero(
                            item = TvMediaItem(
                                title = state.title,
                                url = state.url,
                                apiName = state.apiName,
                                posterUrl = state.poster,
                                backdropUrl = state.backdrop,
                                logoUrl = state.logo,
                                subtitle = state.apiName,
                            ),
                            onPlay = state.primaryEpisode?.let { { onPlay(it) } },
                        )
                    }
                    item(key = "detail-info") {
                        Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                            TvMetadataRow(state.metadata)
                            if (state.plot.isNotBlank()) {
                                Text(
                                    text = state.plot,
                                    color = LocalTvColors.current.textSecondary,
                                    maxLines = 5,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                Button(
                                    onClick = { state.primaryEpisode?.let(onPlay) },
                                    enabled = state.primaryEpisode != null,
                                ) { Text(text = stringResource(R.string.tv_play_resume)) }
                                Button(onClick = onWatched) {
                                    Text(text = stringResource(R.string.action_mark_as_watched))
                                }
                                Button(onClick = onFavorite) {
                                    Text(text = stringResource(R.string.library))
                                }
                                if (state.hasTrailer) {
                                    Button(onClick = onTrailer) {
                                        Text(text = stringResource(R.string.trailer))
                                    }
                                }
                            }
                        }
                    }
                    if (state.seasons.size > 1) {
                        item(key = "seasons") {
                            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                Text(
                                    text = stringResource(R.string.season),
                                    color = LocalTvColors.current.textPrimary,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                    items(state.seasons, key = { it.value }) { season ->
                                        Button(onClick = { onSeasonSelected(season.value) }) {
                                            Text(text = season.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.dubStatuses.size > 1) {
                        item(key = "dub-statuses") {
                            Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                Text(
                                    text = stringResource(R.string.tv_audio),
                                    color = LocalTvColors.current.textPrimary,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                                    items(state.dubStatuses, key = { it.value.name }) { dub ->
                                        Button(onClick = { onDubSelected(dub.value) }) {
                                            Text(text = dub.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.episodes.isNotEmpty()) {
                        item(key = "episodes-title") {
                            Text(text = stringResource(R.string.episodes), color = LocalTvColors.current.textPrimary)
                        }
                        item(key = "episodes") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(TvSpacing.card)) {
                                items(state.episodes, key = { it.id }) { episode ->
                                    val item = episode.toTvMediaItem(context)
                                    TvPosterCard(
                                        item = item,
                                        onClick = { onEpisodeClick(episode) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "recommendations") {
                            TvCatalogRail(
                                row = com.lagradost.cloudstream3.ui.tv.TvCatalogRow(
                                    title = stringResource(R.string.tv_more_like_this),
                                    items = state.recommendations,
                                ),
                                onItemClick = onRecommendationClick,
                            )
                        }
                    }
                }
            }
        }
    }
}
