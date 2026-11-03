package com.tuff.kick

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(slug: String, nav: Nav, loggedIn: Boolean, onLogin: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val l = rememberLoader("profile", slug) { Browse.profile(slug) }
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        when (val s = l.state) {
            is Load.Idle, is Load.Loading -> SkeletonProfile()
            is Load.Fail -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Could not open $slug", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(s.message.take(80), color = Palette.Muted, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { l.load() }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk)) { Text("Retry", fontWeight = FontWeight.Bold) }
            }
            is Load.Ok -> ProfileBody(s.data, nav, loggedIn, onLogin, onBack)
        }
        Box(Modifier.statusBarsPadding().padding(8.dp).size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Color.White)
        }
    }
}

@Composable
fun ProfileBody(p: Profile, nav: Nav, loggedIn: Boolean, onLogin: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(p.following ?: false) }
    var videoSort by remember { mutableStateOf("date") }
    var clipSort by remember { mutableStateOf("view") }
    var clipPeriod by remember { mutableStateOf("all") }
    val videos = rememberLoader("videos", p.slug) { Browse.videos(p.slug) }
    val clips = rememberLoader("pclips", p.slug, clipSort, clipPeriod) { Browse.clips(p.slug, clipSort, clipPeriod) }

    var linksOpen by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Column {
            Box(Modifier.fillMaxWidth().height(if (tab == 4) 96.dp else 150.dp).background(Brush.verticalGradient(listOf(Palette.Raised, Palette.Ink)))) {
                if (p.banner.isNotEmpty()) AsyncImage(model = p.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.avatar, p.name, 60.dp, ring = p.live)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (p.verified) { Spacer(Modifier.width(6.dp)); VerifiedBadge(22.dp) }
                    }
                    Text(
                        (if (p.live) "LIVE • " + fmtViewers(p.viewers) + " watching • " else "") + fmtViewers(p.followers) + " followers",
                        color = Palette.Muted, fontSize = 14.sp,
                    )
                }
                IconButton(onClick = { shareText(ctx, "https://kick.com/" + p.slug) }) {
                    Icon(painterResource(R.drawable.ic_share), "Share", tint = Palette.Text)
                }
            }
            if (p.links.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { linksOpen = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_link), null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
                    Text(
                        "  " + linkHost(p.links[0].second) + (if (p.links.size > 1) " and ${p.links.size - 1} more" else ""),
                        color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(R.drawable.ic_bell, false) { }
                Spacer(Modifier.width(10.dp))
                RoundIcon(R.drawable.ic_heart, following) {
                    if (!loggedIn) onLogin() else {
                        val want = !following
                        following = want
                        scope.launch { try { Browse.follow(p.slug, want) } catch (e: Exception) { following = !want } }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Accent)
                        .clickable { if (p.live) nav.watch(p.slug) else openUrl(ctx, "https://kick.com/" + p.slug) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(if (p.live) R.drawable.ic_play else R.drawable.ic_gift), null, tint = Palette.AccentInk, modifier = Modifier.size(20.dp))
                    Text(if (p.live) "  Watch live" else "  Gift a Sub", color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
            Tabs(listOf("Home", "About", "Videos", "Clips", "Chat"), tab, { tab = it })
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        }
    }

    if (tab == 4) {
        // Chat tab: the chat itself, right here (like the Kick app), with the same header above it
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // keyboard open (the window shrinks): keep only a slim bar so the chat has room
            val compact = maxHeight < 560.dp
            Column(Modifier.fillMaxSize()) {
                if (compact) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 56.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (p.verified) { Spacer(Modifier.width(6.dp)); VerifiedBadge(18.dp) }
                        Text("  Chat", color = Palette.Muted, fontSize = 15.sp)
                    }
                } else header()
                InlineChat(p.slug, loggedIn, onLogin, onProfile = { nav.profile(it) }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { header() }
        when (tab) {
            0 -> {
                item { Column {
                    SectionTitle("Stream Videos") { tab = 2 }
                    LoadView(videos, skeleton = { SkeletonStreamRow() }) { list ->
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list.take(10), key = { it.id }) { v -> VideoItem(v, p, nav, Modifier.width(300.dp)) }
                        }
                    }
                    SectionTitle("Popular Clips") { tab = 3 }
                    LoadView(clips, skeleton = { SkeletonStreamRow() }) { list ->
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list.take(10), key = { it.id }) { c -> ClipCardP(c, p, nav, Modifier.width(300.dp)) }
                        }
                    }
                } }
            }
            1 -> item {
                Column(Modifier.padding(16.dp)) {
                    Text(if (p.bio.isNotEmpty()) p.bio else "No description.", color = Palette.Text, fontSize = 15.sp)
                    p.links.forEach { (label, url) ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
                                .clickable { openUrl(ctx, url) }.padding(16.dp),
                        ) { Text(label, color = Palette.Text, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text(url.removePrefix("https://"), color = Palette.Muted, fontSize = 13.sp, maxLines = 1) }
                    }
                }
            }
            2 -> {
                item { Column {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SortChip("Most Recent", videoSort == "date") { videoSort = "date" }
                        Spacer(Modifier.width(8.dp))
                        SortChip("Views", videoSort == "view") { videoSort = "view" }
                    }
                    LoadView(videos, skeleton = { SkeletonList() }) { list ->
                        val sorted = if (videoSort == "view") list.sortedByDescending { it.views } else list
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sorted.forEach { v -> VideoItem(v, p, nav, Modifier.fillMaxWidth()) }
                        }
                    }
                } }
            }
            3 -> {
                item { Column {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SortChip("Views", clipSort == "view") { clipSort = "view" }
                        Spacer(Modifier.width(8.dp))
                        SortChip("Most Recent", clipSort == "date") { clipSort = "date" }
                    }
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
                        listOf("day" to "Day", "week" to "Week", "month" to "Month", "all" to "All").forEach { (k, n) ->
                            SortChip(n, clipPeriod == k) { clipPeriod = k }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    LoadView(clips, skeleton = { SkeletonList() }) { list ->
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            list.forEach { c -> ClipCardP(c, p, nav, Modifier.fillMaxWidth()) }
                        }
                    }
                } }
            }
            else -> { }
        }
    }
    if (linksOpen) LinksSheet(p.links, onClose = { linksOpen = false }, onOpen = { openUrl(ctx, it) })
}

