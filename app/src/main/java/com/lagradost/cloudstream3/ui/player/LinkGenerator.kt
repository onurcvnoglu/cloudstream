package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.actions.temp.CloudStreamPackage
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.unshortenLinkSafe

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null,
    val tvType: TvType? = null,
)

/**
 * Used to open the player more easily with the LinkGenerator
 **/
data class BasicLink(
    val url: String,
    val name: String? = null,
)

class LinkGenerator(
    private val links: List<BasicLink>,
    private val extract: Boolean = true,
    private val refererUrl: String? = null,
    id: Int?
) : NoVideoGenerator(id) {
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        links.amap { link ->
            if (!extract) {
                callback(
                    newExtractorLink(
                        "",
                        link.name ?: link.url,
                        unshortenLinkSafe(link.url), // unshorten because it might be a raw link
                        type = INFER_TYPE,
                    ) {
                        this.referer = refererUrl ?: ""
                        this.quality = Qualities.Unknown.value
                    } to null
                )
                return@amap
            }

            // Extractors can emit subtitles before their media links. Buffer the subtitles until
            // the extractor source is known so a relation is never inferred from callback order.
            val extractedLinks = mutableListOf<ExtractorLink>()
            val extractedSubtitles = mutableListOf<SubtitleData>()
            val loaded = loadExtractor(
                link.url,
                refererUrl,
                subtitleCallback = { subtitle ->
                    extractedSubtitles += PlayerSubtitleHelper.getSubtitleData(subtitle)
                },
                callback = { extractedLink ->
                    extractedLinks += extractedLink
                    callback(extractedLink to null)
                }
            )

            if (loaded) {
                val source = extractedLinks
                    .map { it.source }
                    .distinct()
                    .singleOrNull()
                    ?.takeIf { it.isNotBlank() }

                extractedSubtitles.forEach { subtitle ->
                    subtitleCallback(subtitle.copy(source = source))
                }
            } else {
                // If no extractor matched, simply return the original raw link.
                callback(
                    newExtractorLink(
                        "",
                        link.name ?: link.url,
                        unshortenLinkSafe(link.url), // unshorten because it might be a raw link
                        type = INFER_TYPE,
                    ) {
                        this.referer = refererUrl ?: ""
                        this.quality = Qualities.Unknown.value
                    } to null
                )
            }
        }

        return true
    }
}

class MinimalLinkGenerator(
    private val links: List<CloudStreamPackage.MinimalVideoLink>,
    private val subs: List<CloudStreamPackage.MinimalSubtitleLink>,
    id: Int?
) : NoVideoGenerator(id) {
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        for (link in links) {
            callback(link.toExtractorLink())
        }
        for (link in subs) {
            subtitleCallback(link.toSubtitleData())
        }

        return true
    }
}