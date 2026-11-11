package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Shared between the player controls and the channel bar under the player (both show and hide together). */
object PlayerUi {
    var controls by mutableStateOf(false)
    var ping by mutableStateOf(0)
}

/** Channel card that slides in over the top of the chat together with the player controls (like the Kick app). */
@Composable
fun ChannelInfoBar(ci: ChannelInfo, loggedIn: Boolean, onLogin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(false) }
    LaunchedEffect(ci.slug, loggedIn) {
        if (loggedIn) Browse.isFollowing(ci.slug)?.let { following = it }
    }
    val lang = if (ci.language.length in 2..3) {
        java.util.Locale(ci.language).getDisplayLanguage(java.util.Locale.ENGLISH).ifEmpty { ci.language }
    } else ci.language
    Column(
        Modifier.fillMaxWidth().background(Palette.Ink).padding(horizontal = 14.dp, vertical = 10.dp)
            .pointerInput(Unit) { detectTapGestures { PlayerUi.ping++ } },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).border(2.dp, if (ci.live) Palette.Accent else Palette.Line, CircleShape).padding(3.dp),
            ) { Avatar(ci.avatar, ci.name, 48.dp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ci.name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (ci.verified) { Spacer(Modifier.width(6.dp)); VerifiedBadge(20.dp) }
                }
                if (ci.title.isNotEmpty()) Text(ci.title, color = Palette.Text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (ci.category.isNotEmpty() || lang.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp, start = 66.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf(ci.category, lang)) if (t.isNotEmpty()) {
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Palette.Raised).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(t, color = Palette.Text, fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Raised).clickable { PlayerUi.ping++ }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bell), "Notifications", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Palette.Raised).clickable {
                    PlayerUi.ping++
                    if (!loggedIn) onLogin() else {
                        val want = !following
                        following = want
                        scope.launch { try { Browse.follow(ci.slug, want) } catch (e: Exception) { following = !want } }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_heart), "Follow", tint = if (following) Palette.Accent else Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Accent)
                    .clickable { PlayerUi.ping++; openUrl(ctx, "https://kick.com/" + ci.slug) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_gift), null, tint = Palette.AccentInk, modifier = Modifier.size(20.dp))
                Text("  Gift a Sub", color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}
