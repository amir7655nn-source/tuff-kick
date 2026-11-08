package com.tuff.kick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val PointsGreen = Color(0xFFA6F58F)
private val Dark = Color(0xFF26272B)
private val OutColors = listOf(Color(0xFF2FD39A), Color(0xFFFF8A9B), Color(0xFF4DA3FF))

/** 3802 -> "3.8K", 7500 -> "7.5K", 3000 -> "3K". */
fun compactNum(n: Long): String {
    fun f(v: Double, s: String) = String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0") + s
    return when {
        n >= 1_000_000 -> f(n / 1_000_000.0, "M")
        n >= 1000 -> f(n / 1000.0, "K")
        else -> n.toString()
    }
}

private fun rewardColor(hex: String): Color {
    if (hex.isEmpty()) return Color(0xFF00F2FF)
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        Color(0xFF00F2FF)
    }
}

@Composable
private fun BalancePill(balance: Int?, onInfo: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Palette.Raised).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_points), null, tint = PointsGreen, modifier = Modifier.size(28.dp))
            Text(
                if (balance != null) compactNum(balance.toLong()) else "-", color = PointsGreen,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.padding(start = 6.dp),
            )
        }
        Box(Modifier.align(Alignment.CenterEnd).size(36.dp).clip(CircleShape).clickable { onInfo() }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Text("i", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RewardTile(r: Browse.ViewerReward, modifier: Modifier, height: Int) {
    Box(
        modifier.height(height.dp).clip(RoundedCornerShape(12.dp)).background(rewardColor(r.color)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_chest), null, tint = Color.Black, modifier = Modifier.size(30.dp))
            Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(Dark).padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text(compactNum(r.cost.toLong()), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            }
        }
    }
}

