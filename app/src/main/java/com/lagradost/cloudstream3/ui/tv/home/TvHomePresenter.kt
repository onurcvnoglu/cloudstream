package com.lagradost.cloudstream3.ui.tv.home

import android.content.Context
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.tv.TvCatalogRow
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.utils.DataStoreHelper

sealed interface TvHomeLoadState {
    data object Loading : TvHomeLoadState
    data object Ready : TvHomeLoadState
    data class Error(val message: String) : TvHomeLoadState
}

data class TvHomeState(
    val providerName: String? = null,
    val providerNames: List<String> = emptyList(),
    val hero: TvMediaItem? = null,
    val continueWatching: List<TvMediaItem> = emptyList(),
    val rows: List<TvCatalogRow> = emptyList(),
    val loadState: TvHomeLoadState = TvHomeLoadState.Loading,
)

sealed interface TvHomeEvent {
    data object Refresh : TvHomeEvent
    data object OpenSearch : TvHomeEvent
    data object OpenLibrary : TvHomeEvent
    data object OpenDownloads : TvHomeEvent
    data object OpenSettings : TvHomeEvent
    data object Random : TvHomeEvent
    data class OpenDetail(val item: TvMediaItem) : TvHomeEvent
    data class ChangeProvider(val providerName: String) : TvHomeEvent
}

/** ViewModel verisini tekrar ağ çağrısı yapmadan Compose'un immutable TV durumuna çevirir. */
object TvHomePresenter {
    fun present(
        page: Resource<Map<String, HomeViewModel.ExpandableHomepageList>>?,
        apiName: String?,
        resume: List<SearchResponse>?,
        bookmarks: Pair<Boolean, List<SearchResponse>>?,
        preview: Resource<Pair<Boolean, List<LoadResponse>>>?,
        context: Context? = null,
        providerNames: List<String> = emptyList(),
    ): TvHomeState {
        val pageState = when (page) {
            is Resource.Failure -> TvHomeLoadState.Error(page.errorString)
            is Resource.Loading, null -> TvHomeLoadState.Loading
            is Resource.Success -> TvHomeLoadState.Ready
        }

        val resumeItems = resume.orEmpty().map { toMediaItem(it, context) }
        val bookmarkItems = bookmarks?.second.orEmpty().map { toMediaItem(it, context) }
        val previewItems = when (preview) {
            is Resource.Success -> preview.value.second.map { toMediaItem(it, context) }
            else -> emptyList()
        }
        val rows = when (page) {
            is Resource.Success -> page.value.values.mapNotNull { expandable ->
                val items = expandable.list.list.map { item -> toMediaItem(item, context) }
                if (items.isEmpty()) null else TvCatalogRow(
                    title = expandable.list.name,
                    items = items,
                    landscape = expandable.list.isHorizontalImages,
                )
            }
            else -> emptyList()
        }.toMutableList()

        if (bookmarkItems.isNotEmpty()) {
            rows.add(0, TvCatalogRow(context?.getString(R.string.library) ?: "Library", bookmarkItems))
        }
        if (resumeItems.isNotEmpty()) {
            rows.add(
                0,
                TvCatalogRow(
                    context?.getString(R.string.continue_watching) ?: "Continue Watching",
                    resumeItems,
                    landscape = true,
                ),
            )
        }

        val hero = previewItems.firstOrNull() ?: rows.firstOrNull()?.items?.firstOrNull()
        return TvHomeState(
            providerName = apiName,
            providerNames = providerNames,
            hero = hero,
            continueWatching = resumeItems,
            rows = rows,
            loadState = pageState,
        )
    }

    private fun toMediaItem(response: SearchResponse, context: Context?): TvMediaItem {
        val resume = response as? DataStoreHelper.ResumeWatchingResult
        val progress = resume?.watchPos?.let { position ->
            if (position.duration <= 0L) null
            else position.position.toFloat() / position.duration.toFloat()
        }
        val episodeText = resume?.let { data ->
            listOfNotNull(
                data.season?.let { season ->
                    "${context?.getString(R.string.season_short) ?: "S"}$season"
                },
                data.episode?.let { episode ->
                    "${context?.getString(R.string.episode_short) ?: "E"}$episode"
                },
            ).joinToString(" ").ifBlank { null }
        }
        return TvMediaItem(
            title = response.name,
            url = response.url,
            apiName = response.apiName,
            posterUrl = response.posterUrl,
            type = response.type,
            subtitle = episodeText ?: response.type?.name,
            progress = progress,
            id = response.id,
        )
    }

    private fun toMediaItem(response: LoadResponse, context: Context?): TvMediaItem = TvMediaItem(
        title = response.name,
        url = response.url,
        apiName = response.apiName,
        posterUrl = response.posterUrl,
        backdropUrl = response.backgroundPosterUrl,
        logoUrl = response.logoUrl,
        description = response.plot,
        metadata = listOfNotNull(
            response.type.name,
            response.year?.toString(),
            response.duration?.let { duration ->
                context?.getString(R.string.duration_format, duration)
            },
        ),
        type = response.type,
    )
}
