package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

data class UserCardInfo(
    val avatar: String,
    val followingSince: String,
    val subscribedMonths: Int,
    val streak: Int,
    val badges: List<Badge>,
    val banner: String = "",
)

/**
 * What the official app shows when you tap a name in chat: the user's badges and how long they follow / subscribe
 * to this channel. Kick does not document this call, so the JSON is read defensively and missing values are just hidden.
 */
object UserCard {
    private fun num(o: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            val v = o.opt(k)
            if (v is Number) return v.toInt()
            if (v is String) v.toIntOrNull()?.let { return it }
        }
        return 0
    }

    /** First number stored under a key that contains [part] (the streak lives under a name I could not confirm). */
    private fun findNum(node: Any?, part: String, depth: Int = 0): Int {
        if (depth > 4 || node !is JSONObject) return 0
        val it = node.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = node.opt(k)
            if (k.contains(part, true)) {
                if (v is Number) return v.toInt()
                if (v is JSONObject) {
                    val n = v.keys()
                    while (n.hasNext()) { val x = v.opt(n.next()); if (x is Number) return x.toInt() }
                }
            }
            if (v is JSONObject) { val r = findNum(v, part, depth + 1); if (r > 0) return r }
        }
        return 0
    }

    /** First image address stored under a key that contains "banner" anywhere in the JSON. */
    private fun findBanner(node: Any?, depth: Int = 0): String {
        if (depth > 5 || node !is JSONObject) return ""
        val it = node.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = node.opt(k)
            if (k.contains("banner", true)) {
                when (v) {
                    is String -> if (v.startsWith("http")) return v
                    is JSONObject -> for (f in listOf("url", "src", "responsive", "original")) {
                        val u = v.optString(f)
                        if (u.startsWith("http")) return u.substringBefore(" ")
                    }
                }
            }
            if (v is JSONObject) { val r = findBanner(v, depth + 1); if (r.isNotEmpty()) return r }
        }
        return ""
    }

    suspend fun load(channelSlug: String, username: String): UserCardInfo {
        val u = KickBridge.BASE + "/api/v2/channels/" + channelSlug + "/users/" + java.net.URLEncoder.encode(username, "UTF-8")
        val root = KickApi.getJsonPublic(u, KickBridge.token() != null) as? JSONObject ?: throw KickException("bad_json")
        val d = root.optJSONObject("data") ?: root
        val avatar = when (val p = d.opt("profile_pic")) {
            is String -> p
            is JSONObject -> p.optString("src")
            else -> ""
        }.orEmpty()
        // the banner belongs to the user's own channel page
        var banner = findBanner(d)
        var avatar2 = avatar
        try {
            val slug = username.lowercase().replace('_', '-')
            val ch = KickApi.getJsonPublic(KickBridge.BASE + "/api/v2/channels/" + java.net.URLEncoder.encode(slug, "UTF-8"), false) as? JSONObject
            if (banner.isEmpty()) banner = findBanner(ch)
            if (avatar2.isEmpty()) avatar2 = ch?.optJSONObject("user")?.optString("profile_pic").orEmpty()
        } catch (e: Exception) {
        }
        return UserCardInfo(
            banner = banner,
            avatar = avatar2,
            followingSince = d.optString("following_since", ""),
            subscribedMonths = num(d, "subscribed_for", "months_subscribed"),
            streak = findNum(d, "streak"),
            badges = ChatRender.badges(d, null, false),
        )
    }
}

private fun prettyDate(iso: String): String {
    if (iso.length < 10 || iso == "null") return ""
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.substring(0, 10))
        SimpleDateFormat("MMMM d, yyyy", Locale.US).format(d!!)
    } catch (e: Exception) {
        iso.substring(0, 10)
    }
}

/** Level of any level badge, also when Kick supplied its own picture (used for the colour of the card). */
fun anyLevel(b: Badge): Int {
    if (b.type.contains("level", true) || b.text.contains("level", true)) {
        if (b.count > 0) return b.count
        return Regex("\\d+").find(b.text)?.value?.toIntOrNull() ?: Regex("\\d+").find(b.type)?.value?.toIntOrNull() ?: 0
    }
    // Kick's own level picture: the badge text is just the number
    if (b.text.isNotEmpty() && b.text.all { it.isDigit() } && b.text.length <= 3) return b.text.toInt()
    return 0
}

