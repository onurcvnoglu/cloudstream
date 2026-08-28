package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.translation.MlKitTranslationEngine
import com.lagradost.cloudstream3.ui.translation.MlKitTranslationResult
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal sealed interface SubtitleTranslationResult {
    data class Translated(val subtitle: SubtitleData) : SubtitleTranslationResult
    data object Unavailable : SubtitleTranslationResult
    data class Failed(val error: Throwable) : SubtitleTranslationResult
}

private data class TranslatableSubtitleCue(
    val startTimeUs: Long,
    val durationUs: Long,
    val text: String,
)

private data class SubtitleContentQuality(
    val cueCount: Long,
    val wordCount: Long,
    val letterCount: Long,
    val lastCueEndTimeUs: Long,
) {
    val score: Long = letterCount + wordCount * 4 + cueCount * 16
}

private data class ParsedSubtitleCandidate(
    val source: SubtitleData,
    val cues: List<TranslatableSubtitleCue>,
    val quality: SubtitleContentQuality,
)

@androidx.annotation.OptIn(UnstableApi::class)
internal class SubtitleTranslationController {
    companion object {
        private const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024
        private const val CACHE_DIRECTORY = "translated_subtitles"
        private const val MIN_PREFERRED_CONTENT_PERCENT = 70L
        private const val MIN_PREFERRED_TIMELINE_PERCENT = 75L
        private val WORD_REGEX = Regex("""[\p{L}\p{N}]+""")
    }

    private val translatedFiles = mutableSetOf<File>()

    /**
     * Önceliklendirilmiş adayların gerçek içeriğini karşılaştırır, en güvenilir kaynağın tüm metin
     * cue'larını çevirip zamanlaması korunmuş geçici WebVTT üretir.
     */
    suspend fun translate(
        context: Context,
        sources: List<SubtitleData>,
        targetLanguageTag: String,
        onProgress: (Int) -> Unit,
    ): SubtitleTranslationResult {
        var lastError: Throwable? = null
        val parsedCandidates = sources.mapNotNull { source ->
            val bytes = try {
                withContext(Dispatchers.IO) { readSubtitle(context, source) }
            } catch (t: Throwable) {
                if (!currentCoroutineContext().isActive) throw t
                lastError = t
                null
            } ?: return@mapNotNull null

            val cues = withContext(Dispatchers.Default) {
                parseSubtitle(bytes, source.mimeType)
            }
            cues.takeIf { it.isNotEmpty() }?.let {
                ParsedSubtitleCandidate(source, it, it.contentQuality())
            }
        }
        if (parsedCandidates.isEmpty()) {
            return lastError?.let(SubtitleTranslationResult::Failed)
                ?: SubtitleTranslationResult.Unavailable
        }

        val selectedCandidate = selectBestCandidate(parsedCandidates)
        val source = selectedCandidate.source
        val cues = selectedCandidate.cues

        return when (val result = MlKitTranslationEngine.translate(
            texts = cues.map(TranslatableSubtitleCue::text),
            sourceLanguageTag = source.getIETF_tag(),
            targetLanguageTag = targetLanguageTag,
            onProgress = onProgress,
        )) {
            is MlKitTranslationResult.Translated -> {
                if (result.texts.size != cues.size || result.texts.any(String::isBlank)) {
                    return SubtitleTranslationResult.Unavailable
                }
                try {
                    SubtitleTranslationResult.Translated(
                        withContext(Dispatchers.IO) {
                            createTranslatedSubtitle(
                                context = context,
                                source = source,
                                targetLanguageTag = targetLanguageTag,
                                cues = cues.zip(result.texts) { cue, text -> cue.copy(text = text) },
                            )
                        }
                    )
                } catch (t: Throwable) {
                    SubtitleTranslationResult.Failed(t)
                }
            }

            MlKitTranslationResult.Unavailable -> SubtitleTranslationResult.Unavailable
            is MlKitTranslationResult.Failed -> SubtitleTranslationResult.Failed(result.error)
        }
    }

    /**
     * İlk dil yeterince doluysa önceliğini korur; belirgin biçimde kısa veya seyrekse içerik ve
     * zaman çizelgesi kapsamı en güçlü olan adaya geçer.
     */
    private fun selectBestCandidate(
        candidates: List<ParsedSubtitleCandidate>,
    ): ParsedSubtitleCandidate {
        val preferred = candidates.first()
        val best = candidates.maxWithOrNull(
            compareBy<ParsedSubtitleCandidate>(
                { it.quality.score },
                { it.quality.lastCueEndTimeUs },
            )
        ) ?: return preferred
        if (preferred === best) return preferred

        val preferredQuality = preferred.quality
        val bestQuality = best.quality
        val hasEnoughContent = preferredQuality.score.isAtLeastPercentOf(
            bestQuality.score,
            MIN_PREFERRED_CONTENT_PERCENT,
        )
        val hasEnoughTimeline = preferredQuality.lastCueEndTimeUs.isAtLeastPercentOf(
            bestQuality.lastCueEndTimeUs,
            MIN_PREFERRED_TIMELINE_PERCENT,
        )
        return if (hasEnoughContent && hasEnoughTimeline) preferred else best
    }

