package com.lagradost.cloudstream3.ui.tv.detail

import android.content.Context
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.result.ExtractedTrailerData
import com.lagradost.cloudstream3.ui.result.ResultData
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.utils.UiText

sealed interface TvDetailLoadState {
    data object Loading : TvDetailLoadState
    data object Ready : TvDetailLoadState
    data class Error(val message: String) : TvDetailLoadState
}

data class TvDetailChoice<T>(
    val label: String,
    val value: T,
)

data class TvDetailState(
    val loadState: TvDetailLoadState = TvDetailLoadState.Loading,
    val url: String = "",
    val apiName: String = "",
    val title: String = "",
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val plot: String = "",
    val metadata: List<String> = emptyList(),
    val episodes: List<ResultEpisode> = emptyList(),
    val primaryEpisode: ResultEpisode? = null,
    val seasons: List<TvDetailChoice<Int>> = emptyList(),
    val dubStatuses: List<TvDetailChoice<DubStatus>> = emptyList(),
    val recommendations: List<TvMediaItem> = emptyList(),
    val hasTrailer: Boolean = false,
)

object TvDetailPresenter {
    fun present(
        page: Resource<ResultData>?,
        episodes: Resource<List<ResultEpisode>>?,
        movie: Resource<Pair<UiText, ResultEpisode>>?,
        recommendations: List<SearchResponse>?,
        trailers: List<ExtractedTrailerData>?,
        seasonSelections: List<Pair<UiText?, Int>>?,
        dubSelections: List<Pair<UiText?, DubStatus>>?,
        context: Context,
    ): TvDetailState {
        val result = (page as? Resource.Success)?.value
        val episodeList = (episodes as? Resource.Success)?.value.orEmpty()
        val movieEpisode = (movie as? Resource.Success)?.value?.second
        val loadState = when (page) {
            is Resource.Failure -> TvDetailLoadState.Error(page.errorString)
            is Resource.Success -> TvDetailLoadState.Ready
            else -> TvDetailLoadState.Loading
        }
        val primaryEpisode = movieEpisode ?: episodeList.firstOrNull()
        return TvDetailState(
            loadState = loadState,
            url = result?.url.orEmpty(),
            apiName = result?.apiName?.asString(context).orEmpty(),
            title = result?.title.orEmpty(),
            poster = result?.posterImage,
            backdrop = result?.backgroundPosterUrl ?: result?.posterBackgroundImage,
            logo = result?.logoUrl,
            plot = result?.plotText?.asString(context).orEmpty(),
            metadata = listOfNotNull(
                result?.typeText?.asString(context),
                result?.yearText?.asString(context),
                result?.durationText?.asString(context),
                result?.contentRatingText?.asString(context),
            ),
            episodes = episodeList,
            primaryEpisode = primaryEpisode,
            seasons = seasonSelections.orEmpty().map { (label, season) ->
                TvDetailChoice(
                    label = label?.asString(context) ?: context.getString(R.string.season_format, context.getString(R.string.season), season, ""),
                    value = season,
                )
            },
            dubStatuses = dubSelections.orEmpty().map { (label, status) ->
                TvDetailChoice(
                    label = label?.asString(context) ?: status.name,
                    value = status,
                )
            },
            recommendations = recommendations.orEmpty().map {
                TvMediaItem(
                    title = it.name,
                    url = it.url,
                    apiName = it.apiName,
                    posterUrl = it.posterUrl,
                    type = it.type,
                )
            },
            hasTrailer = trailers.orEmpty().any { it.mirros.isNotEmpty() },
        )
    }
}

fun ResultEpisode.toTvMediaItem(context: Context): TvMediaItem = TvMediaItem(
    title = name ?: headerName,
    url = data,
    apiName = apiName,
    posterUrl = poster,
    subtitle = listOfNotNull(
        season?.let {
            context.getString(
                R.string.season_format,
                context.getString(R.string.season_short),
                it,
                "",
            )
        },
        context.getString(R.string.episode_format, episode, context.getString(R.string.episode_short)),
    ).joinToString(" "),
    progress = if (duration > 0) position.toFloat() / duration.toFloat() else null,
    type = tvType,
    id = id,
)
