package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink

/** Returns the stable extractor source identifier of an extractor link, when available. */
internal fun ExtractorLink.sourceId(): String? {
    return source.takeIf { it.isNotBlank() }
}

/**
 * Selects a preferred source without changing the existing sorted order or fallback behavior.
 */
internal fun selectPreferredLink(
    links: List<DisplayLink>,
    preferredSource: String?,
    preferredSubtitle: SubtitlePreference? = null,
): DisplayLink? {
    val usableLinks = links.filter { it.shouldUseLink }
    val source = preferredSource?.takeIf { it.isNotBlank() }

    source?.let { preferred ->
        usableLinks.firstOrNull { it.link.first?.sourceId() == preferred }?.let { return it }
    }

    preferredSubtitle?.source?.takeIf { it.isNotBlank() }?.let { subtitleSource ->
        usableLinks.firstOrNull { it.link.first?.sourceId() == subtitleSource }?.let { return it }
    }

    return usableLinks.firstOrNull()
}

internal fun hasSourceLinkedSubtitle(
    links: List<DisplayLink>,
    subtitles: Iterable<SubtitleData>,
    languageTag: String?,
): Boolean {
    val language = languageTag?.takeIf { it.isNotBlank() } ?: return false
    return links.any { displayLink ->
        if (!displayLink.shouldUseLink) return@any false
        val linkSource = displayLink.link.first?.sourceId() ?: return@any false
        subtitles.any { subtitle ->
            subtitle.source == linkSource && subtitle.matchesLanguageCode(language)
        }
    }
}

internal data class SubtitlePreference(
    val languageTag: String?,
    val originalName: String,
    val origin: SubtitleOrigin,
    val source: String?,
    val isNone: Boolean = false,
) {
    fun matches(subtitle: SubtitleData, activeSource: String?): Boolean {
        if (isNone) return false
        if (languageTag != null && subtitle.getIETF_tag() != languageTag) return false
        if (subtitle.originalName != originalName || subtitle.origin != origin) return false

        val preferredSource = source?.takeIf { it.isNotBlank() }
        val subtitleSource = subtitle.source?.takeIf { it.isNotBlank() }
        return if (preferredSource != null) {
            preferredSource == activeSource && subtitleSource == preferredSource
        } else {
            // Unrelated subtitles remain usable, but a known relation must not cross sources.
            subtitleSource == null || subtitleSource == activeSource
        }
    }
}

internal fun SubtitleData.toSubtitlePreference(): SubtitlePreference {
    return SubtitlePreference(
        languageTag = getIETF_tag(),
        originalName = originalName,
        origin = origin,
        source = source?.takeIf { it.isNotBlank() },
    )
}

internal fun noSubtitlePreference(): SubtitlePreference {
    return SubtitlePreference(
        languageTag = null,
        originalName = "",
        origin = SubtitleOrigin.URL,
        source = null,
        isNone = true,
    )
}

internal fun selectPreferredSubtitle(
    subtitles: Iterable<SubtitleData>,
    preference: SubtitlePreference?,
    activeSource: String?,
): SubtitleData? {
    if (preference == null || preference.isNone) return null
    return subtitles.firstOrNull { preference.matches(it, activeSource) }
}