private fun linkHost(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")

private fun linkShown(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

/** Icon file (assets/links) for a link, chosen by its address so any link gets a fitting icon. */
private fun linkIcon(url: String): String {
    val h = linkHost(url).lowercase()
    val f = when {
        h.endsWith("instagram.com") -> "instagram"
        h.endsWith("youtube.com") || h == "youtu.be" -> "youtube"
        h == "x.com" || h.endsWith("twitter.com") -> "x"
        h.endsWith("discord.gg") || h.endsWith("discord.com") -> "discord"
        h.endsWith("tiktok.com") -> "tiktok"
        h.endsWith("facebook.com") || h == "fb.com" || h == "fb.me" -> "facebook"
        else -> "link"
    }
    return "file:///android_asset/links/$f.svg"
}

/** "Follow me on" sheet of the official app: icon, name, address, open arrow. */
@Composable
fun LinksSheet(links: List<Pair<String, String>>, onClose: () -> Unit, onOpen: (String) -> Unit) {
    Sheet("Follow me on", onClose = onClose, onBack = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised)) {
            links.forEachIndexed { i, (label, url) ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(Palette.Line))
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(url) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(model = linkIcon(url), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(label.ifEmpty { linkHost(url) }, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                        Text(linkShown(url), color = Palette.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(painterResource(R.drawable.ic_open_external), "Open", tint = Palette.Text, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RoundIcon(res: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Palette.Raised).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(res), null, tint = if (active) Palette.Accent else Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SortChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Palette.Accent else Palette.Raised).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(text, color = if (on) Palette.AccentInk else Palette.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun VideoItem(v: Video, p: Profile, nav: Nav, modifier: Modifier) {
    MediaCard(
        thumb = v.thumb, views = v.views, duration = v.duration, avatar = p.avatar, name = p.name,
        title = v.title, sub = ago(v.created), category = v.category, modifier = modifier,
    ) { nav.play(v.title.ifEmpty { p.name }, v.source.ifEmpty { null }, if (v.source.isEmpty()) v.id else null, null) }
}

@Composable
private fun ClipCardP(c: Clip, p: Profile, nav: Nav, modifier: Modifier) {
    MediaCard(
        thumb = c.thumb, views = c.views, duration = c.duration, avatar = p.avatar, name = p.name, title = c.title,
        sub = (if (c.creator.isNotEmpty()) "Clipped by " + c.creator else "") + (ago(c.created).let { if (it.isNotEmpty()) " • $it" else "" }),
        category = "", modifier = modifier,
    ) { nav.play(c.title.ifEmpty { "Clip" }, c.url, null, c) }
}

// ------------------------------------------------- video / clip player ----
private class PlaySrc(val url: String, val startMs: Long, val endMs: Long, val vod: Boolean)

@OptIn(UnstableApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PlayScreen(title: String, url: String?, videoId: String?, clip: Clip?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var useVod by remember { mutableStateOf(false) }
    val src = rememberLoader("play", url, videoId, clip?.id) {
        val vod = if (clip != null) Browse.clipVodSource(clip) else null
        if (vod != null) PlaySrc(vod.url, vod.startMs, vod.endMs, true)
        else PlaySrc(url ?: Browse.videoSource(videoId ?: throw KickException("no_source")), 0L, 0L, false)
    }
    var heights by remember { mutableStateOf(listOf<Int>()) }
    var quality by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    val vodAvail = (src.state as? Load.Ok<PlaySrc>)?.data?.vod == true
    androidx.activity.compose.BackHandler { if (sheet) sheet = false else onBack() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s0 = src.state) {
            is Load.Idle, is Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Palette.Accent)
            }
            is Load.Fail -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cannot play this video", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(s0.message.take(80), color = Palette.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            is Load.Ok -> {
                val s = s0.data
                // normal playback = the clip's own file; the full-broadcast cut (more qualities) is opt-in
                val direct = if (clip != null && clip.url.isNotEmpty()) clip.url else s.url
                val clipped = useVod && s.vod
                val playUrl = if (clipped) s.url else direct
                val player = remember(playUrl, clipped) {
                    val http = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .setDefaultRequestProperties(mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/"))
                    val item = MediaItem.Builder().setUri(playUrl).apply {
                        if (clipped) {
                            setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build()
                            )
                        }
                    }.build()
                    ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().also {
                        it.setMediaItem(item)
                        it.prepare()
                        it.playWhenReady = true
                    }
                }
                DisposableEffect(player) {
                    val l = object : androidx.media3.common.Player.Listener {
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            val hs = ArrayList<Int>()
                            for (g in tracks.groups) {
                                if (g.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                    for (k in 0 until g.length) {
                                        val h = g.getTrackFormat(k).height
                                        if (h > 0 && !hs.contains(h)) hs.add(h)
                                    }
                                }
                            }
                            heights = hs.sortedDescending()
                        }

                        override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                            // the broadcast cut did not play (removed / offset off): back to the clip's own file
                            if (clipped) { useVod = false; heights = emptyList() }
                        }
                    }
                    player.addListener(l)
                    onDispose { player.removeListener(l); player.release() }
                }
                LaunchedEffect(quality, heights) {
                    val b = player.trackSelectionParameters.buildUpon()
                    if (quality == 0) b.clearVideoSizeConstraints() else b.setMaxVideoSize(Int.MAX_VALUE, quality)
                    player.trackSelectionParameters = b.build()
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> PlayerView(c).apply { this.player = player; useController = true } },
                )
            }
        }
        Row(Modifier.statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (heights.size > 1 || vodAvail) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x99000000)).clickable { sheet = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(if (quality == 0) "Auto" else quality.toString() + "p", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        if (sheet) {
            Sheet("Quality", onClose = { sheet = false }) {
                OptionRow("Auto (recommended)", checked = quality == 0) { quality = 0; sheet = false }
                heights.forEach { h -> OptionRow(h.toString() + "p", checked = quality == h) { quality = h; sheet = false } }
                if (vodAvail) {
                    OptionRow(
                        "More qualities (from the full broadcast)",
                        "Only if the past broadcast still exists; timing may be a few seconds off",
                        useVod,
                    ) { useVod = !useVod; heights = emptyList(); quality = 0; sheet = false }
                }
            }
        }
    }
}
