package com.mp3tag.mobile.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private data class HomeTileItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenTagEditor: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenSpectrum: () -> Unit,
    onOpenReplayGain: () -> Unit,
    onOpenAutomate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isVertical = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    val tiles = listOf(
        HomeTileItem(
            id = "tag_editor",
            title = "Tag editor",
            subtitle = "Browse music folders, batch edit tags, cover art, rename & regex tools",
            badge = "MP3 • FLAC • M4A • OGG • OPUS • WAV • AIFF • DSD",
            icon = Icons.Filled.MusicNote,
            gradientStart = Color(0xFF1E3C72),
            gradientEnd = Color(0xFF2A5298),
            accentColor = Color(0xFF82B1FF),
            onClick = onOpenTagEditor,
        ),
        HomeTileItem(
            id = "file_manager",
            title = "File manager",
            subtitle = "Browse storage volumes, copy, move, rename, delete & inspect files",
            badge = "Full storage access",
            icon = Icons.Filled.FolderOpen,
            gradientStart = Color(0xFF134E5E),
            gradientEnd = Color(0xFF2B7A78),
            accentColor = Color(0xFF80CBC4),
            onClick = onOpenFileManager,
        ),
        HomeTileItem(
            id = "spectrum",
            title = "Spectrum analysis",
            subtitle = "Spectrogram and cutoff check to spot upsampled or lossy transcodes",
            badge = "Hi-res check",
            icon = Icons.Filled.GraphicEq,
            gradientStart = Color(0xFF3A1C71),
            gradientEnd = Color(0xFF6A3093),
            accentColor = Color(0xFFCE93D8),
            onClick = onOpenSpectrum,
        ),
        HomeTileItem(
            id = "replaygain",
            title = "ReplayGain",
            subtitle = "Analyze track & album loudness and write ReplayGain / Opus R128 tags",
            badge = "Loudness • -18 LUFS",
            icon = Icons.Filled.Tune,
            gradientStart = Color(0xFF602040),
            gradientEnd = Color(0xFF9E2A5B),
            accentColor = Color(0xFFF48FB1),
            onClick = onOpenReplayGain,
        ),
        HomeTileItem(
            id = "automate",
            title = "Automate",
            subtitle = "Automated batch tagging & multi-step file workflows",
            badge = "Coming soon",
            icon = Icons.Filled.AutoAwesome,
            gradientStart = Color(0xFF4A3520),
            gradientEnd = Color(0xFF8D6239),
            accentColor = Color(0xFFFFCC80),
            onClick = onOpenAutomate,
        ),
        HomeTileItem(
            id = "settings",
            title = "Settings",
            subtitle = "Screen mode, design language, ID3v2 version & device performance",
            badge = "Preferences",
            icon = Icons.Filled.Settings,
            gradientStart = Color(0xFF232526),
            gradientEnd = Color(0xFF414345),
            accentColor = Color(0xFFB0BEC5),
            onClick = onOpenSettings,
        ),
    )

    val totalItems = tiles.size * 2000
    val startIndex = tiles.size * 1000
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Classic iPod / iTunes dark reflective stage backdrop
    val stageBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            Color(0xFF0A0C10),
            Color(0xFF050608),
        ),
    )

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
                            text = "Cover Flow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color(0xFF08090C),
    ) { padding ->
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(stageBackground)
                .padding(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        ) {
            if (isVertical) {
                val cardWidth = (maxWidth * 0.80f).coerceIn(240.dp, 340.dp)
                val cardHeight = (maxWidth * 0.62f).coerceIn(185.dp, 240.dp)
                val verticalPad = ((maxHeight - cardHeight) / 2).coerceAtLeast(32.dp)
                val overlapSpacing = (-56).dp

                LazyColumn(
                    state = listState,
                    flingBehavior = snapFlingBehavior,
                    contentPadding = PaddingValues(vertical = verticalPad),
                    verticalArrangement = Arrangement.spacedBy(overlapSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(count = totalItems, key = { it }) { index ->
                        val tile = tiles[index % tiles.size]
                        val layoutInfo = listState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.key == index }
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter
                        val stepPx = (itemInfo?.size?.toFloat() ?: with(density) { cardHeight.toPx() }).coerceAtLeast(1f) * 0.72f
                        val normalized = ((itemCenter - viewportCenter) / stepPx).coerceIn(-4f, 4f)
                        val absNorm = abs(normalized)
                        val clamped = normalized.coerceIn(-1f, 1f)

                        // iPod Cover Flow curve: center cover sits flat (0°), adjacent covers part & tilt sharply (~54°)
                        val tiltAngle = -clamped * 54f
                        val spreadShiftPx = with(density) { (clamped * 34.dp.toPx()) }
                        val scale = (1f - (absNorm.coerceAtMost(1f) * 0.14f) - ((absNorm - 1f).coerceAtLeast(0f) * 0.04f))
                            .coerceIn(0.68f, 1f)
                        val alpha = (1f - absNorm * 0.22f).coerceIn(0.32f, 1f)
                        val zOrder = 100f - absNorm * 10f

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .zIndex(zOrder)
                                .graphicsLayer {
                                    rotationX = tiltAngle
                                    translationY = spreadShiftPx
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                    cameraDistance = 18f * density.density
                                },
                        ) {
                            CoverFlowSleeveCard(
                                tile = tile,
                                width = cardWidth,
                                height = cardHeight,
                                isCentered = absNorm < 0.45f,
                                onClick = {
                                    if (absNorm < 0.55f) {
                                        tile.onClick()
                                    } else {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                val cardSize = (maxHeight * 0.64f).coerceIn(170.dp, 240.dp)
                val reflectionHeight = (cardSize * 0.28f).coerceIn(40.dp, 68.dp)
                val horizontalPad = ((maxWidth - cardSize) / 2).coerceAtLeast(32.dp)
                val overlapSpacing = (-64).dp

                LazyRow(
                    state = listState,
                    flingBehavior = snapFlingBehavior,
                    contentPadding = PaddingValues(horizontal = horizontalPad),
                    horizontalArrangement = Arrangement.spacedBy(overlapSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(count = totalItems, key = { it }) { index ->
                        val tile = tiles[index % tiles.size]
                        val layoutInfo = listState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.key == index }
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        val itemCenter = itemInfo?.let { it.offset + it.size / 2f } ?: viewportCenter
                        val stepPx = (itemInfo?.size?.toFloat() ?: with(density) { cardSize.toPx() }).coerceAtLeast(1f) * 0.68f
                        val normalized = ((itemCenter - viewportCenter) / stepPx).coerceIn(-4f, 4f)
                        val absNorm = abs(normalized)
                        val clamped = normalized.coerceIn(-1f, 1f)

                        // Classic iTunes / iPod Cover Flow:
                        // Left covers (normalized < 0) rotate +58° around Y; right covers (normalized > 0) rotate -58°
                        val tiltAngle = -clamped * 58f
                        val spreadShiftPx = with(density) {
                            (clamped * 42.dp.toPx()) + (sign(normalized) * (absNorm - 1f).coerceAtLeast(0f) * 6.dp.toPx())
                        }
                        val scale = (1f - (absNorm.coerceAtMost(1f) * 0.15f) - ((absNorm - 1f).coerceAtLeast(0f) * 0.03f))
                            .coerceIn(0.68f, 1f)
                        val alpha = (1f - absNorm * 0.20f).coerceIn(0.32f, 1f)
                        val zOrder = 100f - absNorm * 10f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .zIndex(zOrder)
                                .graphicsLayer {
                                    rotationY = tiltAngle
                                    translationX = spreadShiftPx
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                    cameraDistance = 18f * density.density
                                },
                        ) {
                            CoverFlowSleeveCard(
                                tile = tile,
                                width = cardSize,
                                height = cardSize,
                                isCentered = absNorm < 0.45f,
                                onClick = {
                                    if (absNorm < 0.55f) {
                                        tile.onClick()
                                    } else {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                },
                            )
                            Spacer(Modifier.height(3.dp))
                            CoverFlowReflection(
                                tile = tile,
                                width = cardSize,
                                reflectionHeight = reflectionHeight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverFlowSleeveCard(
    tile: HomeTileItem,
    width: Dp,
    height: Dp,
    isCentered: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val sleeveBrush = Brush.linearGradient(
        colors = listOf(tile.gradientStart, tile.gradientEnd),
        start = Offset.Zero,
        end = Offset(600f, 600f),
    )
    // Glossy diagonal vinyl/glass sheen like iPod Classic / iTunes Cover Flow
    val glossBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isCentered) 0.24f else 0.14f),
            Color.White.copy(alpha = 0.05f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.28f),
        ),
        start = Offset(0f, 0f),
        end = Offset(500f, 550f),
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        contentColor = Color.White,
        shadowElevation = if (isCentered) 20.dp else 8.dp,
        border = BorderStroke(
            width = if (isCentered) 1.5.dp else 1.dp,
            color = Color.White.copy(alpha = if (isCentered) 0.38f else 0.16f),
        ),
        modifier = Modifier.size(width = width, height = height),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(sleeveBrush)
                .background(glossBrush)
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    if (tile.badge != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.32f),
                            border = BorderStroke(0.5.dp, tile.accentColor.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = tile.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = tile.accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = tile.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverFlowReflection(
    tile: HomeTileItem,
    width: Dp,
    reflectionHeight: Dp,
) {
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val reflectionBrush = Brush.verticalGradient(
        colors = listOf(
            tile.gradientEnd.copy(alpha = 0.32f),
            tile.gradientStart.copy(alpha = 0.10f),
            Color.Transparent,
        ),
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(reflectionHeight)
            .clip(shape)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .background(reflectionBrush),
    )
}
