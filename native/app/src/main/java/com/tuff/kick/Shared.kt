package com.tuff.kick

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ------------------------------------------------------------- navigation --
class Nav(
    val watch: (String) -> Unit,
    val profile: (String) -> Unit,
    val category: (String, String) -> Unit,
    val play: (String, String?, String?, Clip?) -> Unit,   // title, direct url, kick video uuid, clip
)

// ----------------------------------------------------------------- loading --
class Loader<T>(private val scope: CoroutineScope, private val block: suspend () -> T) {
    var state by mutableStateOf<Load<T>>(Load.Idle)

    fun load() {
        state = Load.Loading
        scope.launch {
            state = try {
                Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: KickException) {
                Load.Fail(e.code)
            } catch (e: Exception) {
                Load.Fail(e.message ?: "error")
            }
        }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val l = remember(*keys) { Loader(scope, block) }
    LaunchedEffect(l) { l.load() }
    return l
}

@Composable
fun <T> LoadView(
    l: Loader<T>,
    skeleton: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Accent)
        }
    },
    content: @Composable (T) -> Unit,
) {
    when (val s = l.state) {
        is Load.Idle, is Load.Loading -> skeleton()
        is Load.Fail -> Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Could not load", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(s.message.take(90), color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp), textAlign = TextAlign.Center)
            Button(
                onClick = { l.load() },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
            ) { Text("Retry", fontWeight = FontWeight.Bold) }
        }
        is Load.Ok -> content(s.data)
    }
}

// ------------------------------------------------------------------ format --
fun fmtDur(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun ago(iso: String): String {
    if (iso.length < 19) return ""
    return try {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        val t = f.parse(iso.substring(0, 19).replace('T', ' '))?.time ?: return ""
        val d = (System.currentTimeMillis() - t) / 1000
        when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} minutes ago"
            d < 86400 -> "${d / 3600} hours ago"
            d < 86400 * 30 -> "${d / 86400} days ago"
            d < 86400 * 365 -> "${d / (86400 * 30)} months ago"
            else -> "${d / (86400 * 365)} years ago"
        }
    } catch (e: Exception) {
        ""
    }
}

fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

fun shareText(ctx: android.content.Context, text: String) {
    try {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

// ----------------------------------------------------------------- pieces --
@Composable
fun Avatar(url: String, name: String, size: Dp, ring: Boolean = false, modifier: Modifier = Modifier) {
    val inner: @Composable () -> Unit = {
        Box(Modifier.size(if (ring) size - 6.dp else size).clip(CircleShape).background(Palette.Raised), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.6f).sp)
            if (url.isNotEmpty()) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
    if (ring) {
        Box(modifier.size(size).border(2.dp, Palette.Accent, CircleShape), contentAlignment = Alignment.Center) { inner() }
    } else {
        Box(modifier) { inner() }
    }
}

@Composable
fun ViewerBadge(n: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Accent))
        Spacer(Modifier.width(6.dp))
        Text(fmtViewers(n), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Raised).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = Palette.Text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        if (onClick != null) Text("  ›", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

@Composable
fun StreamCard(s: Stream, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Panel).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Raised)) {
            if (s.thumb.isNotEmpty()) {
                AsyncImage(model = s.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            ViewerBadge(s.viewers, Modifier.padding(8.dp))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.avatar, s.name, 40.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.language.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Chip(langName(s.language))
                    }
                }
                Text(s.title, color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun StreamRow(list: List<Stream>, nav: Nav, width: Dp = 300.dp) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.slug }) { s -> StreamCard(s, Modifier.width(width)) { nav.watch(s.slug) } }
    }
}

@Composable
fun CatCard(c: Cat, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).background(Palette.Raised)) {
            if (c.image.isNotEmpty()) {
                AsyncImage(model = c.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (c.viewers > 0) ViewerBadge(c.viewers, Modifier.padding(6.dp))
        }
        Text(c.name, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MediaCard(
    thumb: String, views: Int, duration: Int, avatar: String, name: String, title: String, sub: String, category: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Panel).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Raised)) {
            if (thumb.isNotEmpty()) {
                AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Row(
                Modifier.padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_eye), null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(fmtViewers(views), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (duration > 0) {
                Text(
                    fmtDur(duration), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, name, 40.dp)
            Column(Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (category.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Chip(category)
                    }
                }
                if (title.isNotEmpty()) Text(title, color = Palette.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotEmpty()) Text(sub, color = Palette.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { i, l ->
            Column(Modifier.clickable { onSelect(i) }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    l, color = if (i == selected) Palette.Accent else Palette.Text, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, modifier = Modifier.padding(vertical = 12.dp),
                )
                Box(Modifier.height(3.dp).width(36.dp).background(if (i == selected) Palette.Accent else Color.Transparent))
            }
        }
    }
}

@Composable
fun Sheet(title: String, onClose: () -> Unit, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = none, indication = null) { onClose() },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(Palette.Panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding().padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Palette.Text) }
                } else {
                    Box(Modifier.size(48.dp))
                }
                Text(title, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), "Close", tint = Palette.Text) }
            }
            content()
        }
    }
}

@Composable
fun OptionRow(text: String, sub: String = "", checked: Boolean = false, trailing: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Raised)
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = Palette.Text, fontSize = 16.sp)
            if (sub.isNotEmpty()) Text(sub, color = Palette.Muted, fontSize = 13.sp)
        }
        if (trailing.isNotEmpty()) Text(trailing, color = Palette.Muted, fontSize = 14.sp)
        if (checked) Icon(painterResource(R.drawable.ic_check), null, tint = Palette.Text)
    }
}


/** Kick's verified badge (the same artwork the chat uses). */
@Composable
fun VerifiedBadge(size: Dp = 20.dp, modifier: Modifier = Modifier) {
    AsyncImage(model = "file:///android_asset/badges/verified.png", contentDescription = "Verified", modifier = modifier.size(size))
}
