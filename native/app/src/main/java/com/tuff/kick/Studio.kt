package com.tuff.kick

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun field(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
            focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
            focusedIndicatorColor = Palette.Accent, unfocusedIndicatorColor = Palette.Raised,
            focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.Muted, cursorColor = Palette.Accent,
        ),
    )
}

@Composable
private fun pill(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (filled) Palette.Accent else Palette.Raised)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) Palette.AccentInk else Palette.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

/** Creator tools built only on the official Kick API: stream info, channel points, KICKs leaderboard, live count. */
@Composable
fun StudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun run(okMsg: String, block: suspend () -> Unit) {
        scope.launch {
            val t = try { block(); okMsg } catch (e: KickException) { "Failed: " + e.code.take(90) } catch (e: Exception) { "Failed" }
            toast(t)
            tick++
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Palette.Text) }
            Text("Creator tools", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Tabs(listOf("Channel", "Points", "Requests", "KICKs"), tab, { tab = it }, Modifier.padding(bottom = 8.dp))
        when (tab) {
            0 -> ChannelTab(tick) { m, b -> run(m, b) }
            1 -> RewardsTab(tick) { m, b -> run(m, b) }
            2 -> RequestsTab(tick) { m, b -> run(m, b) }
            else -> LeaderTab()
        }
    }
}

@Composable
private fun ChannelTab(tick: Int, run: (String, suspend () -> Unit) -> Unit) {
    val l = rememberLoader("my-channel", tick) {
        val c = PublicApi.myChannel()
        val live = try { PublicApi.liveCount() } catch (e: Exception) { -1 }
        Pair(c, live)
    }
    LoadView(l) { (c, live) ->
        var title by remember(c.title) { mutableStateOf(c.title) }
        var catQ by remember { mutableStateOf("") }
        var results by remember { mutableStateOf(listOf<Cat>()) }
        var picked by remember(c.categoryId) { mutableStateOf<Cat?>(null) }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(c.slug, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(
                (if (c.live) "LIVE · ${fmtViewers(c.viewers)} viewers" else "Offline") + (if (live >= 0) "  ·  $live channels live on Kick" else ""),
                color = if (c.live) Palette.Accent else Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            if (c.description.isNotEmpty()) Text(c.description, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
            field(title, "Stream title", { title = it })
            Text("Category: " + (picked?.name ?: c.category.ifEmpty { "-" }), color = Palette.Text, modifier = Modifier.padding(top = 8.dp))
            field(catQ, "Search a new category", { catQ = it; scope.launch { results = try { PublicApi.categories(it, 8) } catch (e: Exception) { emptyList() } } })
            results.forEach { cat ->
                OptionRow(cat.name, checked = picked?.id == cat.id) { picked = cat; results = emptyList(); catQ = "" }
            }
            Button(
                onClick = { run("Saved") { PublicApi.updateChannel(title, picked?.id) } },
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
            ) { Text("Save changes", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RewardsTab(tick: Int, run: (String, suspend () -> Unit) -> Unit) {
    val l = rememberLoader("rewards", tick) { PublicApi.rewards() }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Channel point rewards", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            pill("+ New") { creating = true }
        }
        Box(Modifier.weight(1f)) {
            LoadView(l) { list ->
                if (list.isEmpty()) Text("No rewards yet (needs a channel with rewards enabled).", color = Palette.Muted, modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(r.cost.toString() + " points" + (if (r.needsInput) " · asks for text" else ""), color = Palette.Muted, fontSize = 13.sp)
                                if (r.description.isNotEmpty()) Text(r.description, color = Palette.Muted, fontSize = 12.sp, maxLines = 2)
                            }
                            Switch(
                                checked = r.enabled, onCheckedChange = { run(if (it) "Enabled" else "Paused") { PublicApi.setRewardEnabled(r.id, it) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.AccentInk),
                            )
                            pill("Delete", false, Modifier.padding(start = 8.dp)) { run("Deleted") { PublicApi.deleteReward(r.id) } }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var t by remember { mutableStateOf("") }
        var c by remember { mutableStateOf("100") }
        var d by remember { mutableStateOf("") }
        var ask by remember { mutableStateOf(false) }
        Sheet("New reward", onClose = { creating = false }) {
            field(t, "Title", { t = it })
            field(c, "Cost (points)", { c = it.filter(Char::isDigit) })
            field(d, "Description", { d = it })
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Viewer must write a message", color = Palette.Text, modifier = Modifier.weight(1f))
                Switch(checked = ask, onCheckedChange = { ask = it }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Palette.AccentInk))
            }
            Button(
                onClick = {
                    val cost = c.toIntOrNull() ?: 0
                    if (t.isNotBlank() && cost > 0) { run("Reward created") { PublicApi.createReward(t.trim(), cost, d.trim(), true, ask) }; creating = false }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RequestsTab(tick: Int, run: (String, suspend () -> Unit) -> Unit) {
    val l = rememberLoader("redemptions", tick) { PublicApi.redemptions() }
    LoadView(l) { list ->
        if (list.isEmpty()) Text("No pending redemptions.", color = Palette.Muted, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised).padding(12.dp)) {
                    Text(r.reward, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(r.user + (if (r.at.isNotEmpty()) " · " + r.at.take(16).replace('T', ' ') else ""), color = Palette.Muted, fontSize = 13.sp)
                    if (r.input.isNotEmpty()) Text("“" + r.input + "”", color = Palette.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pill("Accept") { run("Accepted") { PublicApi.decideRedemption(r.id, true) } }
                        pill("Reject", false) { run("Rejected") { PublicApi.decideRedemption(r.id, false) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderTab() {
    var period by remember { mutableIntStateOf(0) }
    val l = rememberLoader("leaderboard") { PublicApi.leaderboard(25) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Week", "Month", "Lifetime").forEachIndexed { i, n -> pill(n, period == i) { period = i } }
        }
        LoadView(l) { lb ->
            val rows = when (period) { 0 -> lb.week; 1 -> lb.month; else -> lb.lifetime }
            if (rows.isEmpty()) Text("No data.", color = Palette.Muted, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.rank.toString() + it.name }) { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#" + r.rank, color = Palette.Accent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 12.dp))
                        Text(r.name, color = Palette.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(r.amount.toString() + " KICKs", color = Palette.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
