package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private val Card = Color(0xFF1B1C1F)

/** Remembers the level badge this account shows in chat, because Kick's identity reply does not list it. */
@Composable
fun TrackMyBadges(messages: List<ChatMsg>) {
    val ctx = LocalContext.current
    LaunchedEffect(messages.size) {
        if (Me.name.isBlank()) return@LaunchedEffect
        val b = messages.lastOrNull { m -> m.name.equals(Me.name, true) && m.badges.any { it.type.contains("level", true) } }
            ?.badges?.firstOrNull { it.type.contains("level", true) } ?: return@LaunchedEffect
        try { ctx.getSharedPreferences("tuff", android.content.Context.MODE_PRIVATE).edit().putString("my_level", "${b.type}|${b.count}|${b.text}|${b.url.orEmpty()}").apply() } catch (e: Exception) {}
    }
}

// ------------------------------------------------------------ settings UI --
@Composable
fun SettingsHeading(text: String) {
    Text(text, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 10.dp))
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(horizontal = 4.dp)) { content() }
}

@Composable
fun SettingItem(label: String, trailing: String = "", last: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Palette.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (trailing.isNotEmpty()) Text(trailing, color = Palette.Muted, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
            if (chevron) Icon(painterResource(R.drawable.ic_arrow_left), null, tint = Palette.Text, modifier = Modifier.size(26.dp).rotate(180f))
        }
        if (!last) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2A2B2F)))
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Palette.Text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.Accent, checkedThumbColor = Color(0xFF0B0B0C), checkedBorderColor = Palette.Accent,
                    uncheckedTrackColor = Color(0xFF2A2B2F), uncheckedThumbColor = Color(0xFF8A8B90), uncheckedBorderColor = Color(0xFF55575C),
                ),
            )
        }
        if (!last) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Color(0xFF2A2B2F)))
    }
}

// --------------------------------------------------------------- identity --
private val ChannelBadgeTypes = setOf("og", "subscriber", "sub_gifter", "subgifter", "moderator", "vip", "founder", "broadcaster", "owner")

private fun hexColor(h: String): Color = try { Color(android.graphics.Color.parseColor(h)) } catch (e: Exception) { Color.White }

