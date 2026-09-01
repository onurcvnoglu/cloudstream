package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.DisplayLink
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.SubtitlePreference
import com.lagradost.cloudstream3.ui.player.hasSourceLinkedSubtitle
import com.lagradost.cloudstream3.ui.player.selectPreferredLink
import com.lagradost.cloudstream3.ui.player.previousEpisodeIndex
import com.lagradost.cloudstream3.ui.player.selectPreferredSubtitle
import com.lagradost.cloudstream3.ui.player.toSubtitlePreference
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSelectionTest {
    private fun link(source: String) = runBlocking {
        newExtractorLink(
            source = source,
            name = source,
            url = "https://example.com/$source.mp4",
            type = ExtractorLinkType.VIDEO,
        )
    }

    private fun subtitle(
        name: String,
        source: String?,
        languageCode: String = "tr",
        origin: SubtitleOrigin = SubtitleOrigin.URL,
    ) = SubtitleData(
        originalName = name,
        nameSuffix = "1",
        url = "https://example.com/${name.lowercase()}.vtt",
        origin = origin,
        mimeType = "text/vtt",
        headers = emptyMap(),
        languageCode = languageCode,
        source = source,
    )

    @Test
    fun `preferred source keeps sorted order within same source`() {
        val links = listOf(
            DisplayLink(link("source1") to null, shouldUseLink = true, priority = 100),
            DisplayLink(link("source2") to null, shouldUseLink = true, priority = 90),
            DisplayLink(link("source2") to null, shouldUseLink = true, priority = 80),
        )

        assertEquals("source2", selectPreferredLink(links, "source2")?.link?.first?.source)
        assertEquals("source1", selectPreferredLink(links, "")?.link?.first?.source)
        assertEquals("source1", selectPreferredLink(links, "missing")?.link?.first?.source)
    }

    @Test
    fun `preferred subtitle source selects before profile fallback`() {
        val links = listOf(
            DisplayLink(link("source1") to null, shouldUseLink = true, priority = 100),
            DisplayLink(link("source2") to null, shouldUseLink = true, priority = 90),
            DisplayLink(link("source3") to null, shouldUseLink = true, priority = 80),
        )
        val preferredSubtitle = subtitle("Türkçe", "source2").toSubtitlePreference()

        assertEquals(
            "source2",
            selectPreferredLink(links, null, preferredSubtitle)?.link?.first?.source
        )
        assertEquals(
            "source2",
            selectPreferredLink(links, "source2", preferredSubtitle)?.link?.first?.source
        )
    }

    @Test
    fun `source-linked language matching ignores unusable and unrelated subtitles`() {
        val links = listOf(
            DisplayLink(link("hidden") to null, shouldUseLink = false, priority = 100),
            DisplayLink(link("source1") to null, shouldUseLink = true, priority = 90),
            DisplayLink(link("source2") to null, shouldUseLink = true, priority = 80),
        )
        val subtitles = listOf(
            subtitle("Türkçe", "hidden"),
            subtitle("Türkçe", null),
            subtitle("İndirilen Türkçe", null, origin = SubtitleOrigin.DOWNLOADED_FILE),
        )

        assertEquals(
            "source1",
            selectPreferredLink(links, null)?.link?.first?.source
        )
        assertEquals(
            false,
            hasSourceLinkedSubtitle(links, subtitles, "tr")
        )
    }

    @Test
    fun `missing preferred subtitle source falls back to sorted usable link`() {
        val links = listOf(
            DisplayLink(link("source1") to null, shouldUseLink = true, priority = 100),
            DisplayLink(link("source2") to null, shouldUseLink = true, priority = 90),
        )
        val preference = subtitle("Türkçe", "missing").toSubtitlePreference()

        assertEquals("source1", selectPreferredLink(links, null, preference)?.link?.first?.source)
    }

    @Test
    fun `preferred subtitle requires the selected source when relation exists`() {
        val source1 = subtitle("Türkçe", "source1")
        val source2 = subtitle("Türkçe", "source2")
        val preference = source2.toSubtitlePreference()

        assertEquals(
            source2,
            selectPreferredSubtitle(listOf(source1, source2), preference, "source2")
        )
        assertNull(selectPreferredSubtitle(listOf(source1, source2), preference, "source1"))
    }

    @Test
    fun `previous episode index respects boundaries`() {
        assertEquals(1, previousEpisodeIndex(index = 2, hasPrevious = true))
        assertEquals(0, previousEpisodeIndex(index = 1, hasPrevious = true))
        assertNull(previousEpisodeIndex(index = 0, hasPrevious = false))
    }

    @Test
    fun `subtitle preference remains URL independent`() {
        val preference = SubtitlePreference(
            languageTag = "tr",
            originalName = "Türkçe",
            origin = SubtitleOrigin.URL,
            source = null,
        )
        val changedUrl = subtitle("Türkçe", null).copy(url = "https://other.example/sub.vtt")

        assertEquals(
            changedUrl,
            selectPreferredSubtitle(listOf(changedUrl), preference, "source2")
        )
    }
}
