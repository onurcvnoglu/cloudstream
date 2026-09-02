package com.lagradost.cloudstream3.ui.tv.search

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.search.ExpandableSearchList
import com.lagradost.cloudstream3.ui.search.SearchHistoryItem
import com.lagradost.cloudstream3.ui.tv.TvMediaItem

private fun SearchResponse.toTvMediaItem(): TvMediaItem = TvMediaItem(
    title = name,
    url = url,
    apiName = apiName,
    posterUrl = posterUrl,
    type = type,
)

data class TvSearchState(
    val results: List<TvMediaItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val history: List<SearchHistoryItem> = emptyList(),
    val providers: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

object TvSearchPresenter {
    fun present(
        response: Resource<ExpandableSearchList>?,
        currentSearch: Map<String, ExpandableSearchList>?,
        suggestions: List<String>?,
        history: List<SearchHistoryItem>?,
    ): TvSearchState {
        val resultState = when (response) {
            is Resource.Loading, null -> response is Resource.Loading
            else -> false
        }
        val error = (response as? Resource.Failure)?.errorString
        val results = (response as? Resource.Success)?.value?.list.orEmpty().map { it.toTvMediaItem() }
        val providers = currentSearch.orEmpty().keys.sorted()
        return TvSearchState(
            results = results,
            suggestions = suggestions.orEmpty(),
            history = history.orEmpty(),
            providers = providers,
            loading = resultState,
            error = error,
        )
    }

    fun matches(item: TvMediaItem, type: TvType?, provider: String?): Boolean =
        (type == null || item.type == type) && (provider == null || item.apiName == provider)
}
