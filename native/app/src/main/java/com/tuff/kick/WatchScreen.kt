package com.tuff.kick

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

private fun parseColor(s: String): Color = try {
    Color(android.graphics.Color.parseColor(s))
} catch (e: Exception) {
    Color(0xFFC9CED3)
}

fun badgeModel(b: Badge): Any? {
    // a badge that Kick gives an image for is shown with Kick's own image; our bundled artwork is only the fallback
    if (!b.url.isNullOrEmpty()) return b.url
    if (b.type == "subscriber") return null
    val t = b.type.lowercase().replace("_", "").replace("-", "")
    val file = when {
        t == "broadcaster" -> "broadcaster.svg"
        t == "moderator" -> "moderator.png"
        t == "vip" -> "vip.png"
        t == "og" -> "og.png"
        t == "founder" -> "founder.png"
        t == "subgifter" -> "subGifter.svg"
        t == "staff" -> "staff.svg"
        t == "sidekick" -> "sidekick.svg"
        t == "verified" -> "verified.png"
        t.contains("flyby") -> "flyby.png"
        t.contains("goat") -> "goat.png"
        t.contains("golden") -> "goldenk.png"
        else -> return b.url?.takeIf { it.isNotEmpty() }   // any other badge Kick gives an image for
    }
    return "file:///android_asset/badges/$file"
}

