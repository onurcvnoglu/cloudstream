package com.lagradost.cloudstream3.ui.tv.library

import android.content.Context
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.ui.tv.TvCatalogRow
import com.lagradost.cloudstream3.ui.tv.TvMediaItem

private fun SyncAPI.LibraryItem.toTvMediaItem(context: Context): TvMediaItem = TvMediaItem(
    title = name,
    url = url,
    apiName = apiName,
    posterUrl = posterUrl,
    type = type,
    subtitle = buildList {
        episodesCompleted?.let { completed ->
            add(context.getString(R.string.tv_watched_count, completed))
        }
        personalRating?.toFloat()?.let { rating ->
            add(context.getString(R.string.tv_rating_format, rating))
        }
    }.joinToString(" • ").ifBlank { null },
    progress = if (episodesTotal != null && episodesTotal > 0) {
        (episodesCompleted ?: 0).toFloat() / episodesTotal.toFloat()
    } else null,
    id = id,
)

data class TvLibraryState(
    val pages: List<TvCatalogRow> = emptyList(),
    val selectedPage: Int = 0,
    val apiName: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

object TvLibraryPresenter {
    fun present(
        pages: Resource<List<SyncAPI.Page>>?,
        selectedPage: Int,
        apiName: String?,
        context: Context,
    ): TvLibraryState {
        return when (pages) {
            is Resource.Success -> TvLibraryState(
                pages = pages.value.map { page ->
                    TvCatalogRow(
                        title = page.title.asString(context),
                        items = page.items.map { it.toTvMediaItem(context) },
                    )
                },
                selectedPage = selectedPage,
                apiName = apiName,
            )
            is Resource.Failure -> TvLibraryState(error = pages.errorString, apiName = apiName)
            else -> TvLibraryState(loading = true, apiName = apiName)
        }
    }
}