    private fun List<TranslatableSubtitleCue>.contentQuality(): SubtitleContentQuality {
        val texts = asSequence().map(TranslatableSubtitleCue::text).toList()
        return SubtitleContentQuality(
            cueCount = size.toLong(),
            wordCount = texts.sumOf { text -> WORD_REGEX.findAll(text).count().toLong() },
            letterCount = texts.sumOf { text -> text.count(Char::isLetterOrDigit).toLong() },
            lastCueEndTimeUs = maxOfOrNull { cue -> cue.startTimeUs + cue.durationUs } ?: 0L,
        )
    }

    private fun Long.isAtLeastPercentOf(reference: Long, percent: Long): Boolean {
        if (reference <= 0L) return true
        return this * 100L >= reference * percent
    }

    fun clear() {
        translatedFiles.forEach { file -> file.delete() }
        translatedFiles.clear()
    }

    private suspend fun readSubtitle(context: Context, source: SubtitleData): ByteArray? {
        val bytes = when (source.origin) {
            SubtitleOrigin.URL -> app.get(
                url = source.getFixedUrl(),
                headers = source.headers,
            ).body.bytes()

            SubtitleOrigin.DOWNLOADED_FILE -> context.contentResolver
                .openInputStream(Uri.parse(source.getFixedUrl()))
                ?.use { input -> input.readBytes() }

            // Gömülü ve segmentli track'lerin tamamı oynatma başlamadan güvenilir biçimde okunamaz.
            SubtitleOrigin.EMBEDDED_IN_VIDEO -> null
        }
        return bytes?.takeIf { it.isNotEmpty() && it.size <= MAX_SUBTITLE_BYTES }
    }

    private fun parseSubtitle(bytes: ByteArray, mimeType: String): List<TranslatableSubtitleCue> {
        val parsedCues = mutableListOf<CuesWithTiming>()
        val decoder = CustomDecoder(
            Format.Builder()
                .setSampleMimeType(mimeType)
                .build(),
            parseEntireInput = true,
        )
        decoder.parse(
            bytes,
            0,
            bytes.size,
            SubtitleParser.OutputOptions.allCues(),
            Consumer(parsedCues::add),
        )

        return parsedCues.asSequence()
            .filter { cue -> cue.startTimeUs != C.TIME_UNSET && cue.durationUs > 0 }
            .mapNotNull { cue ->
                val text = cue.cues.mapNotNull { it.text?.toString()?.trim() }
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("\n")
                text.takeIf(String::isNotBlank)?.let {
                    TranslatableSubtitleCue(cue.startTimeUs, cue.durationUs, it)
                }
            }
            .groupBy { cue -> cue.startTimeUs to cue.durationUs }
            .map { (_, cuesAtSameTime) ->
                cuesAtSameTime.first().copy(
                    text = cuesAtSameTime.map(TranslatableSubtitleCue::text)
                        .distinct()
                        .joinToString("\n")
                )
            }
            .sortedBy(TranslatableSubtitleCue::startTimeUs)
    }

    private fun createTranslatedSubtitle(
        context: Context,
        source: SubtitleData,
        targetLanguageTag: String,
        cues: List<TranslatableSubtitleCue>,
    ): SubtitleData {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val file = File.createTempFile("subtitle-", ".vtt", directory).apply {
            writeText(buildWebVtt(cues), Charsets.UTF_8)
        }
        translatedFiles += file

        return SubtitleData(
            originalName = fromTagToLanguageName(targetLanguageTag) ?: targetLanguageTag,
            nameSuffix = context.getString(
                R.string.subtitle_translation_name_suffix,
                source.originalName,
            ),
            url = Uri.fromFile(file).toString(),
            origin = SubtitleOrigin.DOWNLOADED_FILE,
            mimeType = MimeTypes.TEXT_VTT,
            headers = emptyMap(),
            languageCode = targetLanguageTag,
            source = source.source,
        )
    }

    private fun buildWebVtt(cues: List<TranslatableSubtitleCue>): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        cues.forEachIndexed { index, cue ->
            appendLine(index + 1)
            append(cue.startTimeUs.toWebVttTimestamp())
            append(" --> ")
            appendLine((cue.startTimeUs + cue.durationUs).toWebVttTimestamp())
            appendLine(cue.text.escapeWebVttText())
            appendLine()
        }
    }

    private fun Long.toWebVttTimestamp(): String {
        val totalMilliseconds = (this / 1_000).coerceAtLeast(0)
        val hours = totalMilliseconds / 3_600_000
        val minutes = totalMilliseconds % 3_600_000 / 60_000
        val seconds = totalMilliseconds % 60_000 / 1_000
        val milliseconds = totalMilliseconds % 1_000
        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            hours,
            minutes,
            seconds,
            milliseconds,
        )
    }

    private fun String.escapeWebVttText(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