@Composable
fun WatchScreen(
    slug: String,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onProfile: (String) -> Unit,
    onBack: () -> Unit,
) {
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current as? Activity
    val ctx = LocalContext.current

    var modMsg by remember { mutableStateOf<ChatMsg?>(null) }
    var userMsg by remember { mutableStateOf<ChatMsg?>(null) }
    var sheet by remember { mutableStateOf(0) }          // 0 none, 1 settings, 2 quality, 3 advanced, 4 chat settings
    var quality by remember { mutableStateOf(0) }        // 0 auto, -1 crisp, -2 lite, >0 height
    var heights by remember { mutableStateOf(listOf<Int>()) }
    val prefs = remember { ctx.getSharedPreferences("tuff", android.content.Context.MODE_PRIVATE) }
    var textSize by remember { mutableStateOf(prefs.getInt("chat_text", 1)) }       // 0 small, 1 normal, 2 large
    var chatOnly by remember { mutableStateOf(false) }
    var showLb by remember { mutableStateOf(prefs.getBoolean("chat_lb", false)) }
    var quick by remember { mutableStateOf(prefs.getBoolean("chat_quick", false)) }
    var lowPower by remember { mutableStateOf(prefs.getBoolean("chat_low", false)) }
    var tick2 by remember { mutableStateOf(0) }
    var useIvs by remember { mutableStateOf(prefs.getBoolean("use_ivs", true)) }
    var lowDelay by remember { mutableStateOf(prefs.getBoolean("low_delay", true)) }
    var minDelay by remember { mutableStateOf(prefs.getBoolean("min_delay", false)) }
    var emoteLevel by remember { mutableStateOf(prefs.getInt("chat_emote", 1)) }     // 0 small, 1 normal, 2 large, 3 huge
    var showTimes by remember { mutableStateOf(prefs.getBoolean("chat_times", false)) }
    var muted by remember { mutableStateOf(prefs.getStringSet("chat_muted", emptySet()) ?: emptySet()) }
    var points by remember { mutableStateOf<Int?>(null) }
    var gifters by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    fun savePref(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    fun setMuted(s: Set<String>) { muted = s; prefs.edit().putStringSet("chat_muted", s).apply() }
    var following by remember { mutableStateOf<Boolean?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(slug) { vm.attach() }
    LaunchedEffect(slug, loggedIn) { following = if (loggedIn) Browse.isFollowing(slug) else null }
    LaunchedEffect(slug, loggedIn) { points = if (loggedIn) try { Browse.myPoints(slug) } catch (e: Exception) { null } else null }
    LaunchedEffect(slug, showLb) { gifters = if (showLb) try { Browse.topGifters(slug) } catch (e: Exception) { emptyList() } else null }
    DisposableEffect(slug) { onDispose { vm.detach() } }
    BackHandler {
        if (sheet != 0) sheet = 0
        else {
            if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        when (val i = info) {
            is Load.Idle, is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding()) { SkeletonWatch() }
            is Load.Fail -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Could not open $slug", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(i.message.take(80), color = Palette.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                Button(
                    onClick = { vm.stop(); vm.start() },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
                ) { Text("Try again", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Raised, contentColor = Palette.Text),
                ) { Text("Back") }
            }
            is Load.Ok -> {
                val ci = i.data
                val player: @Composable (Modifier) -> Unit = { m ->
                    val goBack = {
                        if (landscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        onBack()
                    }
                    if (useIvs) PlayerBoxIvs(
                        ci, m, landscape, quality, { heights = it },
                        onBack = goBack,
                        onRotate = {
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onShare = { shareText(ctx, "https://kick.com/" + ci.slug) },
                        onSettings = { sheet = 1 },
                    ) else PlayerBox(
                        ci, m, landscape, quality, { heights = it },
                        onBack = goBack,
                        onRotate = {
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onShare = { shareText(ctx, "https://kick.com/" + ci.slug) },
                        onSettings = { sheet = 1 },
                        lowDelay = lowDelay,
                        minDelay = minDelay,
                    )
                }
                val chat: @Composable (Modifier) -> Unit = { m ->
                    ChatPanel(
                        modifier = m, ci = ci,
                        messages = (if (muted.isEmpty()) messages else messages.filter { it.name.lowercase() !in muted }).let { if (lowPower) it.takeLast(80) else it },
                        status = status, connected = connected, emotes = emotes,
                        reply = reply, sendError = sendError, loggedIn = loggedIn,
                        textSize = when (textSize) { 0 -> 12f; 2 -> 17f; else -> 14.5f },
                        showTimes = showTimes, quickEmotes = quick, gifters = gifters, points = points,
                        pinned = pinned, prediction = prediction,
                        emoteScale = EMOTE_SCALES[emoteLevel.coerceIn(0, 3)],
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
                if (landscape) {
                    Row(Modifier.fillMaxSize().background(Palette.Ink)) {
                        player(Modifier.weight(1.6f).fillMaxHeight())
                        chat(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize().background(Palette.Ink).statusBarsPadding()) {
                        if (!chatOnly) player(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                        else Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Color.White) }
                            Text(ci.name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        }
                        chat(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        val um = userMsg
        if (um != null) {
            UserSheet(
                um, slug, loggedIn, onLogin,
                onViewChannel = { onProfile(it) },
                onMute = { setMuted(muted + um.name.lowercase()) },
                onClose = { userMsg = null },
            )
        }
        val mm = modMsg
        val infoNow = info
        if (mm != null && infoNow is Load.Ok) {
            val ci2 = infoNow.data
            val uid = mm.userId.toLongOrNull() ?: 0L
            fun act(okMsg: String, block: suspend () -> Unit) {
                modMsg = null
                scope.launch {
                    val t = try { block(); okMsg } catch (e: KickException) { "Failed: " + e.code.take(80) } catch (e: Exception) { "Failed" }
                    android.widget.Toast.makeText(ctx, t, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            Sheet(mm.name, onClose = { modMsg = null }) {
                Text(
                    "Badges: " + (if (mm.badges.isEmpty()) "none" else mm.badges.joinToString(", ") { it.type + (if (it.count > 0) ":" + it.count else "") + (if (it.url != null) "+img" else "") }),
                    color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                OptionRow("Reply") { vm.setReply(mm); modMsg = null }
                OptionRow("Mute " + mm.name, "Hide their messages on this device") { setMuted(muted + mm.name.lowercase()); modMsg = null }
                if (KickOAuth.linked()) {
                OptionRow("Delete message", "Needs moderator rights") { act("Message deleted") { PublicApi.deleteMessage(mm.id) } }
                OptionRow("Timeout 1 minute") { act("Timed out 1 min") { PublicApi.ban(ci2.userId, uid, 1) } }
                OptionRow("Timeout 10 minutes") { act("Timed out 10 min") { PublicApi.ban(ci2.userId, uid, 10) } }
                OptionRow("Timeout 1 hour") { act("Timed out 1 hour") { PublicApi.ban(ci2.userId, uid, 60) } }
                OptionRow("Ban permanently") { act("Banned") { PublicApi.ban(ci2.userId, uid, null) } }
                OptionRow("Unban / remove timeout") { act("Unbanned") { PublicApi.unban(ci2.userId, uid) } }
                }
            }
        }
        if (sheet == 1) {
            Sheet("Settings", onClose = { sheet = 0 }) {
                OptionRow(
                    "Quality",
                    trailing = when (quality) { 0 -> "Auto"; -1 -> "Crisp"; -2 -> "Lite"; else -> quality.toString() + "p" },
                ) { sheet = 2 }
                ToggleRow("Amazon IVS player (best live delay)", useIvs) { useIvs = it; prefs.edit().putBoolean("use_ivs", it).apply() }
                ToggleRow("Low delay (about 4 s behind live, adapts)", lowDelay) { lowDelay = it; prefs.edit().putBoolean("low_delay", it).apply() }
                ToggleRow("Minimum delay (about 1 s, may buffer)", minDelay) { minDelay = it; prefs.edit().putBoolean("min_delay", it).apply() }
                OptionRow("Open channel profile") { sheet = 0; onProfile(slug) }
                OptionRow(if (following == true) "Following" else "Follow", checked = following == true) {
                    sheet = 0
                    if (!loggedIn) onLogin() else {
                        val want = !(following ?: false)
                        following = want
                        scope.launch { try { Browse.follow(slug, want) } catch (e: Exception) { following = !want } }
                    }
                }
                OptionRow("Subscribe / Gift a Sub") { openUrl(ctx, "https://kick.com/" + slug); sheet = 0 }
                OptionRow("Report livestream") { openUrl(ctx, "https://kick.com/" + slug); sheet = 0 }
            }
        }
        if (sheet == 2) {
            Sheet("Quality", onClose = { sheet = 0 }, onBack = { sheet = 1 }) {
                OptionRow("Auto (recommended)", "Auto adjust for best experience", quality == 0) { quality = 0; sheet = 0 }
                OptionRow("Crisp Mode", "Looks great, use more data", quality == -1) { quality = -1; sheet = 0 }
                OptionRow("Lite Mode", "Reduces quality slightly to use less data", quality == -2) { quality = -2; sheet = 0 }
                OptionRow("Advanced", "Select a specific resolution", quality > 0) { sheet = 3 }
            }
        }
        if (sheet == 3) {
            Sheet("Advanced", onClose = { sheet = 0 }, onBack = { sheet = 2 }) {
                if (heights.isEmpty()) Text("Resolutions appear once the stream is playing.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                heights.forEach { h -> OptionRow(h.toString() + "p", checked = quality == h) { quality = h; sheet = 0 } }
            }
        }
        if (sheet == 4) {
            Sheet("Chat Settings", onClose = { sheet = 0 }) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OptionRow("Text size", trailing = when (textSize) { 0 -> "Small"; 2 -> "Large"; else -> "Normal" }) {
                        textSize = (textSize + 1) % 3; prefs.edit().putInt("chat_text", textSize).apply()
                    }
                    OptionRow("Emote size", trailing = EMOTE_LABELS[emoteLevel.coerceIn(0, 3)]) {
                        emoteLevel = (emoteLevel + 1) % 4; prefs.edit().putInt("chat_emote", emoteLevel).apply()
                    }
                    ToggleRow("Animated emotes in picker", remember(tick2) { prefs.getBoolean("picker_anim", true) }) {
                        prefs.edit().putBoolean("picker_anim", it).apply(); tick2++
                    }
                    ToggleRow("Chat only", chatOnly) { chatOnly = it }
                    ToggleRow("Show leaderboard", showLb) { showLb = it; savePref("chat_lb", it) }
                    ToggleRow("Show quick emotes", quick) { quick = it; savePref("chat_quick", it) }
                    ToggleRow("Low power mode", lowPower) { lowPower = it; savePref("chat_low", it) }
                    ToggleRow("Show message timestamps", showTimes) { showTimes = it; savePref("chat_times", it) }
                    OptionRow("Muted users", trailing = if (muted.isEmpty()) "" else muted.size.toString()) { sheet = 5 }
                    OptionRow("Identity") { sheet = 6 }
                }
            }
        }
        if (sheet == 5) {
            Sheet("Muted users", onClose = { sheet = 0 }, onBack = { sheet = 4 }) {
                if (muted.isEmpty()) Text("Nobody is muted. Long-press a message to mute someone.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    muted.sorted().forEach { n -> OptionRow(n, trailing = "Unmute") { setMuted(muted - n) } }
                }
            }
        }
        if (sheet == 6) {
            Sheet("Identity", onClose = { sheet = 0 }, onBack = { sheet = 4 }) {
                Text("Your chat name, color and badges are part of your Kick account.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                OptionRow("Open identity settings on Kick") { openUrl(ctx, "https://kick.com/dashboard/settings/profile"); sheet = 0 }
            }
        }
        if (sheet == 7) PointsSheet(slug, points, onClose = { sheet = 0 }, onBalance = { points = it })
        if (sheet == 8) LeaderboardSheet(slug, onClose = { sheet = 0 })
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Raised)
            .clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.AccentInk),
        )
    }
}

@Composable
fun PointsSheet(slug: String, balance: Int?, onClose: () -> Unit, onBalance: (Int?) -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val l = rememberLoader("vrewards", slug, tick) { Browse.viewerRewards(slug) }
    var pick by remember { mutableStateOf<Browse.ViewerReward?>(null) }
    var input by remember { mutableStateOf("") }
    Sheet("Channel points", onClose = onClose) {
        Text(
            if (balance != null) "Your balance: " + balance + " points" else "Balance unavailable",
            color = Palette.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        val p = pick
        if (p != null) {
            Text(p.title, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(8.dp))
            if (p.description.isNotEmpty()) Text(p.description, color = Palette.Muted, modifier = Modifier.padding(horizontal = 8.dp))
            if (p.needsInput) TextField(
                value = input, onValueChange = { input = it }, placeholder = { Text("Your message") },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Palette.Accent,
                ),
            )
            Button(
                onClick = {
                    scope.launch {
                        val msg = try {
                            Browse.redeem(slug, p.id, input)
                            onBalance(try { Browse.myPoints(slug) } catch (e: Exception) { null })
                            "Redeemed"
                        } catch (e: KickException) { e.code.take(100) } catch (e: Exception) { "Failed" }
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                        pick = null; input = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
            ) { Text("Redeem for " + p.cost, fontWeight = FontWeight.Bold) }
        } else {
            Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp).verticalScroll(rememberScrollState())) {
                LoadView(l) { list ->
                    if (list.isEmpty()) Text("This channel has no rewards.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                    list.forEach { r ->
                        OptionRow(r.title, r.description, trailing = r.cost.toString() + if (r.enabled) "" else " · paused") { if (r.enabled) pick = r }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardSheet(slug: String, onClose: () -> Unit) {
    val l = rememberLoader("gifters", slug) { Browse.topGifters(slug) }
    Sheet("Leaderboard", onClose = onClose) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp).verticalScroll(rememberScrollState())) {
            LoadView(l) { list ->
                if (list.isEmpty()) Text("No gifts yet.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                list.forEachIndexed { i, g -> OptionRow("#" + (i + 1) + "  " + g.first, trailing = g.second.toString() + " KICKs") { } }
            }
        }
    }
}

// ----------------------------------------------------------------- player --
@OptIn(UnstableApi::class)
@Composable
fun PlayerBox(
    ci: ChannelInfo, modifier: Modifier, landscape: Boolean, quality: Int, onHeights: (List<Int>) -> Unit,
    onBack: () -> Unit, onRotate: () -> Unit, onShare: () -> Unit, onSettings: () -> Unit,
    lowDelay: Boolean = true,
    minDelay: Boolean = false,
) {
    val ctx = LocalContext.current
    var heights by remember { mutableStateOf(listOf<Int>()) }
    var muted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    // seek bar state
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var liveOff by remember { mutableStateOf(C.TIME_UNSET) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var stalls by remember { mutableStateOf(0) }            // rebuffers seen: each one raises the safe distance from live
    var wasReady by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }            // refresh button: a new player, same quality (quality lives in the parent)
    var shifted by remember { mutableStateOf(false) }       // the viewer went back on purpose: do not catch up to live

    val player = remember(ci.slug, lowDelay, minDelay, reload) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/"))
        val b = ExoPlayer.Builder(ctx).setMediaSourceFactory(HlsMediaSource.Factory(http))
        // small buffers: start playing after ~0.8 s of data and keep only a few seconds ahead
        if (lowDelay) {
            val lc = DefaultLoadControl.Builder()
            if (minDelay) lc.setBufferDurationsMs(1200, 6000, 400, 700) else lc.setBufferDurationsMs(4000, 15000, 1500, 2500)
            b.setLoadControl(lc.build())
        }
        b.build()
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val hs = ArrayList<Int>()
                for (g in tracks.groups) {
                    if (g.type == C.TRACK_TYPE_VIDEO) {
                        for (k in 0 until g.length) {
                            val h = g.getTrackFormat(k).height
                            if (h > 0 && !hs.contains(h)) hs.add(h)
                        }
                    }
                }
                heights = hs.sortedDescending()
                onHeights(heights)
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Playback error (" + e.errorCodeName + ")"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { error = ""; wasReady = true }
                else if (state == Player.STATE_BUFFERING && wasReady) { stalls++; wasReady = false }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
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
            val item = MediaItem.Builder().setUri(ci.playbackUrl).apply {
                if (lowDelay) {
                    // sit 1.5 s behind the live edge (never more than 4 s); catching up is done below, not by ExoPlayer
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(if (minDelay) 1000 else 3000).setMinOffsetMs(if (minDelay) 500 else 2000).setMaxOffsetMs(if (minDelay) 2500 else 7000)
                            .setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()
                    )
                }
            }.build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        }
    }
    // keep the delay small: after a stall or a rebuffer play slightly faster until close to live again
    LaunchedEffect(player, lowDelay, minDelay) {
        if (!lowDelay) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            val off = player.currentLiveOffset
            if (off == C.TIME_UNSET || !player.isPlaying) continue
            // never go closer to live than a safe floor; every stall moves the floor 1.5 s further away
            val floor = if (minDelay) 1000L else (4000L + 1500L * stalls).coerceAtMost(10000L)
            if (off < floor + 1500) shifted = false
            val ahead = player.bufferedPosition - player.currentPosition
            if (!shifted && off > floor + (if (minDelay) 4000 else 9000)) { player.seekToDefaultPosition(); continue }
            val speed = when {
                shifted || ahead < (if (minDelay) 700 else 3000) -> 1f
                minDelay && off > floor + 1000 -> 1.25f
                minDelay && off > floor + 300 -> 1.1f
                off > floor + 3000 -> 1.06f
                off > floor + 500 -> 1.03f
                else -> 1f
            }
            if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
        }
    }
    LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(quality, heights, player) {
        val b = player.trackSelectionParameters.buildUpon()
        b.setForceHighestSupportedBitrate(quality == -1)
        when {
            quality == 0 || heights.isEmpty() && quality < 0 -> b.clearVideoSizeConstraints()
            quality == -1 -> b.setMaxVideoSize(Int.MAX_VALUE, heights.max())
            quality == -2 -> b.setMaxVideoSize(Int.MAX_VALUE, heights.filter { it <= 480 }.maxOrNull() ?: heights.min())
            else -> b.setMaxVideoSize(Int.MAX_VALUE, quality)
        }
        player.trackSelectionParameters = b.build()
    }
    // hide the controls a few seconds after the last touch
    LaunchedEffect(controls, tick) {
        if (controls) {
            kotlinx.coroutines.delay(5000)
            controls = false
        }
    }
    // read position / length of the seekable window while the controls are visible
    LaunchedEffect(controls, player) {
        while (controls) {
            pos = player.currentPosition
            val d = player.duration
            dur = if (d == C.TIME_UNSET || d < 0) 0L else d
            liveOff = player.currentLiveOffset
            kotlinx.coroutines.delay(400)
        }
    }

    fun seekTo(target: Long) {
        val t = target.coerceAtLeast(0L)
        if (dur > 0 && t >= dur - 1500) {
            player.seekToDefaultPosition()
            shifted = false
        } else {
            player.seekTo(t)
            if (dur > 0 && dur - t > 4000) shifted = true
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = player },
            )
        }
        if (error.isNotEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Palette.Text, fontSize = 13.sp)
                Button(
                    onClick = { error = ""; player.prepare(); player.playWhenReady = true },
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
                onPlayPause = { if (player.isPlaying) player.pause() else player.play(); tick++ },
                onSkip = { d -> seekTo(player.currentPosition + d) },
                onLive = { shifted = false; player.seekToDefaultPosition(); tick++ },
                onRefresh = { error = ""; stalls = 0; wasReady = false; shifted = false; reload++; tick++ },
            )
        }
    }
}

/** Controls drawn over the video; shared by the ExoPlayer and the IVS player. */
@Composable
fun BoxScope.PlayerOverlay(
    landscape: Boolean, viewers: Int, playing: Boolean, muted: Boolean,
    pos: Long, dur: Long, liveOff: Long, dragFrac: Float?,
    onDrag: (Float) -> Unit, onDragEnd: () -> Unit,
    onBack: () -> Unit, onShare: () -> Unit, onSettings: () -> Unit, onRotate: () -> Unit,
    onMute: () -> Unit, onPlayPause: () -> Unit, onSkip: (Long) -> Unit, onLive: () -> Unit, onRefresh: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000)))
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(if (landscape) R.drawable.ic_arrow_left else R.drawable.ic_chevron_down), "Back", tint = Color.White)
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = onRefresh) { Icon(painterResource(R.drawable.ic_refresh), "Refresh player", tint = Color.White, modifier = Modifier.size(22.dp)) }
        IconButton(onClick = onShare) { Icon(painterResource(R.drawable.ic_share), "Share", tint = Color.White, modifier = Modifier.size(22.dp)) }
        IconButton(onClick = { onSettings() }) { Icon(painterResource(R.drawable.ic_gear), "Settings", tint = Color.White) }
    }
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        SkipButton(forward = false) { onSkip(-10_000L) }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color(0x66000000)).clickable { onPlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play), "Play/Pause", tint = Color.White, modifier = Modifier.size(38.dp))
        }
        SkipButton(forward = true) { onSkip(10_000L) }
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
        val atLive = liveOff == C.TIME_UNSET || liveOff < 4000
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (atLive) Palette.Live else Color(0x88000000))
                    .clickable { onLive() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    if (atLive) (if (liveOff != C.TIME_UNSET) "LIVE " + (liveOff / 1000) + "s" else "LIVE") else "-" + fmtDur((liveOff / 1000).toInt()),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ViewerBadge(viewers)
            Box(Modifier.weight(1f))
            IconButton(onClick = onMute) {
                Icon(painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume), "Sound", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onRotate) {
                Icon(painterResource(R.drawable.ic_fullscreen), "Rotate", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 10 second skip button: arrow with "10" in it. */
@Composable
internal fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x66000000)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(painterResource(if (forward) R.drawable.ic_forward else R.drawable.ic_replay), if (forward) "Forward 10 seconds" else "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
        Text("10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 3.dp))
    }
}

// ------------------------------------------------------------------- chat --
@Composable
fun ChatPanel(
    modifier: Modifier,
    ci: ChannelInfo,
    messages: List<ChatMsg>,
    status: String,
    connected: Boolean,
    emotes: List<EmoteItem>,
    reply: ChatMsg?,
    sendError: String,
    loggedIn: Boolean,
    textSize: Float,
    showTimes: Boolean,
    quickEmotes: Boolean,
    gifters: List<Pair<String, Int>>?,
    points: Int?,
    pinned: PinnedMsg? = null,
    prediction: Prediction? = null,
    emoteScale: Float = 1f,
    onUserClick: (ChatMsg) -> Unit = {},
    onViewPrediction: () -> Unit = {},
    onLogin: () -> Unit,
    onReply: (ChatMsg?) -> Unit,
    onSend: (String) -> Unit,
    onClearError: () -> Unit,
    onPoints: () -> Unit,
    onKicks: () -> Unit,
    onPeople: () -> Unit,
    onChatSettings: () -> Unit,
    onLongMsg: (ChatMsg) -> Unit,
) {
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf(false) }

    val emoteHolder = remember(emotes) { EmoteHolder(emotes) }
    // one stable callback, so the picker is not recomposed together with every new chat message
    val pickEmote = remember { { name: String -> text = (if (text.isEmpty() || text.endsWith(" ")) text else "$text ") + name + " " } }
    val atBottom by remember { derivedAtBottom(listState) }
    // "stick" = the viewer is following the newest message. Only the viewer's own scrolling changes it, so opening the
    // keyboard (the list shrinks) never shows "Newer messages".
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving -> if (!moving) stick = atBottom }
    }
    val newest = messages.lastOrNull()
    LaunchedEffect(newest) {
        if (newest != null && stick) listState.scrollToItem(messages.size - 1)
    }
    // keyboard / panels change the list height: stay on the newest message if the viewer was following it
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportEndOffset }.collect {
            if (stick && messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
        }
    }

    var hidePred by remember(prediction?.id) { mutableStateOf(false) }
    var hidePin by remember(pinned?.msg?.id) { mutableStateOf(false) }
    Column(modifier.background(Palette.Ink)) {
        if (prediction != null && !hidePred) PredictionCard(prediction, onView = onViewPrediction, onHide = { hidePred = true })
        if (pinned != null && !hidePin) PinnedCard(pinned, textSize, onHide = { hidePin = true })
        if (gifters != null && gifters.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Top gifters", color = Palette.Muted, fontSize = 12.sp)
                gifters.take(3).forEachIndexed { i, g ->
                    Text("${i + 1}. ${g.first} ${g.second}", color = Palette.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(messages, key = { it.id + it.userId + it.hashCode() }) { m ->
                    MessageRow(m, textSize, showTimes, onLongPress = { onLongMsg(m) }, emoteScale = emoteScale, onUserClick = { onUserClick(m) })
                }
            }
            if (!stick && messages.isNotEmpty()) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp))
                        .background(Palette.Accent)
                        .clickable { stick = true; scope.launch { listState.scrollToItem(messages.size - 1) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Newer messages", color = Palette.AccentInk, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        if (reply != null) {
            Row(
                Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Replying to @" + reply.name, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("Cancel", color = Palette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onReply(null) })
            }
        }
        if (sendError.isNotEmpty()) {
            Text(
                sendError, color = Color(0xFFFF4F4D), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clickable { onClearError() },
            )
        }
        if (loggedIn && !picker) {
            val token = text.substringAfterLast(' ')
            val mentionQ = if (token.startsWith("@")) token.drop(1) else null
            val emoteQ = if (token.startsWith(":") && token.length >= 2) token.drop(1) else null
            val mentions = remember(mentionQ, messages.size) {
                if (mentionQ == null) emptyList()
                else messages.asReversed().map { it.name }.distinct().filter { it.startsWith(mentionQ, true) }.take(12)
            }
            val emoteHits = remember(emoteQ, emotes) {
                if (emoteQ == null) emptyList()
                else emotes.filter { it.name.contains(emoteQ, true) }.sortedBy { !it.name.startsWith(emoteQ, true) }.take(20)
            }
            fun replaceToken(with: String) { text = text.dropLast(token.length) + with + " " }
            if (mentions.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(mentions, key = { it }) { n ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Raised).clickable { replaceToken("@$n") }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text("@$n", color = Palette.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (emoteHits.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(emoteHits, key = { it.src + it.name }) { e ->
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Raised).clickable { replaceToken(e.name) }.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(model = e.url, contentDescription = e.name, modifier = Modifier.size(30.dp))
                            Text("  " + e.name, color = Palette.Text, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        if (picker) EmotePicker(emoteHolder, pickEmote)
        if (quickEmotes && emotes.isNotEmpty() && loggedIn && !picker) {
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(emotes.take(14), key = { it.src + it.name }) { e ->
                    AsyncImage(
                        model = e.url, contentDescription = e.name,
                        modifier = Modifier.size(34.dp).clickable { onSend(e.name) },
                    )
                }
            }
        }
        if (!loggedIn) {
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Raised, contentColor = Palette.Accent),
                ) { Text("Sign in to chat", fontWeight = FontWeight.Bold) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                Row(
                    Modifier.weight(1f).heightIn(min = 52.dp, max = 140.dp).clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, Palette.Muted.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 13.dp)) {
                        if (text.isEmpty()) Text("Send a message", color = Palette.Muted, fontSize = 17.sp)
                        androidx.compose.foundation.text.BasicTextField(
                            value = text, onValueChange = { text = it }, maxLines = 5,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Palette.Text, fontSize = 17.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(Modifier.padding(bottom = 6.dp).size(40.dp).clip(CircleShape).clickable { picker = !picker }, contentAlignment = Alignment.Center) {
                        Text("☺", color = if (picker) Palette.Accent else Palette.Text, fontSize = 26.sp)
                    }
                }
                Box(
                    Modifier.padding(start = 8.dp).size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (text.isBlank()) Palette.Raised else Palette.Accent)
                        .clickable { if (text.isNotBlank()) { onSend(text); text = "" } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_send), "Send", tint = if (text.isBlank()) Palette.Muted else Palette.AccentInk, modifier = Modifier.size(24.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPoints() }.padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Palette.Accent))
                Text("  " + (if (points != null) fmtViewers(points) else "Points"), color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Row(Modifier.padding(start = 16.dp).clip(RoundedCornerShape(10.dp)).clickable { onKicks() }.padding(vertical = 6.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Accent), contentAlignment = Alignment.Center) {
                    Text("K", color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                Text("  Kicks", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Box(Modifier.weight(1f))
            Text(fmtViewers(ci.viewers), color = Palette.Muted, fontSize = 13.sp)
            IconButton(onClick = onPeople) { Icon(painterResource(R.drawable.ic_eye), "Leaderboard", tint = Palette.Text) }
            IconButton(onClick = onChatSettings) { Icon(painterResource(R.drawable.ic_gear), "Chat settings", tint = Palette.Text) }
        }
    }
}

private fun derivedAtBottom(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.derivedStateOf {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        last == null || last.index >= info.totalItemsCount - 2
    }

val EMOTE_SCALES = listOf(0.8f, 1f, 1.3f, 1.7f)
val EMOTE_LABELS = listOf("Small", "Normal", "Large", "Huge")

/** Size of an emote in the chat: its real size (7TV files carry it), Kick emotes are square. */
fun emoteDims(e: Seg.Emote, scale: Float): Pair<Float, Float> {
    var w = if (e.w > 0) e.w.toFloat() else 28f
    var h = if (e.h > 0) e.h.toFloat() else 28f
    val f = when {
        h < 20f -> 20f / h          // never tinier than a letter
        h > 64f -> 64f / h
        else -> 1f
    }
    w *= f; h *= f
    if (w > 170f) { h *= 170f / w; w = 170f }
    return Pair(w * scale, h * scale)
}

@Composable
fun MessageRow(m: ChatMsg, textSize: Float, showTime: Boolean, onLongPress: () -> Unit, emoteScale: Float = 1f, onUserClick: () -> Unit = {}) {
    val inline = HashMap<String, InlineTextContent>()
    val bs = (textSize + 4f).sp
    var maxEmote = 0f
    var userStart = 0
    var userEnd = 0
    val layoutHolder = remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    val text: AnnotatedString = buildAnnotatedString {
        if (showTime) {
            val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.time))
            withStyle(SpanStyle(color = Palette.Muted, fontSize = (textSize - 2f).sp)) { append("$f ") }
        }
        userStart = length
        if (!m.deleted) m.badges.forEachIndexed { i, b ->
            val lv = levelOf(b)
            val model = if (lv > 0) null else badgeModel(b)
            if (lv > 0) {
                val id = "b$i"
                inline[id] = InlineTextContent(Placeholder(bs, bs, PlaceholderVerticalAlign.TextCenter)) {
                    LevelBadge(lv, (textSize + 4f).dp)
                }
                appendInlineContent(id, "[level]")
                append(" ")
            } else if (model != null) {
                val id = "b$i"
                inline[id] = InlineTextContent(Placeholder(bs, bs, PlaceholderVerticalAlign.TextCenter)) {
                    AsyncImage(model = model, contentDescription = b.text, modifier = Modifier.fillMaxSize())
                }
                appendInlineContent(id, "[" + b.type + "]")
                append(" ")
            }
        }
        withStyle(SpanStyle(color = parseColor(m.color), fontWeight = FontWeight.ExtraBold)) { append(m.name) }
        withStyle(SpanStyle(color = Palette.Text)) { append(":") }
        userEnd = length
        m.seg.forEachIndexed { i, s ->
            when (s) {
                is Seg.Text -> if (m.deleted) withStyle(SpanStyle(color = Palette.Muted)) { append(s.v) } else append(s.v)
                is Seg.Emote -> {
                    val id = "e$i"
                    val (ew, eh) = emoteDims(s, emoteScale)
                    if (eh > maxEmote) maxEmote = eh
                    inline[id] = InlineTextContent(Placeholder(ew.sp, eh.sp, PlaceholderVerticalAlign.TextCenter)) {
                        AsyncImage(model = s.url, contentDescription = s.name, modifier = Modifier.fillMaxSize())
                    }
                    appendInlineContent(id, s.name)
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        if (m.reply != null && !m.deleted) {
            Text(
                "↩ @" + m.reply.username + ": " + m.reply.content,
                color = Palette.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pointerInput(m.id) { detectTapGestures(onLongPress = { onLongPress() }) },
            )
        }
        Text(
            text, inlineContent = inline, color = Palette.Text, fontSize = textSize.sp,
            lineHeight = maxOf(textSize + 9f, maxEmote + 4f).sp,
            onTextLayout = { layoutHolder[0] = it },
            modifier = Modifier.pointerInput(m.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    // tapping the badges/name part opens the user card
                    onTap = { pos ->
                        val off = layoutHolder[0]?.getOffsetForPosition(pos)
                        if (!m.deleted && off != null && off >= userStart && off <= userEnd) onUserClick()
                    },
                )
            },
        )
    }
}

/** Wrapper so Compose can skip the emote picker while the chat above it keeps recomposing on every message. */
@androidx.compose.runtime.Immutable
class EmoteHolder(val items: List<EmoteItem>)

@Composable
fun EmotePicker(holder: EmoteHolder, onPick: (String) -> Unit) {
    val all = holder.items
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tuff", android.content.Context.MODE_PRIVATE) }
    val animated = remember { prefs.getBoolean("picker_anim", true) }
    val loader = remember(animated) { if (animated) coil.Coil.imageLoader(ctx) else TuffApp.instance.staticLoader }
    // favourites are kept as "src<TAB>name<TAB>url" so they also show in channels that do not have that emote listed
    var favRaw by remember { mutableStateOf<Set<String>>(prefs.getStringSet("emote_favs", emptySet()) ?: emptySet()) }
    val favs = remember(favRaw) {
        favRaw.mapNotNull { r -> r.split("\t").let { if (it.size == 3) EmoteItem(it[1], it[2], it[0]) else null } }.sortedBy { it.name.lowercase() }
    }
    fun favKey(e: EmoteItem) = e.src + "\t" + e.name + "\t" + e.url
    fun toggleFav(e: EmoteItem) {
        val k = favKey(e)
        favRaw = if (k in favRaw) favRaw - k else favRaw + k
        prefs.edit().putStringSet("emote_favs", favRaw).apply()
    }
    var tab by remember { mutableStateOf(if (favs.isNotEmpty()) 0 else 1) }       // 0 favourites, 1 Kick, 2 7TV, 3 All
    var q by remember { mutableStateOf("") }
    val favNames = remember(favRaw) { favs.map { it.src + it.name }.toSet() }
    val list = remember(tab, q, favs, holder) {
        (if (tab == 0) favs else all.filter { tab == 3 || (tab == 1 && it.src == "kick") || (tab == 2 && it.src != "kick") })
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxWidth().height(250.dp).background(Palette.Panel).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("\u2605", "Kick", "7TV", "All").forEachIndexed { i, label ->
                Box(
                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (tab == i) Palette.Accent else Palette.Raised)
                        .clickable { tab = i }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(label, color = if (tab == i) Palette.AccentInk else Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            TextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search emotes", fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
                ),
            )
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        tab == 0 -> "No favourites yet. Long-press any emote to add it here."
                        all.isEmpty() -> "No emotes loaded for this channel"
                        else -> "No emotes found"
                    },
                    color = Palette.Muted, fontSize = 13.sp,
                )
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(46.dp), modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
                items(list, key = { it.src + it.name }) { e ->
                    Box(
                        Modifier.padding(3.dp).size(40.dp).pointerInput(e.name) {
                            detectTapGestures(onTap = { onPick(e.name) }, onLongPress = { toggleFav(e) })
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        // decoded at picker size (not full size) so many animated emotes stay cheap
                        AsyncImage(
                            model = remember(e.url) { coil.request.ImageRequest.Builder(ctx).data(e.url).size(96).build() },
                            imageLoader = loader,
                            contentDescription = e.name, modifier = Modifier.fillMaxSize(),
                        )
                        if ((e.src + e.name) in favNames && tab != 0) {
                            Text("\u2605", color = Color(0xFFFFC107), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------- prediction + pinned --
private val PredGreen = Color(0xFF53FC18)
private val PredPink = Color(0xFFFF7A8A)
private val PredBlue = Color(0xFF4DA3FF)

@Composable
fun PredictionCard(p: Prediction, onView: () -> Unit, onHide: () -> Unit) {
    val cols = listOf(PredGreen, PredPink, PredBlue)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
            .background(Palette.Panel).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p.title.ifEmpty { "Prediction" }, color = Palette.Muted, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                p.outcomes.take(3).forEachIndexed { i, o ->
                    if (i > 0) Text("  vs  ", color = Palette.Muted, fontSize = 17.sp)
                    Box(Modifier.size(12.dp).clip(CircleShape).background(cols[i % cols.size]))
                    Text(
                        " " + java.text.NumberFormat.getIntegerInstance().format(o.amount),
                        color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, maxLines = 1,
                    )
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Raised).clickable { onView() }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) { Text("View", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        IconButton(onClick = onHide) { Icon(painterResource(R.drawable.ic_more), "Hide", tint = Palette.Text) }
    }
}

@Composable
fun PinnedCard(pin: PinnedMsg, textSize: Float, onHide: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(Palette.Panel).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pin), null, tint = Palette.Muted, modifier = Modifier.size(15.dp))
            Text(
                "  Pinned by " + pin.by, color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.ic_close), "Hide", tint = Palette.Muted, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
            MessageRow(pin.msg, textSize, false, onLongPress = { })
        }
    }
}
