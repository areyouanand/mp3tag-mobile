package com.mp3tag.mobile.ui.home

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.data.prefs.DesignLanguage
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo
import com.mp3tag.mobile.ui.theme.LocalDesignLanguage
import kotlin.math.abs

private data class HomeTileItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTagEditor: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenSpectrum: () -> Unit,
    onOpenReplayGain: () -> Unit,
    onOpenAutomate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val isVertical = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    val tiles = listOf(
        HomeTileItem(
            id = "tag_editor",
            title = "Tag editor",
            subtitle = "Browse music folders, batch edit tags, cover art, rename & regex tools",
            badge = "MP3 • FLAC • M4A • OGG • OPUS • WAV • AIFF • WMA • APE • WV • DSF • DFF",
            icon = Icons.Filled.MusicNote,
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            onClick = onOpenTagEditor,
        ),
        HomeTileItem(
            id = "file_manager",
            title = "File manager",
            subtitle = "Browse all storage, copy, move, rename, delete, create folders & inspect files",
            badge = "Full storage access",
            icon = Icons.Filled.FolderOpen,
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            onClick = onOpenFileManager,
        ),
        HomeTileItem(
            id = "spectrum",
            title = "Spectrum analysis",
            subtitle = "Spectrogram and cutoff check. Spots upsampled files and lossy sources hiding in lossless",
            badge = "Hi-res check",
            icon = Icons.Filled.GraphicEq,
            containerColor = scheme.surfaceVariant,
            contentColor = scheme.onSurfaceVariant,
            onClick = onOpenSpectrum,
        ),
        HomeTileItem(
            id = "replaygain",
            title = "ReplayGain",
            subtitle = "Analyze loudness and write ReplayGain tags for consistent playback volume",
            badge = "Loudness",
            icon = Icons.Filled.Tune,
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            onClick = onOpenReplayGain,
        ),
        HomeTileItem(
            id = "automate",
            title = "Automate",
            subtitle = "Automated tagging & file workflows",
            badge = "Coming soon",
            icon = Icons.Filled.AutoAwesome,
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            onClick = onOpenAutomate,
        ),
        HomeTileItem(
            id = "settings",
            title = "Settings",
            subtitle = "Screen mode, design language, ID3v2 version & device performance",
            badge = null,
            icon = Icons.Filled.Settings,
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            onClick = onOpenSettings,
        ),
    )

    val totalItems = tiles.size * 2000
    val startIndex = tiles.size * 1000
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    val centeredIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = if (isVertical) {
                info.viewportStartOffset + info.viewportSize.height / 2f
            } else {
                info.viewportStartOffset + info.viewportSize.width / 2f
            }
            info.visibleItemsInfo.minByOrNull {
                val center = if (isVertical) it.offset + it.size / 2f else it.offset + it.size / 2f
                abs(center - viewportCenter)
            }?.index ?: startIndex
        }
    }
    val centeredTile = tiles[centeredIndex % tiles.size]

    val hasScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex != startIndex || listState.firstVisibleItemScrollOffset != 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MP3Tag Mobile",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Choose a workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))

                if (isVertical) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(count = totalItems, key = { it }) { index ->
                            val tile = tiles[index % tiles.size]
                            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.key == index }
                            val viewportCenter = listState.layoutInfo.viewportStartOffset +
                                listState.layoutInfo.viewportSize.height / 2f
                            val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter
                            val distance = itemCenter - viewportCenter
                            val itemSize = itemInfo?.size?.toFloat()?.coerceAtLeast(1f) ?: 240f
                            val normalized = (distance / itemSize).coerceIn(-3f, 3f)
                            val scale = (1f - abs(normalized) * 0.18f).coerceIn(0.62f, 1f)
                            val alpha = (1f - abs(normalized) * 0.35f).coerceIn(0.35f, 1f)
                            val rotation = normalized * 38f

                            Surface(
                                onClick = tile.onClick,
                                shape = MaterialTheme.shapes.extraLarge,
                                color = tile.containerColor,
                                contentColor = tile.contentColor,
                                modifier = Modifier
                                    .size(width = 260.dp, height = 120.dp)
                                    .graphicsLayer {
                                        rotationX = rotation
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                        cameraDistance = 16f * density.density
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = tile.contentColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = tile.icon,
                                                contentDescription = null,
                                                tint = tile.contentColor,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = tile.contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = tile.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tile.contentColor.copy(alpha = 0.82f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyRow(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    ) {
                        items(count = totalItems, key = { it }) { index ->
                            val tile = tiles[index % tiles.size]
                            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.key == index }
                            val viewportCenter = listState.layoutInfo.viewportStartOffset +
                                listState.layoutInfo.viewportSize.width / 2f
                            val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter
                            val distance = itemCenter - viewportCenter
                            val itemWidth = itemInfo?.size?.toFloat()?.coerceAtLeast(1f) ?: 220f
                            val normalized = (distance / itemWidth).coerceIn(-3f, 3f)
                            val scale = (1f - abs(normalized) * 0.18f).coerceIn(0.62f, 1f)
                            val alpha = (1f - abs(normalized) * 0.35f).coerceIn(0.35f, 1f)
                            val rotation = normalized * 38f

                            Surface(
                                onClick = tile.onClick,
                                shape = MaterialTheme.shapes.extraLarge,
                                color = tile.containerColor,
                                contentColor = tile.contentColor,
                                modifier = Modifier
                                    .size(width = 220.dp, height = 240.dp)
                                    .graphicsLayer {
                                        rotationY = rotation
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                        cameraDistance = 16f * density.density
                                    },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = tile.contentColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = tile.icon,
                                                contentDescription = null,
                                                tint = tile.contentColor,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    }
                                    if (tile.badge != null) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = tile.contentColor.copy(alpha = 0.14f),
                                        ) {
                                            Text(
                                                text = tile.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tile.contentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Column {
                                        Text(
                                            text = tile.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = tile.contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = tile.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tile.contentColor.copy(alpha = 0.82f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = centeredTile.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = centeredTile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 520.dp),
                )
                if (!hasScrolled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Swipe to browse",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
