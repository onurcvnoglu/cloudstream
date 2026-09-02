package com.lagradost.cloudstream3.ui.tv.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable

/** Saved focus coordinates let Back and configuration changes return to the same TV card. */
data class TvHomeFocusState(
    val row: Int = 0,
    val item: Int = 0,
    val sidebarExpanded: Boolean = false,
)

private val tvHomeFocusSaver: Saver<TvHomeFocusState, String> = Saver(
    save = { "${it.row}|${it.item}|${if (it.sidebarExpanded) 1 else 0}" },
    restore = { value ->
        val parts = value.split('|')
        TvHomeFocusState(
            row = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            item = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            sidebarExpanded = parts.getOrNull(2) == "1",
        )
    },
)

@Composable
fun rememberTvHomeFocusState(): MutableState<TvHomeFocusState> =
    rememberSaveable(stateSaver = tvHomeFocusSaver) { mutableStateOf(TvHomeFocusState()) }
