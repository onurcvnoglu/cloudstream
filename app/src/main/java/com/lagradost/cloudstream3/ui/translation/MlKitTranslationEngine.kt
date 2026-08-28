package com.lagradost.cloudstream3.ui.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface MlKitTranslationResult {
    data class Translated(val texts: List<String>) : MlKitTranslationResult
    data object Unavailable : MlKitTranslationResult
    data class Failed(val error: Throwable) : MlKitTranslationResult
}

internal object MlKitTranslationEngine {
    private const val MAX_PARALLEL_TRANSLATIONS = 3
    private const val LANGUAGE_SAMPLE_LENGTH = 2_000

    /**
     * Açıklama ve altyazı akışlarının aynı model yönetimini kullanması için ML Kit yaşam döngüsünü
     * burada topluyoruz.
     */
    suspend fun translate(
        texts: List<String>,
        sourceLanguageTag: String?,
        targetLanguageTag: String,
        onProgress: (Int) -> Unit = {},
    ): MlKitTranslationResult {
        if (texts.isEmpty() || texts.all(String::isBlank)) {
            return MlKitTranslationResult.Unavailable
        }

        val targetLanguage = targetLanguageTag.toTranslateLanguage()
            ?: return MlKitTranslationResult.Unavailable
        val sourceLanguage = sourceLanguageTag.toTranslateLanguage()
            ?: detectLanguage(texts)
            ?: return MlKitTranslationResult.Unavailable

        if (sourceLanguage == targetLanguage) return MlKitTranslationResult.Unavailable

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
        )
        return try {
            onProgress(0)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .awaitMlKitTask()

            val uniqueTexts = texts.distinct()
            val completed = AtomicInteger(0)
            val semaphore = Semaphore(MAX_PARALLEL_TRANSLATIONS)
            val translatedByText = coroutineScope {
                uniqueTexts.map { text ->
                    async {
                        val translated = semaphore.withPermit {
                            translator.translate(text).awaitMlKitTask()
                        }
                        val progress = completed.incrementAndGet() * 100 / uniqueTexts.size
                        onProgress(progress)
                        text to translated
                    }
                }.awaitAll().toMap()
            }

            MlKitTranslationResult.Translated(texts.map { translatedByText.getValue(it) })
        } catch (t: Throwable) {
            if (!currentCoroutineContext().isActive) throw t
            MlKitTranslationResult.Failed(t)
        } finally {
            translator.close()
        }
    }

    private suspend fun detectLanguage(texts: List<String>): String? {
        val sample = texts.asSequence()
            .filter(String::isNotBlank)
            .joinToString("\n")
            .take(LANGUAGE_SAMPLE_LENGTH)
        if (sample.isBlank()) return null

        val languageIdentifier = LanguageIdentification.getClient()
        return try {
            languageIdentifier.identifyLanguage(sample).awaitMlKitTask().toTranslateLanguage()
        } catch (t: Throwable) {
            if (!currentCoroutineContext().isActive) throw t
            null
        } finally {
            languageIdentifier.close()
        }
    }

    private fun String?.toTranslateLanguage(): String? {
        val language = this?.let(Locale::forLanguageTag)?.language ?: return null
        return language.takeIf { it in TranslateLanguage.getAllLanguages() }
    }
}

private suspend fun <T> Task<T>.awaitMlKitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                task.isCanceled -> continuation.cancel()
                task.isSuccessful -> continuation.resume(task.result)
                else -> continuation.resumeWithException(
                    task.exception ?: IllegalStateException("ML Kit task failed without an exception")
                )
            }
        }
    }
