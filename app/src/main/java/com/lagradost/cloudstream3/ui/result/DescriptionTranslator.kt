package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.translation.MlKitTranslationEngine
import com.lagradost.cloudstream3.ui.translation.MlKitTranslationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    return when (val result = MlKitTranslationEngine.translate(
        texts = listOf(originalText),
        sourceLanguageTag = null,
        targetLanguageTag = targetLanguageTag,
    )) {
        is MlKitTranslationResult.Translated ->
            DescriptionTranslationResult.Translated(result.texts.single())

        MlKitTranslationResult.Unavailable -> DescriptionTranslationResult.Unavailable
        is MlKitTranslationResult.Failed -> {
            if (!currentCoroutineContext().isActive) throw result.error
            logError(result.error)
            DescriptionTranslationResult.Failed
        }
    }
}