@Composable
fun PointsSheet(slug: String, balance: Int?, onClose: () -> Unit, onBalance: (Int?) -> Unit, startOnPrediction: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = rememberLoader("vrewards", slug) { Browse.viewerRewards(slug) }
    val pl = rememberLoader("lastpred", slug) { ChatExtras.latest(slug) }
    var pick by remember { mutableStateOf<Browse.ViewerReward?>(null) }
    var showPred by remember { mutableStateOf(startOnPrediction) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf(-1) }
    var amount by remember { mutableStateOf("") }
    val p = pick
    val title = when {
        showPred -> "Predictions"
        p != null -> p.title
        else -> "Channel Points"
    }
    val back: (() -> Unit)? = if (showPred) { { showPred = false } } else if (p != null) { { pick = null; input = "" } } else null
    val toast = { m: String -> android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_LONG).show() }

    Sheet(title, onClose = onClose, onBack = back) {
        BalancePill(balance) { toast("Earn points by watching and chatting in this channel.") }
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

        if (showPred) {
            val pred = (pl.state as? Load.Ok<*>)?.data as? Prediction
            Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
                if (pred == null) {
                    Text(
                        if (pl.state is Load.Ok<*>) "No predictions in this channel yet." else "Loading…",
                        color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                } else {
                    val total = pred.outcomes.sumOf { it.amount }
                    val open = ChatExtras.isOpen(pred)
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pred.title.ifEmpty { "Prediction" }, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textAlign = TextAlign.Center)
                        Text(
                            java.text.NumberFormat.getIntegerInstance().format(total) + " points contributed",
                            color = Palette.Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val wi = pred.outcomes.indexOfFirst { it.id.isNotEmpty() && it.id == pred.winnerId }
                    if (!open && wi >= 0 && total > 0) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Line, RoundedCornerShape(12.dp)).padding(vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Winner: ", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Box(Modifier.size(30.dp).clip(CircleShape).background(OutColors[wi % 3]), contentAlignment = Alignment.Center) {
                                    Text((wi + 1).toString(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            Text(
                                Math.round(pred.outcomes[wi].amount * 100.0 / total).toString() + "%",
                                color = OutColors[wi % 3], fontWeight = FontWeight.ExtraBold, fontSize = 44.sp,
                            )
                        }
                    }
                    Text("Outcomes", color = Palette.Muted, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    pred.outcomes.forEachIndexed { i, o ->
                        val won = !open && wi == i
                        val sel = open && outcome == i
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (won || sel) Color(0xFF30333A) else Color(0xFF1C1D20))
                                .clickable(enabled = open) { outcome = i }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(OutColors[i % 3]), contentAlignment = Alignment.Center) {
                                Text((i + 1).toString(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                o.title, color = if (open || won) Palette.Text else Color(0xFF55575C), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            if (won) Text("Winner", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Row(Modifier.padding(start = 6.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_points), null, tint = OutColors[i % 3], modifier = Modifier.size(22.dp))
                            Text(
                                " " + java.text.NumberFormat.getIntegerInstance().format(o.amount), color = Palette.Text, fontSize = 22.sp,
                                modifier = Modifier.padding(end = 14.dp),
                            )
                            Icon(painterResource(R.drawable.ic_filter), null, tint = OutColors[i % 3], modifier = Modifier.size(20.dp))
                            Text(
                                " 1:" + (if (o.amount > 0) String.format(java.util.Locale.US, "%.1f", total.toDouble() / o.amount).removeSuffix(".0") else "-"),
                                color = Palette.Text, fontSize = 22.sp,
                            )
                        }
                    }
                    if (open) {
                        TextField(
                            value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
                            placeholder = { Text("Points to predict") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                                focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Palette.Accent,
                            ),
                        )
                        val amt = amount.toIntOrNull() ?: 0
                        val ok = outcome >= 0 && amt > 0 && (balance == null || amt <= balance) && !busy
                        Box(
                            Modifier.fillMaxWidth().padding(top = 4.dp).height(54.dp).clip(CircleShape)
                                .background(if (ok) Palette.Accent else Color(0xFF1E1F22))
                                .clickable(enabled = ok) {
                                    busy = true
                                    scope.launch {
                                        val msg = try {
                                            Browse.vote(slug, pred.id, pred.outcomes[outcome].id, amt)
                                            onBalance(try { Browse.myPoints(slug) } catch (e: Exception) { null })
                                            pl.load()
                                            "Prediction placed"
                                        } catch (e: KickException) { e.code.take(100) } catch (e: Exception) { "Failed" }
                                        busy = false
                                        toast(msg)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (outcome < 0) "Pick an outcome" else if (amt <= 0) "Enter points" else "Predict " + compactNum(amt.toLong()),
                                color = if (ok) Palette.AccentInk else Palette.Muted, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            )
                        }
                    }
                }
            }
        } else if (p != null) {
            Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
                RewardTile(p, Modifier.fillMaxWidth().padding(horizontal = 4.dp), 124)
                if (p.description.isNotEmpty()) Text(p.description, color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                if (p.needsInput) TextField(
                    value = input, onValueChange = { input = it }, placeholder = { Text("Your message") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                        focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Palette.Accent,
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
            val enough = balance != null && balance >= p.cost
            val canRedeem = enough && p.enabled && !busy && (!p.needsInput || input.isNotBlank())
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(if (enough && p.enabled) Palette.Accent else Color(0xFF1E1F22))
                    .clickable(enabled = canRedeem) {
                        busy = true
                        scope.launch {
                            val msg = try {
                                Browse.redeem(slug, p.id, input)
                                onBalance(try { Browse.myPoints(slug) } catch (e: Exception) { null })
                                pick = null; input = ""
                                "Redeemed"
                            } catch (e: KickException) { e.code.take(100) } catch (e: Exception) { "Failed" }
                            busy = false
                            toast(msg)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        !p.enabled -> "Paused"
                        balance == null -> "Log in to redeem"
                        enough -> "Redeem"
                        else -> compactNum((p.cost - balance).toLong()) + " points to go"
                    },
                    color = if (enough && p.enabled) Palette.AccentInk else Palette.Muted, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                )
            }
        } else {
            Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
                val last = (pl.state as? Load.Ok<*>)?.data as? Prediction
                if (last != null) {
                    val total = last.outcomes.sumOf { it.amount }
                    val open = ChatExtras.isOpen(last)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Dark)
                            .clickable { showPred = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_points), null, tint = Color.White, modifier = Modifier.size(34.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(last.title.ifEmpty { "Prediction" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (open) "Open  " else "Ended  ", color = Palette.Muted, fontSize = 18.sp)
                                Icon(painterResource(R.drawable.ic_points), null, tint = PointsGreen, modifier = Modifier.size(18.dp))
                                Text(" " + compactNum(total), color = PointsGreen, fontSize = 22.sp)
                                Text(if (open) " in play" else " distributed", color = Palette.Muted, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LoadView(l) { list ->
                    if (list.isEmpty()) Text("This channel has no rewards.", color = Palette.Muted, modifier = Modifier.padding(12.dp))
                    list.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { r ->
                                Column(Modifier.weight(1f).alpha(if (r.enabled) 1f else 0.4f).clickable { pick = r }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    RewardTile(r, Modifier.fillMaxWidth(), 104)
                                    Text(
                                        r.title, color = Palette.Text, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
