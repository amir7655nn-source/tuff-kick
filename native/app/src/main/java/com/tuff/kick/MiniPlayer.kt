package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/** The stream that keeps playing in a small floating window while the viewer browses the rest of the app. */
object MiniPlayer {
    class Item(val slug: String, val url: String)

    var item by mutableStateOf<Item?>(null)
        private set

    fun show(slug: String, url: String, live: Boolean) {
        item = if (live && url.isNotEmpty()) Item(slug, url) else null
    }

    fun close() {
        item = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerWindow(it: MiniPlayer.Item, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var off by remember { mutableStateOf(IntOffset.Zero) }
    var playing by remember { mutableStateOf(true) }
    val player = remember(it.slug) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/"))
        ExoPlayer.Builder(ctx).setMediaSourceFactory(HlsMediaSource.Factory(http)).build()
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, it.url) {
        player.setMediaItem(MediaItem.fromUri(it.url))
        player.prepare()
        player.playWhenReady = true
    }
    Box(
        modifier.offset { off }.width(190.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    off = IntOffset(off.x + drag.x.roundToInt(), off.y + drag.y.roundToInt())
                }
            }
            .clickable { onOpen(it.slug) },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c -> PlayerView(c).apply { useController = false; this.player = player } },
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0x99000000))
                .clickable { MiniPlayer.close() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), "Close", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0x99000000))
                .clickable { if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), "Play/Pause", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
