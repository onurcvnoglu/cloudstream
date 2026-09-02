package com.lagradost.cloudstream3.ui.tv.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.ui.tv.components.TvCatalogRail
import com.lagradost.cloudstream3.ui.tv.components.TvEmptySurface
import com.lagradost.cloudstream3.ui.tv.components.TvErrorSurface
import com.lagradost.cloudstream3.ui.tv.components.TvLoadingSurface
import com.lagradost.cloudstream3.ui.tv.components.TvSidebar
import com.lagradost.cloudstream3.ui.tv.focus.tvFocusable
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.TvSpacing

@Composable
fun TvLibraryScreen(
    state: TvLibraryState,
    onPageSelected: (Int) -> Unit,
    onItemClick: (TvMediaItem) -> Unit,
    onNavigate: (String) -> Unit,
    onLegacyNavigate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Row(modifier = modifier.fillMaxSize()) {
        TvSidebar(
            selectedRoute = "library",
            expanded = false,
            onExpandedChange = { },
            onRouteSelected = onNavigate,
            onLegacySelected = onLegacyNavigate,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = TvSpacing.screen, vertical = TvSpacing.section),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
        ) {
            Text(text = stringResource(R.string.library), color = LocalTvColors.current.textPrimary)
            if (state.pages.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact)) {
                    state.pages.forEachIndexed { index, page ->
                        Text(
                            text = page.title,
                            color = LocalTvColors.current.textPrimary,
                            modifier = Modifier
                                .tvFocusable { onPageSelected(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            when {
                state.loading -> TvLoadingSurface(Modifier.weight(1f))
                state.error != null -> TvErrorSurface(state.error, null, Modifier.weight(1f))
                state.pages.isEmpty() -> TvEmptySurface(stringResource(R.string.tv_empty_library), Modifier.weight(1f))
                else -> {
                    val page = state.pages.getOrNull(state.selectedPage) ?: state.pages.first()
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(TvSpacing.section),
                    ) {
                        item(key = "library-${state.selectedPage}") {
                            TvCatalogRail(page, onItemClick)
                        }
                    }
                }
            }
        }
    }
}
