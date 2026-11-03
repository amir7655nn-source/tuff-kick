package com.tuff.kick

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

fun fmtViewers(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}

@Composable
fun Message(title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
        ) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun LoginDialog(onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needCode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var web by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (web) {
        WebLogin(onClose)
        return
    }
    val fieldColors = androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
    )
    Column(
        Modifier.fillMaxSize().background(Palette.Ink).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sign in to Kick", color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        Text("Use your Kick email and password.", color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        androidx.compose.material3.TextField(
            value = email, onValueChange = { email = it }, singleLine = true,
            placeholder = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp), colors = fieldColors,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
        )
        Box(Modifier.size(10.dp))
        androidx.compose.material3.TextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            placeholder = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp), colors = fieldColors,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        )
        if (needCode) {
            Box(Modifier.size(10.dp))
            androidx.compose.material3.TextField(
                value = code, onValueChange = { code = it }, singleLine = true,
                placeholder = { Text("Two-factor code") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), colors = fieldColors,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
        }
        if (error.isNotEmpty()) {
            Text(error, color = Color(0xFFFF4F4D), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                error = ""
                scope.launch {
                    val r = try {
                        KickLogin.login(email.trim(), pass, code.trim())
                    } catch (e: Exception) {
                        LoginResult.Fail(e.message ?: "error")
                    }
                    busy = false
                    when (r) {
                        is LoginResult.Ok -> onClose()
                        is LoginResult.NeedCode -> { needCode = true; error = "Enter the code from your authenticator app." }
                        is LoginResult.Fail -> error = r.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
        ) {
            if (busy) CircularProgressIndicator(color = Palette.AccentInk, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("Sign in", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Text(
            "Use the Kick website instead", color = Palette.Muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 16.dp).clickable { web = true },
        )
        Text("Cancel", color = Palette.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp).clickable { onClose() })
    }
}

@Composable
fun WebLogin(onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    run {
        Box(Modifier.fillMaxSize().background(Palette.Ink).systemBarsPadding()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                // Kick's login box can end up off-screen in a WebView: bring it into view
                                view.evaluateJavascript(
                                    "(function(){if(window.__tuffFix)return;window.__tuffFix=1;var n=0;" +
                                        "setInterval(function(){n++;if(n>400)return;" +
                                        "var d=document.querySelector('[role=dialog],dialog[open],[aria-modal=true]');" +
                                        "if(d&&d!==window.__tuffSeen){window.__tuffSeen=d;" +
                                        "var r=d.getBoundingClientRect();" +
                                        "if(r.height<250||r.top<0||r.bottom>innerHeight){" +
                                        "d.style.maxHeight='none';d.style.height='auto';d.style.minHeight='300px';" +
                                        "}d.scrollIntoView({block:'center'});}" +
                                        "},500);})();",
                                    null,
                                )
                            }
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(KickBridge.BASE + "/")
                    }
                },
            )
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).clip(RoundedCornerShape(22.dp))
                    .background(Palette.Accent).clickable { onClose() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) { Text("Done", color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        }
    }
}

/** Official Kick sign-in (OAuth): Kick's own login page, then the redirect is caught here and exchanged for tokens. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthScreen(onDone: (Boolean) -> Unit) {
    androidx.activity.compose.BackHandler { onDone(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val authUrl = androidx.compose.runtime.saveable.rememberSaveable { KickOAuth.authUrl() }
    var busy by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var showWeb by remember { mutableStateOf(true) }

    fun finish(u: String) {
        if (busy) return
        busy = true
        err = ""
        scope.launch {
            val ok = KickOAuth.finish(u)
            busy = false
            if (ok) onDone(true) else err = "Failed: " + KickOAuth.lastError
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink).systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showWeb) AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                val u = request.url.toString()
                                if (u.startsWith(KickOAuth.REDIRECT)) { finish(u); return true }
                                return false
                            }
                        }
                        loadUrl(authUrl)
                    }
                },
            )
            if (busy) Box(Modifier.fillMaxSize().background(Palette.Ink), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Accent)
            }
        }
        Column(Modifier.fillMaxWidth().background(Palette.Panel).padding(12.dp)) {
            Text(
                "If the page above is blank: open it in your browser, press Allow, then copy the address of the final page (https://localhost/callback?...) and paste it here.",
                color = Palette.Muted, fontSize = 12.sp,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Accent)
                        .clickable { showWeb = false; try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))) } catch (e: Exception) { err = "No browser found" } }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Open in browser", color = Palette.AccentInk, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Raised)
                        .clickable { pasted = clip.getText()?.text.orEmpty() }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Paste", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.Raised)
                        .clickable { onDone(false) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Cancel", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            if (pasted.isNotEmpty()) {
                Text(pasted.take(70) + if (pasted.length > 70) "…" else "", color = Palette.Text, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Box(
                    Modifier.padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Accent)
                        .clickable { finish(pasted) }.padding(horizontal = 22.dp, vertical = 10.dp),
                ) { Text("Finish", color = Palette.AccentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            }
            if (err.isNotEmpty()) Text(err, color = androidx.compose.ui.graphics.Color(0xFFFF4F4D), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
