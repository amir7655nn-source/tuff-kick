package com.tuff.kick

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amazonaws.ivs.player.Cue
import com.amazonaws.ivs.player.Player
import com.amazonaws.ivs.player.PlayerException
import com.amazonaws.ivs.player.PlayerView
import com.amazonaws.ivs.player.Quality
import androidx.media3.common.C

/**
 * Same screen as PlayerBox, but the video is played by the Amazon IVS player (Kick streams on IVS).
 * It keeps the live delay at its minimum (about 2 to 4 s) without the buffering ExoPlayer shows.
 */
@Composable
fun PlayerBoxIvs(
    ci: ChannelInfo, modifier: Modifier, landscape: Boolean, quality: Int, onHeights: (List<Int>) -> Unit,
    onBack: () -> Unit, onRotate: () -> Unit, onShare: () -> Unit, onSettings: () -> Unit,
    onToggleChat: (() -> Unit)? = null,
    chatShown: Boolean = true,
) {
    val ctx = LocalContext.current
    var muted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var liveOff by remember { mutableStateOf(C.TIME_UNSET) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var heights by remember { mutableStateOf(listOf<Int>()) }
    var reload by remember { mutableStateOf(0) }
    var readyTick by remember { mutableStateOf(0) }

    val view = remember(ci.slug, reload) { PlayerView(ctx).apply { setControlsEnabled(false) } }
    val player: Player = remember(view) { view.getPlayer() }

    DisposableEffect(player) {
        val l = object : Player.Listener() {
            override fun onCue(cue: Cue) {}
            override fun onDurationChanged(duration: Long) {}
            override fun onRebuffering() {}
            override fun onSeekCompleted(time: Long) {}
            override fun onVideoSizeChanged(width: Int, height: Int) {}
            override fun onQualityChanged(q: Quality) {}
            override fun onError(exception: PlayerException) {
                error = "Playback error: " + exception.getErrorMessage().orEmpty().take(60)
            }

            override fun onStateChanged(state: Player.State) {
                playing = state == Player.State.PLAYING
                if (state == Player.State.READY) {
                    error = ""
                    readyTick++
                    player.play()
                    val hs = ArrayList<Int>()
                    for (q in player.getQualities()) if (q.getHeight() > 0 && !hs.contains(q.getHeight())) hs.add(q.getHeight())
                    heights = hs.sortedDescending()
                    onHeights(heights)
                }
            }
        }
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            player.release()
        }
    }
    LaunchedEffect(player, ci.playbackUrl) {
        if (ci.playbackUrl.isNotEmpty()) {
            try {
                player.setOrigin("https://kick.com")
                player.setLiveLowLatencyEnabled(true)
                player.setRebufferToLive(true)
                player.load(Uri.parse(ci.playbackUrl))
            } catch (e: Exception) {
                error = "Could not start the IVS player"
            }
        }
    }
    LaunchedEffect(muted, player) { player.setMuted(muted) }
    LaunchedEffect(quality, readyTick, player) {
        try {
            val qs = player.getQualities().sortedBy { it.getHeight() }
            if (qs.isEmpty()) return@LaunchedEffect
            when {
                quality == 0 -> player.setAutoQualityMode(true)
                quality == -1 -> player.setQuality(qs.last())
                quality == -2 -> player.setQuality(qs.lastOrNull { it.getHeight() <= 480 } ?: qs.first())
                else -> player.setQuality(qs.firstOrNull { it.getHeight() == quality } ?: qs.last())
            }
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(controls, tick, PlayerUi.ping) {
        if (controls) {
            kotlinx.coroutines.delay(5000)
            controls = false
        }
    }
    LaunchedEffect(controls, player) {
        while (controls) {
            try {
                pos = player.getPosition()
                val d = player.getDuration()
                // a live stream reports no usable length: then the seek bar stays disabled
                dur = if (d <= 0 || d > 6L * 3600_000L) 0L else d
                val lat = player.getLiveLatency()
                liveOff = if (lat > 0) lat else C.TIME_UNSET
            } catch (e: Exception) {
            }
            kotlinx.coroutines.delay(400)
        }
    }

    fun seekTo(t: Long) {
        try {
            player.seekTo(t.coerceAtLeast(0L))
        } catch (e: Exception) {
        }
        tick++
    }

    Box(modifier.background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        controls = !controls
        tick++
    }) {
        if (ci.playbackUrl.isEmpty() || !ci.live) {
            Text(
                if (ci.live) "No stream address" else ci.name + " is offline",
                color = Palette.Muted,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            androidx.compose.runtime.key(view) { AndroidView(modifier = Modifier.fillMaxSize(), factory = { view }) }
        }
        if (error.isNotEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Palette.Text, fontSize = 13.sp)
                Button(
                    onClick = {
                        error = ""
                        try { player.load(Uri.parse(ci.playbackUrl)) } catch (e: Exception) { }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
                ) { Text("Retry", fontWeight = FontWeight.Bold) }
            }
        }
        if (controls) {
            PlayerOverlay(
                landscape = landscape, viewers = ci.viewers, playing = playing, muted = muted,
                pos = pos, dur = dur, liveOff = liveOff, dragFrac = dragFrac,
                onDrag = { dragFrac = it; tick++ },
                onDragEnd = {
                    val f = dragFrac
                    dragFrac = null
                    if (f != null && dur > 0) seekTo((f * dur).toLong())
                },
                onBack = onBack, onShare = onShare, onSettings = onSettings, onRotate = onRotate,
                onMute = { muted = !muted },
                onPlayPause = { if (playing) player.pause() else player.play(); tick++ },
                onSkip = { d -> seekTo(player.getPosition() + d) },
                onLive = { try { player.seekTo(player.getDuration()) } catch (e: Exception) { }; tick++ },
                onRefresh = { error = ""; playing = false; reload++; tick++ },
                onToggleChat = onToggleChat, chatShown = chatShown,
            )
        }
    }
}