/** Colour of the user's level tier: yellow 10+, pink 20+, cyan 30+, green 40+, blue 50+, lime 60+. */
fun tierColor(level: Int): Color = when {
    level >= 60 -> Color(0xFF9CD320)
    level >= 50 -> Color(0xFF768DFF)
    level >= 40 -> Color(0xFF0AFFAD)
    level >= 30 -> Color(0xFF00EFFD)
    level >= 20 -> Color(0xFFFF61B7)
    level >= 10 -> Color(0xFFFFED38)
    else -> Palette.Accent
}

/** Number shown on Kick's level badge, or 0 when the badge is not a level badge. */
fun levelOf(b: Badge): Int {
    if (!b.url.isNullOrEmpty()) return 0          // Kick sent its own image: show that, not our drawing
    if (!b.type.contains("level", true)) return 0
    if (b.count > 0) return b.count
    return Regex("\\d+").find(b.text)?.value?.toIntOrNull() ?: 0
}

private class LevelTier(val file: String, val centerY: Float, val ink: Int)

// The artwork changes every 10 levels; below 10 uses the first one. centerY / ink are measured from Kick's own badge images.
private fun tierOf(level: Int): LevelTier = when {
    level >= 60 -> LevelTier("level_60.png", 24.3f, 0xFF0C1103.toInt())
    level >= 50 -> LevelTier("level_50.png", 24.5f, 0xFF07080D.toInt())
    level >= 40 -> LevelTier("level_40.png", 22.8f, 0xFF010D09.toInt())
    level >= 30 -> LevelTier("level_30.png", 21.9f, 0xFF000E0F.toInt())
    level >= 20 -> LevelTier("level_20.png", 27.9f, 0xFF0C0509.toInt())
    else -> LevelTier("level_10.png", 23.3f, 0xFF0E0E05.toInt())
}

/** Level badge: the artwork of the level's tier (empty shape) with this user's level drawn into it. */
@Composable
fun LevelBadge(level: Int, size: Dp) {
    val t = remember(level) { tierOf(level) }
    val text = level.toString()
    Box(Modifier.size(size)) {
        AsyncImage(model = "file:///android_asset/badges/" + t.file, contentDescription = "Level $level", modifier = Modifier.fillMaxSize())
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val s = this.size.width
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = t.ink
            paint.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            val r = android.graphics.Rect()
            // digits as tall as in Kick's badges (14 of 50 px), narrowed only when the level has 3+ digits
            paint.textSize = 100f
            paint.getTextBounds("0", 0, 1, r)
            paint.textSize = 100f * (s * 0.28f) / r.height()
            val w = paint.measureText(text)
            val maxW = s * 0.62f
            if (w > maxW) paint.textSize = paint.textSize * maxW / w
            paint.getTextBounds(text, 0, text.length, r)
            val baseline = s * t.centerY / 50f - (r.top + r.bottom) / 2f
            drawContext.canvas.nativeCanvas.drawText(text, s / 2f, baseline, paint)
        }
    }
}

@Composable
fun BadgeIcon(b: Badge, size: Dp) {
    val lv = levelOf(b)
    if (lv > 0) { LevelBadge(lv, size); return }
    val model = badgeModel(b) ?: return
    AsyncImage(model = model, contentDescription = b.text, modifier = Modifier.size(size))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Raised)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.Muted, fontSize = 17.sp)
    }
}

@Composable
private fun ActionRow(icon: Int, text: String, arrow: Boolean = false, divider: Boolean = true, onClick: () -> Unit) {
    if (divider) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(Palette.Line))
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = Palette.Text, modifier = Modifier.size(22.dp))
        Text(text, color = Palette.Text, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp).weight(1f))
        if (arrow) Text("›", color = Palette.Text, fontSize = 22.sp)
    }
}

