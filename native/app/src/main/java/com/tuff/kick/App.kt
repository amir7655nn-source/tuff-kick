package com.tuff.kick

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Route {
    data class Watch(val slug: String) : Route
    data class Profile(val slug: String) : Route
    data class Category(val slug: String, val name: String) : Route
    object Studio : Route
    data class Play(val title: String, val url: String?, val videoId: String?, val clip: Clip?) : Route
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }       // 0 home, 1 browse, 2 following, 3 search
    var showLogin by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var showOAuth by remember { mutableStateOf(false) }
    var linkedTick by remember { mutableIntStateOf(0) }
    var oauthMsg by remember { mutableStateOf("") }
    val stack = remember { mutableStateListOf<Route>() }

    if (showLogin) {
        LoginDialog(onClose = { showLogin = false; vm.onLoginClosed() })
        return
    }

    if (showOAuth) {
        OAuthScreen(onDone = { ok ->
            showOAuth = false
            linkedTick++
            oauthMsg = if (ok) "Official Kick API connected." else "Not connected."
        })
        return
    }

    val nav = remember {
        Nav(
            watch = { stack.add(Route.Watch(it)) },
            profile = { stack.add(Route.Profile(it)) },
            category = { s, n -> stack.add(Route.Category(s, n)) },
            play = { t, u, v, c -> stack.add(Route.Play(t, u, v, c)) },
        )
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.size - 1) }
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }
    val pop: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (tab != 3) TopBar(loggedIn, username, avatar, onLogin = { showLogin = true }, onAccount = { showAccount = true })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(nav, 110.dp)
                    1 -> BrowseScreen(nav, 110.dp)
                    2 -> FollowingScreen(loggedIn, following, nav, onLogin = { showLogin = true }, onRetry = { vm.loadFollowing() }, bottomPad = 110.dp)
                    else -> SearchScreen(nav, 110.dp)
                }
            }
        }
        BottomBar(tab, onTab = { tab = it }, onPlus = { showGoTo = true }, modifier = Modifier.align(Alignment.BottomCenter))

        val r = stack.lastOrNull()
        if (r != null) {
            Box(Modifier.fillMaxSize().background(Palette.Ink).pointerInput(Unit) { }) {
                when (r) {
                    is Route.Watch -> WatchScreen(r.slug, loggedIn, onLogin = { showLogin = true }, onProfile = { nav.profile(it) }, onBack = pop)
                    is Route.Profile -> ProfileScreen(r.slug, nav, loggedIn, onLogin = { showLogin = true }, onBack = pop)
                    is Route.Category -> Box(Modifier.statusBarsPadding()) { CategoryScreen(r.slug, r.name, nav, onBack = pop) }
                    is Route.Studio -> Box(Modifier.statusBarsPadding()) { StudioScreen(onBack = pop) }
                    is Route.Play -> PlayScreen(r.title, r.url, r.videoId, r.clip, onBack = pop)
                }
            }
        }
        if (showAccount) {
            Sheet(if (username.isNotEmpty()) username else "Account", onClose = { showAccount = false }) {
                OptionRow("Sign out") { vm.logout(); showAccount = false }
                OptionRow("Refresh") { vm.refreshAll(); showAccount = false }
                val linked = remember(linkedTick) { KickOAuth.linked() }
                OptionRow(
                    "Official Kick API",
                    "Optional: only for sending via official API, moderation and creator tools",
                    trailing = if (linked) "Disconnect" else "Connect",
                ) {
                    if (linked) { KickOAuth.unlink(); linkedTick++; oauthMsg = "Disconnected." } else { showOAuth = true }
                    showAccount = false
                }
                if (linked) OptionRow("Creator tools", "Channel points, stream info, KICKs leaderboard") { showAccount = false; stack.add(Route.Studio) }
                Text("Version " + BuildConfig.VERSION_NAME, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (oauthMsg.isNotEmpty()) Text(oauthMsg, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (showGoTo) GoToDialog(onClose = { showGoTo = false }, onGo = { showGoTo = false; stack.add(Route.Profile(it)) })
    }
}

@Composable
fun TopBar(loggedIn: Boolean, username: String, avatar: String, onLogin: () -> Unit, onAccount: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("TUFF", color = Palette.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Box(Modifier.weight(1f))
        if (loggedIn) {
            Avatar(avatar, username.ifEmpty { "•" }, 42.dp, modifier = Modifier.clip(CircleShape).clickable { onAccount() })
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Raised).clickable { onLogin() }.padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Text("Sign in", color = Palette.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xF2161618)).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(if (tab == 0) R.drawable.ic_home_filled else R.drawable.ic_home_outline, tab == 0) { onTab(0) }
            BarIcon(if (tab == 1) R.drawable.ic_navigation_browse_active else R.drawable.ic_navigation_browse_inactive, tab == 1, plain = true) { onTab(1) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Palette.Raised).clickable { onPlus() },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_plus), "Go to channel", tint = Color.White, modifier = Modifier.size(26.dp)) }
            BarIcon(if (tab == 2) R.drawable.ic_navigation_following_active else R.drawable.ic_navigation_following_inactive, tab == 2, plain = true) { onTab(2) }
            BarIcon(R.drawable.ic_search, tab == 3) { onTab(3) }
        }
    }
}

@Composable
private fun BarIcon(res: Int, on: Boolean, plain: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (plain) {
            // these vectors already carry their own colours
            Icon(painterResource(res), null, tint = if (on) Palette.Accent else Color.White, modifier = Modifier.size(26.dp))
        } else {
            Icon(painterResource(res), null, tint = if (on) Palette.Accent else Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun GoToDialog(onClose: () -> Unit, onGo: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    Sheet("Go to channel", onClose = onClose) {
        TextField(
            value = t, onValueChange = { t = it }, singleLine = true, placeholder = { Text("Channel name") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
            ),
        )
        Button(
            onClick = { val s = t.trim().lowercase().replace(" ", ""); if (s.isNotEmpty()) onGo(s) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
        ) { Text("Open", fontWeight = FontWeight.ExtraBold) }
    }
}
