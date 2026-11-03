package com.tuff.kick

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The live chat of a channel shown directly inside another screen (the Chat tab of a channel page).
 * It shares the chat connection with the watch page (same view model), so opening the stream from here keeps the chat.
 */
@Composable
fun InlineChat(slug: String, loggedIn: Boolean, onLogin: () -> Unit, onProfile: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm: WatchViewModel = viewModel(key = "watch_$slug", factory = WatchFactory(slug))
    val info by vm.info.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val emotes by vm.emotes.collectAsStateWithLifecycle()
    val reply by vm.reply.collectAsStateWithLifecycle()
    val sendError by vm.sendError.collectAsStateWithLifecycle()
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val prediction by vm.prediction.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }       // bumped when a chat setting changes
    var sheet by remember { mutableIntStateOf(0) }      // 0 none, 4 chat settings, 7 points, 8 leaderboard
    var modMsg by remember { mutableStateOf<ChatMsg?>(null) }
    var userMsg by remember { mutableStateOf<ChatMsg?>(null) }
    var points by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(slug) { vm.attach() }
    DisposableEffect(slug) { onDispose { vm.detach() } }
    LaunchedEffect(slug, loggedIn) { points = if (loggedIn) try { Browse.myPoints(slug) } catch (e: Exception) { null } else null }

    val textLevel = remember(tick) { prefs.getInt("chat_text", 1) }
    val showTimes = remember(tick) { prefs.getBoolean("chat_times", false) }
    val emoteLevel = remember(tick) { prefs.getInt("chat_emote", 1).coerceIn(0, 3) }
    val quick = remember(tick) { prefs.getBoolean("chat_quick", false) }
    val muted = remember(tick) { prefs.getStringSet("chat_muted", emptySet()) ?: emptySet() }

    Box(modifier) {
        when (val i = info) {
            is Load.Idle, is Load.Loading -> SkeletonChat()
            is Load.Fail -> Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not load chat", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(i.message.take(80), color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = { vm.stop(); vm.start() },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
                ) { Text("Retry", fontWeight = FontWeight.Bold) }
            }
            is Load.Ok -> {
                val ci = i.data
                ChatPanel(
                    modifier = Modifier.fillMaxSize(), ci = ci,
                    messages = if (muted.isEmpty()) messages else messages.filter { it.name.lowercase() !in muted },
                    status = status, connected = connected, emotes = emotes,
                    reply = reply, sendError = sendError, loggedIn = loggedIn,
                    textSize = when (textLevel) { 0 -> 12f; 2 -> 17f; else -> 14.5f },
                    showTimes = showTimes, quickEmotes = quick, gifters = null, points = points,
                    pinned = pinned, prediction = prediction,
                    emoteScale = EMOTE_SCALES[emoteLevel],
                    onUserClick = { userMsg = it },
                    onViewPrediction = { openUrl(ctx, "https://kick.com/" + ci.slug) },
                    onLogin = onLogin,
                    onReply = { vm.setReply(it) },
                    onSend = { vm.send(it) },
                    onClearError = { vm.clearError() },
                    onPoints = { if (!loggedIn) onLogin() else sheet = 7 },
                    onKicks = { openUrl(ctx, "https://kick.com/" + ci.slug) },
                    onPeople = { sheet = 8 },
                    onChatSettings = { sheet = 4 },
                    onLongMsg = { modMsg = it },
                )
            }
        }
    }

    // sheets sit in their own window so they cover the whole screen, not only the small chat area
    val um = userMsg
    if (um != null) FullSheet(onDismiss = { userMsg = null }) {
        UserSheet(
            um, slug, loggedIn, onLogin,
            onViewChannel = onProfile,
            onMute = { prefs.edit().putStringSet("chat_muted", muted + um.name.lowercase()).apply(); tick++ },
            onClose = { userMsg = null },
        )
    }
    val mm = modMsg
    if (mm != null) FullSheet(onDismiss = { modMsg = null }) {
        Sheet(mm.name, onClose = { modMsg = null }) {
            Text(
                "Badges: " + (if (mm.badges.isEmpty()) "none" else mm.badges.joinToString(", ") { it.type + (if (it.count > 0) ":" + it.count else "") + (if (it.url != null) "+img" else "") }),
                color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            OptionRow("Reply") { vm.setReply(mm); modMsg = null }
            OptionRow("Mute " + mm.name, "Hide their messages on this device") {
                prefs.edit().putStringSet("chat_muted", muted + mm.name.lowercase()).apply(); tick++; modMsg = null
            }
        }
    }
    if (sheet == 4) FullSheet(onDismiss = { sheet = 0 }) {
        Sheet("Chat Settings", onClose = { sheet = 0 }) {
            OptionRow("Text size", trailing = when (textLevel) { 0 -> "Small"; 2 -> "Large"; else -> "Normal" }) {
                prefs.edit().putInt("chat_text", (textLevel + 1) % 3).apply(); tick++
            }
            OptionRow("Emote size", trailing = EMOTE_LABELS[emoteLevel]) {
                prefs.edit().putInt("chat_emote", (emoteLevel + 1) % 4).apply(); tick++
            }
            ToggleRow("Animated emotes in picker", remember(tick) { prefs.getBoolean("picker_anim", true) }) {
                prefs.edit().putBoolean("picker_anim", it).apply(); tick++
            }
            ToggleRow("Show quick emotes", quick) { prefs.edit().putBoolean("chat_quick", it).apply(); tick++ }
            ToggleRow("Show message timestamps", showTimes) { prefs.edit().putBoolean("chat_times", it).apply(); tick++ }
        }
    }
    if (sheet == 7) FullSheet(onDismiss = { sheet = 0 }) { PointsSheet(slug, points, onClose = { sheet = 0 }, onBalance = { points = it }) }
    if (sheet == 8) FullSheet(onDismiss = { sheet = 0 }) { LeaderboardSheet(slug, onClose = { sheet = 0 }) }
}

@Composable
private fun FullSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
