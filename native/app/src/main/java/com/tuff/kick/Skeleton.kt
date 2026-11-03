package com.tuff.kick

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Skeleton placeholders: grey blocks shaped like the real content (what the official Kick app shows while
 * a list loads) instead of a spinning circle.
 */

private val SkelBase = Color(0xFF1D1E20)
private val SkelHi = Color(0xFF26272A)

/** Slow, soft shimmer over a flat grey block. */
fun Modifier.skeleton(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonX",
    )
    val start = x * 1600f - 500f
    background(Brush.linearGradient(listOf(SkelBase, SkelHi, SkelBase), start = Offset(start, 0f), end = Offset(start + 500f, 120f)))
}

@Composable
fun SkelBox(modifier: Modifier, radius: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(radius)).skeleton())
}

@Composable
fun SkelCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).skeleton())
}

/** Horizontal row of big stream cards (Home: Featured Creators, Streams For You, category rows). */
@Composable
fun SkeletonStreamRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState(), enabled = false),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Column(Modifier.width(300.dp)) {
                SkelBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 14.dp)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkelCircle(40.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        SkelBox(Modifier.width(110.dp).height(12.dp), 6.dp)
                        Spacer(Modifier.height(6.dp))
                        SkelBox(Modifier.width(170.dp).height(12.dp), 6.dp)
                    }
                }
            }
        }
    }
}

/** Horizontal row of tall category cards. */
@Composable
fun SkeletonCatRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState(), enabled = false),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(4) { SkelBox(Modifier.width(110.dp).aspectRatio(3f / 4f), 10.dp) }
    }
}

/** 3 x 4 grid of category cards (Browse > Categories). */
@Composable
fun SkeletonCatGrid() {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { SkelBox(Modifier.weight(1f).aspectRatio(3f / 4f), 10.dp) }
            }
        }
    }
}

/** Full-width cards, one under the other (Browse > Livestreams / Clips, Following, search, category). */
@Composable
fun SkeletonList(count: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Column {
                SkelBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 14.dp)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkelCircle(40.dp)
                    Column(Modifier.padding(start = 10.dp)) {
                        SkelBox(Modifier.width(120.dp).height(12.dp), 6.dp)
                        Spacer(Modifier.height(6.dp))
                        SkelBox(Modifier.width(200.dp).height(12.dp), 6.dp)
                    }
                }
            }
        }
    }
}

/** Channel page: banner, avatar + name, buttons, tabs. */
@Composable
fun SkeletonProfile() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(150.dp).skeleton())
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SkelCircle(60.dp)
            Column(Modifier.padding(start = 12.dp)) {
                SkelBox(Modifier.width(140.dp).height(18.dp), 8.dp)
                Spacer(Modifier.height(8.dp))
                SkelBox(Modifier.width(210.dp).height(13.dp), 6.dp)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SkelCircle(44.dp)
            SkelCircle(44.dp)
            SkelBox(Modifier.weight(1f).height(44.dp), 22.dp)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(5) { SkelBox(Modifier.width(48.dp).height(14.dp), 6.dp) }
        }
        Spacer(Modifier.height(24.dp))
        SkelBox(Modifier.padding(horizontal = 16.dp).width(170.dp).height(20.dp), 8.dp)
        Spacer(Modifier.height(12.dp))
        SkelBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f), 14.dp)
    }
}

/** Watch page: player area and chat lines. */
@Composable
fun SkeletonWatch() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).skeleton())
        SkeletonChat()
    }
}

/** A few chat lines of different length. */
@Composable
fun SkeletonChat() {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(0.55f, 0.8f, 0.4f, 0.7f, 0.5f, 0.85f, 0.45f, 0.65f).forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkelBox(Modifier.width(70.dp).height(13.dp), 6.dp)
                Spacer(Modifier.width(8.dp))
                SkelBox(Modifier.fillMaxWidth(w).height(13.dp), 6.dp)
            }
        }
    }
}
