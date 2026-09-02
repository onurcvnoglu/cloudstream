package com.lagradost.cloudstream3.ui.tv

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.TvType

@Immutable
data class TvMediaItem(
    val title: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val metadata: List<String> = emptyList(),
    val type: TvType? = null,
    val subtitle: String? = null,
    val progress: Float? = null,
    val id: Int? = null,
)

@Immutable
data class TvCatalogRow(
    val title: String,
    val items: List<TvMediaItem>,
    val landscape: Boolean = false,
)
