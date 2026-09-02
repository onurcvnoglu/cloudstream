package com.lagradost.cloudstream3.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.ui.tv.TvCatalogRow
import com.lagradost.cloudstream3.ui.tv.TvMediaItem
import com.lagradost.cloudstream3.ui.tv.focus.tvFocusable
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvColors
import com.lagradost.cloudstream3.ui.tv.theme.LocalTvMotion
import com.lagradost.cloudstream3.ui.tv.theme.TvShapes
import com.lagradost.cloudstream3.ui.tv.theme.TvSpacing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text

@Composable
fun TvPosterCard(
    item: TvMediaItem,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    Card(
        onClick = onClick,
        modifier = modifier
            .width(156.dp)
            .height(236.dp)
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .semantics { contentDescription = item.title },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xEE080A0F)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(TvSpacing.compact),
            ) {
                Text(
                    text = item.title,
                    color = colors.textPrimary,
                    maxLines = 2,
                )
                item.subtitle?.let { subtitle ->
                    Text(text = subtitle, color = colors.textSecondary, maxLines = 1)
                }
                item.progress?.let { progress -> TvProgressIndicator(progress) }
            }
        }
    }
}

@Composable
fun TvLandscapeCard(
    item: TvMediaItem,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(158.dp)
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .semantics { contentDescription = item.title },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xEE080A0F)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(TvSpacing.compact),
            ) {
                Text(text = item.title, color = LocalTvColors.current.textPrimary, maxLines = 2)
                Text(text = item.apiName, color = LocalTvColors.current.textSecondary, maxLines = 1)
                item.progress?.let { progress -> TvProgressIndicator(progress) }
            }
        }
    }
}

@Composable
fun TvCatalogRail(
    row: TvCatalogRow,
    onItemClick: (TvMediaItem) -> Unit,
    onItemFocused: ((TvMediaItem) -> Unit)? = null,
    initialItemIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (initialItemIndex != null && row.items.getOrNull(initialItemIndex) != null) {
            // Detaydan geri dönüldüğünde son kartı yeniden odaklayarak D-pad akışını korur.
            focusRequester.requestFocus()
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = row.title,
            color = LocalTvColors.current.textPrimary,
            modifier = Modifier.padding(bottom = TvSpacing.compact),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.url }) { index, item ->
                val itemModifier = if (index == initialItemIndex) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
                if (row.landscape) {
                    TvLandscapeCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onFocused = { onItemFocused?.invoke(item) },
                        modifier = itemModifier,
                    )
                } else {
                    TvPosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onFocused = { onItemFocused?.invoke(item) },
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}

@Composable
fun TvProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LocalTvColors.current.surfaceRaised),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .background(LocalTvColors.current.accentStrong),
        )
    }
}

@Composable
fun TvHero(
    item: TvMediaItem?,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(332.dp)
            .clip(RoundedCornerShape(TvShapes.hero)),
    ) {
        AsyncImage(
            model = item?.backdropUrl ?: item?.posterUrl,
            contentDescription = item?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.background, Color.Transparent, colors.background),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, colors.background),
                    ),
                ),
        )
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 560.dp)
                    .padding(TvSpacing.section),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.compact),
            ) {
                if (item.logoUrl.isNullOrBlank()) {
                    Text(text = item.title, color = colors.textPrimary)
                } else {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    )
                }
                item.metadata.takeIf { it.isNotEmpty() }?.let { metadata ->
                    TvMetadataRow(values = metadata)
                }
                item.subtitle?.let { Text(text = it, color = colors.textSecondary, maxLines = 2) }
                item.description?.let { description ->
                    Text(text = description, color = colors.textSecondary, maxLines = 3)
                }
                onPlay?.let {
                    Button(onClick = it) { Text(text = stringResource(R.string.home_play)) }
                }
            }
        }
    }
}

@Composable
fun TvMetadataRow(
    values: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.compact),
    ) {
        values.filter(String::isNotBlank).forEachIndexed { index, value ->
            if (index > 0) Text(text = "•", color = LocalTvColors.current.textSecondary)
            Text(text = value, color = LocalTvColors.current.textSecondary, maxLines = 1)
        }
    }
}

@Composable
fun TvLoadingSurface(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalTvColors.current.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.loading), color = LocalTvColors.current.textSecondary)
    }
}

@Composable
fun TvEmptySurface(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalTvColors.current.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = LocalTvColors.current.textSecondary)
    }
}

@Composable
fun TvErrorSurface(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalTvColors.current.background)
            .padding(TvSpacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = LocalTvColors.current.error)
        onRetry?.let { retry ->
            Spacer(modifier = Modifier.height(TvSpacing.compact))
            Button(onClick = retry) { Text(text = stringResource(R.string.reload_error)) }
        }
    }
}

@Composable
fun TvSidebar(
    selectedRoute: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRouteSelected: (String) -> Unit,
    onLegacySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvColors.current
    val motion = LocalTvMotion.current
    val width by animateDpAsState(
        targetValue = if (expanded) TvSpacing.sidebarExpanded else TvSpacing.sidebar,
        animationSpec = tween(if (motion.reducedMotion) 0 else 180),
        label = "tv-sidebar-width",
    )
    val routes = remember {
        listOf(
            "home" to R.string.title_home,
            "search" to R.string.title_search,
            "library" to R.string.library,
            "downloads" to R.string.title_downloads,
            "settings" to R.string.title_settings,
        )
    }
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(colors.surface)
            .padding(horizontal = TvSpacing.compact, vertical = TvSpacing.section),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.compact),
    ) {
        routes.forEach { (route, label) ->
            val selected = selectedRoute == route
            val itemColor by animateColorAsState(
                if (selected) colors.accentStrong else colors.surface,
                label = "tv-sidebar-item",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TvShapes.button))
                    .background(itemColor)
                    .tvFocusable { if (route == "downloads" || route == "settings") onLegacySelected(route) else onRouteSelected(route) }
                    .padding(horizontal = TvSpacing.compact, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (route) {
                        "home" -> "⌂"
                        "search" -> "⌕"
                        "library" -> "▣"
                        "downloads" -> "⇩"
                        else -> "⚙"
                    },
                    color = colors.textPrimary,
                )
                if (expanded) {
                    Spacer(modifier = Modifier.width(TvSpacing.compact))
                    Text(text = stringResource(label), color = colors.textPrimary, maxLines = 1)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TvShapes.button))
                .tvFocusable { onExpandedChange(!expanded) }
                .padding(horizontal = TvSpacing.compact, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = if (expanded) "‹" else "›", color = colors.textPrimary)
            if (expanded) {
                Spacer(modifier = Modifier.width(TvSpacing.compact))
                Text(text = stringResource(R.string.tv_collapse), color = colors.textSecondary)
            }
        }
    }
}
