package com.mp3tag.mobile.ui.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp3tag.mobile.ui.common.rememberAdaptiveWindowInfo

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
    onOpenAutomate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    val scheme = MaterialTheme.colorScheme

    val tiles = listOf(
        HomeTileItem(
            id = "tag_editor",
            title = "Tag editor",
            subtitle = "Browse music folders, batch edit tags, cover art, rename & regex tools",
            badge = "MP3 • FLAC • M4A • OGG • WAV • DSF",
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
            subtitle = "ID3v2 version, theme mode & device hardware performance",
            badge = null,
            icon = Icons.Filled.Settings,
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            onClick = onOpenSettings,
        ),
    )

    val gridColumns = when {
        windowInfo.widthDp >= 900 -> 3
        else -> 2
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
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 1080.dp)
                    .fillMaxSize(),
            ) {
                items(
                    items = tiles,
                    key = { it.id },
                ) { tile ->
                    HomeGridCard(
                        tile = tile,
                        compactHeight = windowInfo.isCompactHeight,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.navigationBarsPadding().height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeGridCard(
    tile: HomeTileItem,
    compactHeight: Boolean,
) {
    ElevatedCard(
        onClick = tile.onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = tile.containerColor,
            contentColor = tile.contentColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compactHeight) 148.dp else 188.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
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
            }

            Spacer(Modifier.height(if (compactHeight) 10.dp else 18.dp))

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
