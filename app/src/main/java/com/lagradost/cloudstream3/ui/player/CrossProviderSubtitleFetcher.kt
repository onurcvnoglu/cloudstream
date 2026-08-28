package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import kotlinx.coroutines.CancellationException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class CrossProviderSubtitleFetcher {
    private data class CachedPageKey(
        val sourceUrl: String,
        val providerName: String,
    )

    private val pageCache = ConcurrentHashMap<CachedPageKey, LoadResponse>()

    suspend fun fetch(
        page: LoadResponse,
        episode: ResultEpisode,
        languageTag: String,
        providers: List<MainAPI>,
        subtitleCallback: (Set<SubtitleData>) -> Unit = {},
    ): Set<SubtitleData> {
        if (languageTag.isBlank()) return emptySet()

        val seenSubtitles = ConcurrentHashMap.newKeySet<Triple<String, Map<String, String>, String?>>()
        return providers
            .asSequence()
            .filter { provider ->
                provider.name != page.apiName &&
                        provider.providerType == ProviderType.DirectProvider &&
                        provider.supportedTypes.any { type -> mediaTypesMatch(page, type) }
            }
            .distinctBy(MainAPI::name)
            .toList()
            .amap { provider ->
                val subtitles = try {
                    fetchFromProvider(page, episode, languageTag, provider)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (throwable: Throwable) {
                    logError(throwable)
                    emptySet()
                }.filterTo(linkedSetOf()) { subtitle ->
                    seenSubtitles.add(
                        Triple(subtitle.getFixedUrl(), subtitle.headers, subtitle.getIETF_tag())
                    )
                }
                if (subtitles.isNotEmpty()) subtitleCallback(subtitles)
                subtitles
            }
            .flatten()
            .toSet()
    }

    private suspend fun fetchFromProvider(
        page: LoadResponse,
        episode: ResultEpisode,
        languageTag: String,
        provider: MainAPI,
    ): Set<SubtitleData> {
        val repository = APIRepository(provider)
        val cacheKey = CachedPageKey(page.uniqueUrl, provider.name)
        val matchingPage = pageCache[cacheKey] ?: findMatchingPage(page, repository)
            ?.also { pageCache[cacheKey] = it }
            ?: return emptySet()
        val episodeData = getMatchingEpisodeData(matchingPage, episode)
        if (episodeData.isEmpty()) return emptySet()

        val subtitles = ConcurrentHashMap.newKeySet<SubtitleData>()
        episodeData.take(MAX_EPISODE_VARIANTS).forEach { data ->
            repository.loadLinks(
                data = data,
                isCasting = false,
                subtitleCallback = { file ->
                    val subtitle = PlayerSubtitleHelper.getSubtitleData(file)
                    if (subtitle.matchesLanguageCode(languageTag)) {
                        subtitles += subtitle.copy(nameSuffix = "[${provider.name}]")
                    }
                },
                callback = {},
            )
        }
        return subtitles
    }

    private suspend fun findMatchingPage(
        page: LoadResponse,
        repository: APIRepository,
    ): LoadResponse? {
        val searchCandidates = linkedMapOf<String, SearchResponse>()
        val queries = page.titleNames()
            .filter(String::isNotBlank)
            .distinctBy(::normalizeTitle)
            .take(MAX_TITLE_QUERIES)
        for (query in queries) {
            val result = repository.search(query, 1)
            if (result !is Resource.Success) continue
            result.value.items
                .filter { candidate -> searchResultMatches(page, candidate) }
                .forEach { candidate -> searchCandidates.putIfAbsent(candidate.url, candidate) }
            if (searchCandidates.size >= MAX_SEARCH_CANDIDATES) break
        }

        for (candidate in searchCandidates.values.take(MAX_SEARCH_CANDIDATES)) {
            val result = repository.load(candidate.url)
            val loadedPage = (result as? Resource.Success)?.value ?: continue
            if (loadedPageMatches(page, candidate, loadedPage)) {
                return loadedPage
            }
        }
        return null
    }

    private fun searchResultMatches(page: LoadResponse, result: SearchResponse): Boolean {
        val resultType = result.type
        if (resultType != null && !mediaTypesMatch(page, resultType)) return false
        if (!yearsMatch(page.year, result.searchYear())) return false
        return page.titleNames().titlesMatch(result.titleNames())
    }

    private fun loadedPageMatches(
        page: LoadResponse,
        searchResult: SearchResponse,
        loadedPage: LoadResponse,
    ): Boolean {
        if (!mediaTypesMatch(page, loadedPage)) return false
        if (!yearsMatch(page.year, loadedPage.year)) return false

        val hasSharedSyncId = page.syncData.any { (key, value) ->
            value.isNotBlank() && loadedPage.syncData[key] == value
        }
        return hasSharedSyncId ||
                page.titleNames().titlesMatch(loadedPage.titleNames()) ||
                page.titleNames().titlesMatch(searchResult.titleNames())
    }

    private fun getMatchingEpisodeData(
        page: LoadResponse,
        currentEpisode: ResultEpisode,
    ): List<String> {
        return when (page) {
            is MovieLoadResponse -> listOf(page.dataUrl)
            is TvSeriesLoadResponse -> page.episodes.mapIndexedNotNull { index, episode ->
                episode.data.takeIf { episode.matches(page, currentEpisode, index) }
            }

            is AnimeLoadResponse -> page.episodes.values.flatMap { episodes ->
                episodes.mapIndexedNotNull { index, episode ->
                    episode.data.takeIf { episode.matches(page, currentEpisode, index) }
                }
            }

            else -> emptyList()
        }.filter(String::isNotBlank).distinct()
    }

    private fun Episode.matches(
        page: LoadResponse,
        currentEpisode: ResultEpisode,
        index: Int,
    ): Boolean {
        val episodeNumber = episode ?: (index + 1)
        if (episodeNumber != currentEpisode.episode) return false

        val expectedSeason = currentEpisode.season ?: currentEpisode.seasonIndex
        if (expectedSeason == null) return true
        val displaySeason = (page as? EpisodeResponse)
            ?.seasonNames
            ?.firstOrNull { seasonData -> seasonData.season == season }
            ?.displaySeason
            ?: season
        return displaySeason == expectedSeason
    }

    private fun LoadResponse.titleNames(): List<String> {
        return buildList {
            add(name)
            if (this@titleNames is AnimeLoadResponse) {
                engName?.let(::add)
                japName?.let(::add)
                synonyms.orEmpty().forEach(::add)
            }
        }
    }

    private fun SearchResponse.titleNames(): List<String> {
        return buildList {
            add(name)
            if (this@titleNames is AnimeSearchResponse) {
                otherName?.let(::add)
            }
        }
    }

    private fun SearchResponse.searchYear(): Int? {
        return when (this) {
            is AnimeSearchResponse -> year
            is MovieSearchResponse -> year
            is TvSeriesSearchResponse -> year
            else -> null
        }
    }

    private fun Collection<String>.titlesMatch(other: Collection<String>): Boolean {
        val normalized = map(::normalizeTitle).filter(String::isNotBlank).toSet()
        return other.any { title -> normalizeTitle(title) in normalized }
    }

    private fun normalizeTitle(title: String): String {
        return Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC_REGEX, "")
    }

    private fun yearsMatch(first: Int?, second: Int?): Boolean {
        return first == null || second == null || first == second
    }

    private fun mediaTypesMatch(page: LoadResponse, type: TvType): Boolean {
        val pageIsMovie = page is MovieLoadResponse || page.type.isMovieType()
        val pageIsEpisode = page is TvSeriesLoadResponse ||
                page is AnimeLoadResponse ||
                page.type.isEpisodeBased()
        return (pageIsMovie && type.isMovieType()) ||
                (pageIsEpisode && type.isEpisodeBased())
    }

    private fun mediaTypesMatch(first: LoadResponse, second: LoadResponse): Boolean {
        val secondType = when (second) {
            is MovieLoadResponse -> TvType.Movie
            is TvSeriesLoadResponse -> TvType.TvSeries
            is AnimeLoadResponse -> TvType.Anime
            else -> second.type
        }
        return mediaTypesMatch(first, secondType)
    }

    private companion object {
        const val MAX_TITLE_QUERIES = 2
        const val MAX_SEARCH_CANDIDATES = 3
        const val MAX_EPISODE_VARIANTS = 2
        val DIACRITICS_REGEX: Regex = "\\p{Mn}+".toRegex()
        val NON_ALPHANUMERIC_REGEX: Regex = "[^\\p{L}\\p{N}]".toRegex()
    }
}
