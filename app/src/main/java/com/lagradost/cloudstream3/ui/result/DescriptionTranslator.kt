package com.lagradost.cloudstream3.ui.result

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface DescriptionTranslationState {
    data class Original(val text: String) : DescriptionTranslationState
    data object Translating : DescriptionTranslationState
    data class Translated(val text: String) : DescriptionTranslationState
    data object Unavailable : DescriptionTranslationState
    data object Failed : DescriptionTranslationState
}

private sealed interface DescriptionTranslationResult {
    data class Translated(val text: String) : DescriptionTranslationResult
    data object Unavailable : DescriptionTranslationResult
    data object Failed : DescriptionTranslationResult
}

class DescriptionTranslationController {
    private var translationJob: Job? = null

    fun reset(originalText: String, onStateChanged: (DescriptionTranslationState) -> Unit) {
        cancel()
        onStateChanged(DescriptionTranslationState.Original(originalText))
    }

    fun translate(
        scope: CoroutineScope,
        originalText: String,
        targetLanguageTag: String,
        onStateChanged: (DescriptionTranslationState) -> Unit,
    ) {
        cancel()
        translationJob = scope.launch {
            onStateChanged(DescriptionTranslationState.Translating)
            val state = when (val result = translateDescription(originalText, targetLanguageTag)) {
                is DescriptionTranslationResult.Translated ->
                    DescriptionTranslationState.Translated(result.text)

                DescriptionTranslationResult.Unavailable -> DescriptionTranslationState.Unavailable
                DescriptionTranslationResult.Failed -> DescriptionTranslationState.Failed
            }
            if (currentCoroutineContext().isActive) onStateChanged(state)
        }
    }

    fun cancel() {
        translationJob?.cancel()
        translationJob = null
    }
}

private suspend fun translateDescription(
    originalText: String,
    targetLanguageTag: String,
): DescriptionTranslationResult {
    if (originalText.isBlank()) return DescriptionTranslationResult.Unavailable

    val targetLanguage = targetLanguageTag.toTranslateLanguage() ?: return DescriptionTranslationResult.Unavailable
    val languageIdentifier = LanguageIdentification.getClient()
    val sourceLanguage = try {
        languageIdentifier.identifyLanguage(originalText).awaitDescriptionTranslation()
            .toTranslateLanguage()
    } catch (t: Throwable) {
        if (!currentCoroutineContext().isActive) throw t
        logError(t)
        null
    } finally {
        languageIdentifier.close()
    } ?: return DescriptionTranslationResult.Unavailable

    if (sourceLanguage == targetLanguage) return DescriptionTranslationResult.Unavailable

    val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
    )
    return try {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .awaitDescriptionTranslation()
        DescriptionTranslationResult.Translated(
            translator.translate(originalText).awaitDescriptionTranslation()
        )
    } catch (t: Throwable) {
        if (!currentCoroutineContext().isActive) throw t
        logError(t)
        DescriptionTranslationResult.Failed
    } finally {
        translator.close()
    }
}

private fun String.toTranslateLanguage(): String? {
    val language = Locale.forLanguageTag(this).language
    return language.takeIf { it in TranslateLanguage.getAllLanguages() }
}

private suspend fun <T> Task<T>.awaitDescriptionTranslation(): T =
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