/** Sheet opened by tapping a name in chat (Reply stays on long-press). */
@Composable
fun UserSheet(
    msg: ChatMsg, channelSlug: String, loggedIn: Boolean, onLogin: () -> Unit,
    onViewChannel: (String) -> Unit, onMute: () -> Unit, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLoader("usercard", channelSlug, msg.name) { UserCard.load(channelSlug, msg.name) }
    val userSlug = remember(msg.name) { msg.name.lowercase().replace('_', '-') }
    var following by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(userSlug, loggedIn) {
        following = if (loggedIn) try { Browse.isFollowing(userSlug) } catch (e: Exception) { null } else null
    }
    Sheet(msg.name, onClose = onClose) {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.64f).dp).verticalScroll(rememberScrollState())) {
            val st = l.state
            val info = (st as? Load.Ok<UserCardInfo>)?.data
            val badges = msg.badges + info?.badges.orEmpty().filter { ib -> msg.badges.none { it.type == ib.type } }
            val tint = tierColor(badges.maxOfOrNull { anyLevel(it) } ?: 0)
            val banner = info?.banner.orEmpty()
            if (banner.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 28.dp).clip(RoundedCornerShape(14.dp))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.15f))))
                            .padding(5.dp),
                    ) {
                        coil.compose.AsyncImage(
                            model = banner, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black),
                        )
                    }
                    Box(Modifier.align(Alignment.BottomCenter).size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint).padding(3.dp)) {
                        Avatar(info?.avatar.orEmpty(), msg.name, 58.dp)
                    }
                }
            } else if (info != null) {
                // no banner on the channel: Kick shows a pixel picture, drawn here from the name
                Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 28.dp).clip(RoundedCornerShape(14.dp))
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.15f))))
                            .padding(5.dp),
                    ) {
                        PixelBanner(msg.name, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    Box(Modifier.align(Alignment.BottomCenter).size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint).padding(3.dp)) {
                        Avatar(info.avatar, msg.name, 58.dp)
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(100.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint).padding(4.dp)) {
                        Avatar(info?.avatar.orEmpty(), msg.name, 92.dp)
                    }
                }
            }
            if (badges.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    badges.forEach { b -> BadgeIcon(b, 30.dp); Spacer(Modifier.width(8.dp)) }
                }
            }
            if (st is Load.Idle || st is Load.Loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
            }
            if (info != null) {
                val since = prettyDate(info.followingSince)
                if (since.isNotEmpty()) InfoRow("Following since", since)
                if (info.subscribedMonths > 0) InfoRow("Subscribed for", info.subscribedMonths.toString() + (if (info.subscribedMonths == 1) " month" else " months"))
                if (info.streak > 0) InfoRow("Streak", "x" + info.streak + (if (info.streak == 1) " day" else " days"))
            }
            Text("Actions", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised)) {
                ActionRow(R.drawable.ic_heart, if (following == true) "Following" else "Follow", divider = false) {
                    if (!loggedIn) { onClose(); onLogin() } else {
                        val want = following != true
                        following = want
                        scope.launch {
                            try { Browse.follow(userSlug, want) } catch (e: Exception) {
                                following = !want
                                android.widget.Toast.makeText(ctx, "Failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                ActionRow(R.drawable.ic_play, "View channel", arrow = true) { onClose(); onViewChannel(userSlug) }
                ActionRow(R.drawable.ic_volume_off, "Mute") { onMute(); onClose() }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


private val pixelColors = listOf(
    0xFF53FC18, 0xFF00EFFD, 0xFF3A2A12, 0xFF5A4318, 0xFF0AFFAD, 0xFF1B2A12, 0xFF2B6E10, 0xFF0B0B0B,
).map { Color(it.toInt()) }

/** Chunky random pixel picture, always the same for the same name. */
@Composable
fun PixelBanner(seed: String, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val rnd = java.util.Random(seed.hashCode().toLong())
        val cols = 24
        val rows = 6
        val w = size.width / cols
        val h = size.height / rows
        for (c in 0 until cols) for (r in 0 until rows) {
            val col = pixelColors[rnd.nextInt(pixelColors.size)]
            drawRect(col, androidx.compose.ui.geometry.Offset(c * w, r * h), androidx.compose.ui.geometry.Size(w + 1f, h + 1f))
        }
    }
}