@Composable
fun IdentitySheet(slug: String, channelId: Long, messages: List<ChatMsg>, onClose: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLoader("identity", slug) { Identity.load(slug, channelId) }
    var color by remember { mutableStateOf("") }
    val badges = remember { mutableStateListOf<IdBadge>() }
    var saving by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(Identity.COLORS) }
    var fromKick by remember { mutableStateOf(false) }
    var known by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(l.state) {
        val s = l.state
        if (s is Load.Ok) {
            color = s.data.color
            if (s.data.colors.isNotEmpty()) { palette = s.data.colors; fromKick = true }
        }
        if (s is Load.Ok || s is Load.Fail) {
            fun norm(t: String) = t.lowercase().replace("_", "").replace("-", "")
            // Kick's identity reply lists only the badges that are switched on
            val all = (s as? Load.Ok)?.data?.badges
            val on = all?.filter { !it.v2 }?.map { norm(it.type) }?.toSet()
            val v2on = all?.filter { it.v2 }?.associate { norm(it.type) to it.active } ?: emptyMap()
            known = on.orEmpty()
            // every badge the account owns, as the user card shows them
            val card = try { if (Me.name.isNotBlank()) UserCard.load(slug, Me.name).badges else emptyList() } catch (e: Exception) { emptyList() }
            val imgs = HashMap<String, String>()
            for (m in messages) for (b in m.badges) if (!b.url.isNullOrEmpty()) imgs[norm(b.type)] = b.url
            val list = ArrayList<IdBadge>()
            for (b in card) if (list.none { norm(it.type) == norm(b.type) }) {
                val isV2 = norm(b.type).contains("level") || norm(b.type) in v2on
                val act = if (isV2) (v2on[norm(b.type)] ?: v2on.entries.firstOrNull { it.key.contains("level") && norm(b.type).contains("level") }?.value ?: true) else (on == null || norm(b.type) in on)
                list.add(IdBadge(b.type, b.text, b.count, act, b.url.orEmpty().ifEmpty { imgs[norm(b.type)].orEmpty() }, isV2))
            }
            (s as? Load.Ok)?.data?.badges?.forEach { b ->
                if (list.none { norm(it.type) == norm(b.type) }) list.add(IdBadge(b.type, b.text, b.count, b.active, b.url.ifEmpty { imgs[norm(b.type)].orEmpty() }, b.v2))
            }
            val mine = messages.filter { it.name.equals(Me.name, true) }
            if (color.isEmpty()) mine.lastOrNull()?.color?.let { color = it }
            for (m in mine.reversed()) for (b in m.badges) {
                if (list.none { norm(it.type) == norm(b.type) }) list.add(IdBadge(b.type, b.text, b.count, on == null || norm(b.type) in on, b.url.orEmpty()))
            }
            // a level badge that is switched off is not in Kick's lists, so keep the one seen earlier to be able to switch it on again
            if (list.none { norm(it.type).contains("level") }) {
                val sv = try { ctx.getSharedPreferences("tuff", android.content.Context.MODE_PRIVATE).getString("my_level", null) } catch (e: Exception) { null }
                val p = sv?.split("|")
                if (p != null && p.size >= 4) list.add(0, IdBadge(p[0], p[2], p[1].toIntOrNull() ?: 0, false, p[3], true))
            }
            badges.clear()
            badges.addAll(list)
            // Kick only accepts its own exact colours; the real ones are the colours seen in chat (and the account's current one)
            if (!fromKick) {
                val prefs = try { ctx.getSharedPreferences("tuff", android.content.Context.MODE_PRIVATE) } catch (e: Exception) { null }
                val seen = LinkedHashSet<String>()
                prefs?.getString("seen_colors", "")?.split(",")?.filter { it.startsWith("#") && it.length == 7 }?.forEach { seen.add(it.uppercase()) }
                for (m in messages) if (m.color.startsWith("#") && m.color.length == 7) seen.add(m.color.uppercase())
                if (color.startsWith("#") && color.length == 7) seen.add(color.uppercase())
                try { prefs?.edit()?.putString("seen_colors", seen.take(200).joinToString(","))?.apply() } catch (e: Exception) {}
                fun rgb(h: String) = intArrayOf(h.substring(1, 3).toInt(16), h.substring(3, 5).toInt(16), h.substring(5, 7).toInt(16))
                fun dist(a: String, b: String) = try { val x = rgb(a); val y = rgb(b); Math.abs(x[0] - y[0]) + Math.abs(x[1] - y[1]) + Math.abs(x[2] - y[2]) } catch (e: Exception) { 999 }
                palette = Identity.COLORS.map { c -> seen.filter { dist(it, c) <= 30 }.minByOrNull { dist(it, c) } ?: c }
            }
        }
    }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Sheet("Identity", onClose = onClose, onBack = onBack) {
        Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
            SettingsHeading("Username Color")
            if (l.state is Load.Ok && !fromKick) Text("Kick did not send its colour list, showing the default colours.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            palette.chunked(8).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val sel = color.equals(c, true)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(hexColor(c))
                                .border(if (sel) 3.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(painterResource(R.drawable.ic_check), null, tint = Color.Black, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
            val failed = (l.state as? Load.Fail)
            val globals = badges.filter { it.type.lowercase() !in ChannelBadgeTypes }
            val channels = badges.filter { it.type.lowercase() in ChannelBadgeTypes }
            SettingsHeading("Global Badges")
            if (l.state is Load.Idle || l.state is Load.Loading) Text("Loading…", color = Palette.Muted, modifier = Modifier.padding(8.dp))
            if (failed != null && badges.isEmpty()) Text("Could not load your badges (" + failed.message.take(40) + ").", color = Palette.Muted, modifier = Modifier.padding(8.dp))
            else if (globals.isEmpty() && l.state is Load.Ok) Text("You don't have any global badges yet.", color = Palette.Muted, modifier = Modifier.padding(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                globals.forEach { b -> BadgeTile(b) { toggle(badges, b) } }
            }
            SettingsHeading("Channel Badges")
            if (channels.isEmpty() && failed == null && l.state is Load.Ok) {
                Text("You don't have any channel badges yet.", color = Palette.Muted, textAlign = TextAlign.Center, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                channels.forEach { b -> BadgeTile(b) { toggle(badges, b) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(if (saving) Color(0xFF1E1F22) else Palette.Accent).clickable(enabled = !saving) {
                saving = true
                scope.launch {
                    val msg = try {
                        Identity.save(slug, channelId, color.ifEmpty { palette[0] }, badges.toList(), known)
                        "Saved"
                    } catch (e: KickException) { if (e.code.contains("422") && e.code.contains("invalid", true)) "Kick did not accept this colour yet. Stay in chat for a minute so the exact colours are learned, then try again." else e.code.take(240) } catch (e: Exception) { "Failed" }
                    saving = false
                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            },
            contentAlignment = Alignment.Center,
        ) { Text("Save", color = if (saving) Palette.Muted else Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp) }
    }
}

private fun toggle(list: androidx.compose.runtime.snapshots.SnapshotStateList<IdBadge>, b: IdBadge) {
    val i = list.indexOf(b)
    if (i >= 0) list[i] = IdBadge(b.type, b.text, b.count, !b.active, b.url, b.v2)
}

@Composable
private fun BadgeTile(b: IdBadge, onClick: () -> Unit) {
    val badge = Badge(b.type, b.url.ifEmpty { null }, b.text, b.count)
    val lv = levelOf(badge)
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF26272B))
            .border(if (b.active) 2.dp else 0.dp, if (b.active) Palette.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lv > 0) LevelBadge(lv, 36.dp)
        else AsyncImage(model = badgeModel(badge), contentDescription = b.text, modifier = Modifier.size(36.dp))
        Text(
            if (lv > 0) "Level" else b.text.ifEmpty { b.type.replaceFirstChar { it.uppercase() } }, color = Palette.Text, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

// --------------------------------------------------------------- chatters --
private class Chatter(val name: String, val color: String)

@Composable
fun ChattersSheet(messages: List<ChatMsg>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var openMods by remember { mutableStateOf(true) }
    var openVips by remember { mutableStateOf(true) }
    var openOgs by remember { mutableStateOf(true) }
    var openChat by remember { mutableStateOf(true) }
    // built from who has spoken recently in this chat (Kick does not expose its viewer list)
    val groups = remember(messages.size, messages.lastOrNull()?.id) {
        val last = LinkedHashMap<String, ChatMsg>()
        for (m in messages) if (!m.deleted) last[m.name.lowercase()] = m
        val mods = ArrayList<Chatter>(); val vips = ArrayList<Chatter>(); val ogs = ArrayList<Chatter>(); val rest = ArrayList<Chatter>()
        for (m in last.values.reversed()) {
            val t = m.badges.map { it.type.lowercase() }
            val c = Chatter(m.name, m.color)
            when {
                "moderator" in t || "broadcaster" in t -> mods.add(c)
                "vip" in t -> vips.add(c)
                "og" in t -> ogs.add(c)
                else -> rest.add(c)
            }
        }
        listOf(mods, vips, ogs, rest)
    }
    val q = query.trim()
    fun f(l: List<Chatter>) = if (q.isEmpty()) l else l.filter { it.name.contains(q, true) }
    val mods = f(groups[0]); val vips = f(groups[1]); val ogs = f(groups[2]); val rest = f(groups[3])
    val total = groups.sumOf { it.size }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    Sheet("Active chatters", onClose = onClose, titleAccent = "($total)") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Palette.Line, RoundedCornerShape(10.dp)).background(Palette.Ink)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
            Box(Modifier.padding(start = 10.dp).weight(1f)) {
                if (query.isEmpty()) Text("Search username", color = Palette.Muted, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp), cursorBrush = SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxH)) {
            fun section(key: String, title: String, type: String, open: Boolean, toggle: () -> Unit, list: List<Chatter>) {
                if (list.isEmpty()) return
                item(key = "h$key") {
                    Row(Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (type == "chatters") Icon(painterResource(R.drawable.ic_chatters), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        else AsyncImage(model = badgeModel(Badge(type, null, title)), contentDescription = title, modifier = Modifier.size(20.dp))
                        Text(title, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        Icon(painterResource(R.drawable.ic_chevron_down), null, tint = Palette.Text, modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2B2F)))
                }
                if (open) items(list.size, key = { "$key-${list[it].name}" }) { i ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        val av by androidx.compose.runtime.produceState("", list[i].name) { value = Browse.avatarOf(list[i].name) }
                        Avatar(av, list[i].name, 30.dp)
                        Text(list[i].name, color = Palette.Text, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            section("m", "Moderators", "moderator", openMods, { openMods = !openMods }, mods)
            section("v", "VIPs", "vip", openVips, { openVips = !openVips }, vips)
            section("o", "OGs", "og", openOgs, { openOgs = !openOgs }, ogs)
            section("c", "Chatters", "chatters", openChat, { openChat = !openChat }, rest)
        }
    }
}
