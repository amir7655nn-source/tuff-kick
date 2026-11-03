package com.tuff.kick

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * HTTP access to kick.com.
 *
 * Kick sits behind Cloudflare, which blocks plain HTTP clients. Like the Python version of this app, every
 * request is run as fetch() inside a hidden, real WebView that has loaded kick.com. That WebView shares the
 * app's cookie jar, so it is logged in as soon as the user signs in through the login screen.
 * Only requests go through it; all screens are native Compose.
 */
object KickBridge {
    const val BASE = "https://kick.com"

    private var webView: WebView? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Pair<Int, String>>>()
    private val counter = AtomicLong(0)

    class Callback {
        @JavascriptInterface
        fun done(id: String, status: Int, body: String) {
            pending[id]?.complete(Pair(status, body))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity: Activity) {
        val existing = webView
        if (existing != null) {
            // activity was recreated: move the hidden WebView into the new one
            (existing.parent as? ViewGroup)?.removeView(existing)
            activity.addContentView(existing, ViewGroup.LayoutParams(1, 1))
            return
        }
        val wv = WebView(activity)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(Callback(), "KCBridge")
        wv.alpha = 0.01f
        activity.addContentView(wv, ViewGroup.LayoutParams(1, 1))
        wv.loadUrl("$BASE/")
        webView = wv
    }

    private suspend fun eval(js: String): String? = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext null
        suspendCancellableCoroutine<String?> { cont ->
            wv.evaluateJavascript(js) { value -> if (cont.isActive) cont.resume(value) }
        }
    }

    private suspend fun pageReady(): Boolean {
        val js = "(function(){return location.hostname.indexOf('kick.com')>-1&&" +
            "document.readyState==='complete'&&document.title.indexOf('Just a moment')<0})()"
        return eval(js) == "true"
    }

    /** Returns (httpStatus, bodyText). Status 0 means the request itself failed. */
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Long = 20000,
    ): Pair<Int, String> {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!pageReady()) {
            if (SystemClock.elapsedRealtime() > deadline) return Pair(0, "cloudflare/page not ready")
            delay(700)
        }
        val id = "r" + counter.incrementAndGet()
        val deferred = CompletableDeferred<Pair<Int, String>>()
        pending[id] = deferred
        val h = JSONObject()
        for ((k, v) in headers) h.put(k, v)
        val bodyJs = if (body == null) "null" else JSONObject.quote(body)
        val js = "(function(){fetch(" + JSONObject.quote(url) + ",{method:" + JSONObject.quote(method) +
            ",headers:" + h.toString() + ",body:" + bodyJs + ",credentials:'include'})" +
            ".then(function(r){return r.text().then(function(t){KCBridge.done('" + id + "',r.status,t)})})" +
            ".catch(function(e){KCBridge.done('" + id + "',0,String(e))});return 1})()"
        eval(js)
        val left = maxOf(deadline - SystemClock.elapsedRealtime(), 1000L)
        val res = withTimeoutOrNull(left) { deferred.await() }
        pending.remove(id)
        return res ?: Pair(0, "timeout")
    }

    // ---- auth (cookies of the shared jar) ----
    private fun cookie(name: String): String? {
        val raw = CookieManager.getInstance().getCookie(BASE) ?: return null
        for (part in raw.split(";")) {
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0] == name) return Uri.decode(kv[1])
        }
        return null
    }

    fun token(): String? {
        val t = cookie("session_token") ?: return null
        if (t.contains("|")) {
            val parts = t.split("|", limit = 2)
            if (parts[0].isNotEmpty() && parts[0].all { it.isDigit() }) return parts[1]
        }
        return t
    }

    fun xsrf(): String? = cookie("XSRF-TOKEN")

    /** Re-load kick.com in the hidden WebView (after login/logout) so its page state is fresh. */
    fun reload() {
        webView?.post { webView?.loadUrl("$BASE/") }
    }
}
