package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

private class EmoteSrc(val label: String, val color: Color, val page: String?)

private fun sourceOf(url: String): EmoteSrc {
    val u = url.lowercase()
    return when {
        "7tv" in u -> {
            val id = Regex("/emote/([A-Za-z0-9]+)").find(url)?.groupValues?.get(1)
            EmoteSrc("7TV", Color(0xFF29B6F6), id?.let { "https://7tv.app/emotes/$it" })
        }
        "kick.com" in u -> EmoteSrc("KICK", Color(0xFF53FC18), null)
        "betterttv" in u -> {
            val id = Regex("/emote/([A-Za-z0-9]+)").find(url)?.groupValues?.get(1)
            EmoteSrc("BTTV", Color(0xFFFF5C5C), id?.let { "https://betterttv.com/emotes/$it" })
        }
        "frankerfacez" in u || "ffz" in u -> EmoteSrc("FFZ", Color(0xFF7C8CFF), null)
        else -> EmoteSrc("EMOTE", Color(0xFFADADB8), null)
    }
}

/** Card shown when an emote in chat is long-pressed. */
@Composable
fun EmoteInfoDialog(e: Seg.Emote, onDismiss: () -> Unit) {
    val src = sourceOf(e.url)
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 230.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Panel).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Ink),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = e.url, contentDescription = e.name, contentScale = ContentScale.Fit, modifier = Modifier.size(72.dp))
            }
            Text(
                e.name, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center,
                maxLines = 1, modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(src.color.copy(alpha = 0.18f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(src.label + " EMOTE", color = src.color, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
                }
                if (e.w > 0 && e.h > 0) Text("${e.w}×${e.h}", color = Palette.Muted, fontSize = 11.sp)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Palette.Raised).clickable {
                    clip.setText(AnnotatedString(e.name))
                    android.widget.Toast.makeText(ctx, "Copied " + e.name, android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                contentAlignment = Alignment.Center,
            ) { Text("Copy emote", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}
