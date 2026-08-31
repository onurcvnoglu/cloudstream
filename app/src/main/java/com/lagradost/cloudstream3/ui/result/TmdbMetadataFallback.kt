package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.isMovie
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * Supplies optional English metadata without changing a provider's links, media structure, or IDs.
 */
internal object TmdbMetadataFallback {
    private object MetadataProvider : TmdbProvider() {
        override val useMetaLoadResponse = true
    }

    private data class Metadata(val name: String, val plot: String?)

    private val latinLetter = Regex("\\p{IsLatin}")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]")

    suspend fun enrich(response: LoadResponse): LoadResponse {
        val replaceTitle = containsNonLatinLetters(response.name)
        val fillPlot = response.plot.isNullOrBlank()
        if (!replaceTitle && !fillPlot) return response

        val metadata = getMetadata(response, fillPlot) ?: return response
        if (replaceTitle && containsOnlyLatinLetters(metadata.name)) {
            response.name = metadata.name
        }
        if (fillPlot && !metadata.plot.isNullOrBlank()) {
            response.plot = metadata.plot
        }
        return response
    }

    private suspend fun getMetadata(response: LoadResponse, requirePlot: Boolean): Metadata? {
        response.getTMDbId()?.let { directId ->
            return tmdbCall {
                MetadataProvider.load(tmdbUrl(directId, response.isMovie()))
            }?.toMetadata()
        }

        val responseName = normalize(response.name)
        if (responseName.isBlank()) return null

        val results = tmdbCall { MetadataProvider.search(response.name, 1) }?.items.orEmpty()
        val candidates = results.filter { candidate ->
            candidate.type?.isMovieType() == response.isMovie() && yearsMatch(candidate, response)
        }
        val candidate = candidates.firstOrNull { normalize(it.name) == responseName }
            ?: candidates.firstOrNull().takeIf { containsNonLatinLetters(response.name) }
            ?: return null

        val responseImdbId = response.getImdbId()
        if (!requirePlot && responseImdbId == null) {
            return Metadata(candidate.name, null)
        }

        val loaded = tmdbCall { MetadataProvider.load(candidate.url) } ?: return null
        if (responseImdbId != null && !loaded.getImdbId().equals(responseImdbId, ignoreCase = true)) {
            return null
        }
        return loaded.toMetadata(candidate.name)
    }

    private fun LoadResponse.toMetadata(fallbackName: String = this.name): Metadata =
        Metadata(this.name.ifBlank { fallbackName }, this.plot)

    private fun tmdbUrl(id: String, isMovie: Boolean): String =
        "https://www.themoviedb.org/${if (isMovie) "movie" else "tv"}/$id"

    private fun yearsMatch(candidate: SearchResponse, response: LoadResponse): Boolean {
        val candidateYear = when (candidate) {
            is AnimeSearchResponse -> candidate.year
            is MovieSearchResponse -> candidate.year
            is TvSeriesSearchResponse -> candidate.year
            else -> null
        }
        return response.year == null || candidateYear == null || abs(response.year!! - candidateYear) <= 1
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(nonAlphanumeric, "")

    private fun containsNonLatinLetters(value: String): Boolean =
        value.any { it.isLetter() && !latinLetter.matches(it.toString()) }

    private fun containsOnlyLatinLetters(value: String): Boolean =
        value.any(Char::isLetter) && value.all { !it.isLetter() || latinLetter.matches(it.toString()) }

    private suspend fun <T> tmdbCall(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }
}
