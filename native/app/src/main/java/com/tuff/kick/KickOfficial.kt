package com.tuff.kick

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Official Kick OAuth 2.1 (PKCE) - tokens are kept in the app's private storage.
 * The client secret never has to live in the app: when KICK_WORKER_URL is set, token requests go through
 * a small Cloudflare Worker that holds the secret. Without it the app falls back to a build-time secret.
 */
object KickOAuth {
    private const val AUTH = "https://id.kick.com/oauth/authorize"
    private const val TOKEN = "https://id.kick.com/oauth/token"
    const val REDIRECT = "https://localhost/callback"
    private const val SCOPES = "user:read channel:read channel:write channel:rewards:read channel:rewards:write chat:write moderation:ban moderation:chat_message:manage kicks:read"
    private val http = OkHttpClient()

    private val worker: String get() = BuildConfig.KICK_WORKER_URL.trim().trimEnd('/')

    private fun prefs() = TuffApp.instance.getSharedPreferences("kick_oauth", Context.MODE_PRIVATE)

    fun linked(): Boolean = prefs().getString("refresh", "").orEmpty().isNotEmpty()

    /** True when official data can be read: the user connected, or the app can get its own app token. */
    fun available(): Boolean = linked() || worker.isNotEmpty() || BuildConfig.KICK_CLIENT_SECRET.isNotEmpty()

    @Volatile var lastError: String = ""

    /** One token request, through the worker when configured, directly otherwise. Returns (http status, body). */
    private suspend fun tokenCall(grant: String, fields: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val req = if (worker.isNotEmpty()) {
            val path = when (grant) { "client_credentials" -> "/app-token"; "authorization_code" -> "/token"; else -> "/refresh" }
            if (grant == "client_credentials") {
                Request.Builder().url(worker + path).get().build()
            } else {
                val j = JSONObject()
                fields.forEach { (k, v) -> j.put(k, v) }
                Request.Builder().url(worker + path).post(j.toString().toRequestBody("application/json".toMediaType())).build()
            }
        } else {
            val fb = FormBody.Builder()
                .add("grant_type", grant)
                .add("client_id", BuildConfig.KICK_CLIENT_ID)
                .add("client_secret", BuildConfig.KICK_CLIENT_SECRET)
            fields.forEach { (k, v) -> fb.add(k, v) }
            Request.Builder().url(TOKEN).post(fb.build()).build()
        }
        try {
            http.newCall(req).execute().use { r -> Pair(r.code, r.body?.string().orEmpty()) }
        } catch (e: Exception) {
            Pair(0, e.message ?: e.javaClass.simpleName)
        }
    }

    /** App access token (client_credentials): public data only, no user login needed. */
    suspend fun appToken(force: Boolean = false): String? {
        val p = prefs()
        val tok = p.getString("app_access", "").orEmpty()
        if (!force && tok.isNotEmpty() && System.currentTimeMillis() < p.getLong("app_expires", 0L)) return tok
        if (worker.isEmpty() && BuildConfig.KICK_CLIENT_SECRET.isEmpty()) return null
        val (code, text) = tokenCall("client_credentials", emptyMap())
        if (code !in 200..299) { lastError = "app token http $code " + text.take(120); return null }
        return try {
            val j = JSONObject(text)
            val a = j.optString("access_token")
            if (a.isEmpty()) return null
            val exp = System.currentTimeMillis() + (j.optLong("expires_in", 3600L) - 60L) * 1000L
            p.edit().putString("app_access", a).putLong("app_expires", exp).apply()
            a
        } catch (e: Exception) {
            null
        }
    }

    fun unlink() {
        val access = prefs().getString("access", "").orEmpty()
        prefs().edit().clear().apply()
        if (access.isNotEmpty()) {
            // tell Kick the token is no longer wanted (best effort, in the background)
            Thread {
                try {
                    val req = Request.Builder()
                        .url("https://id.kick.com/oauth/revoke?token=" + java.net.URLEncoder.encode(access, "UTF-8") + "&token_hint_type=access_token")
                        .post(ByteArray(0).toRequestBody(null)).build()
                    http.newCall(req).execute().close()
                } catch (e: Exception) {
                }
            }.start()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun authUrl(): String {
        val rnd = SecureRandom()
        val vb = ByteArray(48).also { rnd.nextBytes(it) }
        val sb = ByteArray(16).also { rnd.nextBytes(it) }
        val verifier = b64(vb)
        val state = b64(sb)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        prefs().edit().putString("verifier", verifier).putString("state", state).apply()
        val e = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return AUTH + "?response_type=code&client_id=" + e(BuildConfig.KICK_CLIENT_ID) +
            "&redirect_uri=" + e(REDIRECT) + "&scope=" + e(SCOPES) +
            "&code_challenge=" + e(challenge) + "&code_challenge_method=S256&state=" + e(state)
    }

    private suspend fun userToken(grant: String, fields: Map<String, String>): Boolean {
        val (code, text) = tokenCall(grant, fields)
        if (code !in 200..299) { lastError = "token http $code " + text.take(160); return false }
        return try {
            val j = JSONObject(text)
            val access = j.optString("access_token")
            if (access.isEmpty()) { lastError = "token answer had no access_token"; return false }
            val refresh = j.optString("refresh_token").ifEmpty { prefs().getString("refresh", "").orEmpty() }
            val exp = System.currentTimeMillis() + (j.optLong("expires_in", 3600L) - 60L) * 1000L
            prefs().edit().putString("access", access).putString("refresh", refresh).putLong("expires", exp).apply()
            true
        } catch (e: Exception) {
            lastError = "token " + (e.message ?: e.javaClass.simpleName)
            false
        }
    }

    /** Called with the redirect URL (https://localhost/callback?code=...&state=...). */
    suspend fun finish(url: String): Boolean {
        val raw = url.trim()
        val uri = android.net.Uri.parse(raw)
        val pasteCodeOnly = !raw.contains("=") && !raw.contains("/") && raw.length > 8
        lastError = ""
        val code = uri.getQueryParameter("code") ?: (if (pasteCodeOnly) raw else run { lastError = "no code in the pasted text"; return false })
        val state = uri.getQueryParameter("state")
        val p = prefs()
        if (!pasteCodeOnly && state != p.getString("state", null)) { lastError = "state mismatch (start Connect again and use the newest link)"; return false }
        if (worker.isEmpty() && BuildConfig.KICK_CLIENT_SECRET.isEmpty()) { lastError = "this build has neither KICK_WORKER_URL nor a client secret"; return false }
        return userToken(
            "authorization_code",
            mapOf("redirect_uri" to REDIRECT, "code_verifier" to p.getString("verifier", "").orEmpty(), "code" to code),
        )
    }

    suspend fun accessToken(): String? {
        val p = prefs()
        val access = p.getString("access", "").orEmpty()
        if (access.isNotEmpty() && System.currentTimeMillis() < p.getLong("expires", 0L)) return access
        return refresh()
    }

    suspend fun refresh(): String? {
        val p = prefs()
        val rt = p.getString("refresh", "").orEmpty()
        if (rt.isEmpty()) return null
        return if (userToken("refresh_token", mapOf("refresh_token" to rt))) p.getString("access", null) else null
    }
}

data class MyChan(val id: Long, val slug: String, val title: String, val category: String, val categoryId: Int, val live: Boolean, val viewers: Int, val description: String)
data class Reward(val id: String, val title: String, val cost: Int, val description: String, val enabled: Boolean, val color: String, val needsInput: Boolean)
data class Redemption(val id: String, val reward: String, val user: String, val input: String, val status: String, val at: String)
data class LbRow(val rank: Int, val name: String, val amount: Int)
data class Leaderboard(val week: List<LbRow>, val month: List<LbRow>, val lifetime: List<LbRow>)
data class OfficialUser(val id: Long, val name: String, val email: String, val avatar: String)

/** Kick's documented public API (api.kick.com). Only used when the user has connected the official API. */
object PublicApi {
    private const val BASE = "https://api.kick.com"
    private val http = OkHttpClient()

    private suspend fun call(method: String, path: String, json: String? = null): Pair<Int, String> {
        var token = KickOAuth.accessToken() ?: KickOAuth.appToken() ?: throw KickException("not_linked")
        for (attempt in 0..1) {
            val code = withContext(Dispatchers.IO) {
                val b = Request.Builder().url(BASE + path).header("Authorization", "Bearer $token").header("Accept", "application/json")
                if (json != null) b.method(method, json.toRequestBody("application/json".toMediaType()))
                else if (method == "GET") b.get() else b.method(method, ByteArray(0).toRequestBody(null))
                try {
                    http.newCall(b.build()).execute().use { r -> Pair(r.code, r.body?.string().orEmpty()) }
                } catch (e: Exception) {
                    Pair(0, e.message.orEmpty())
                }
            }
            if (code.first == 401 && attempt == 0) {
                token = (if (KickOAuth.linked()) KickOAuth.refresh() else KickOAuth.appToken(true)) ?: throw KickException("not_linked")
                continue
            }
            return code
        }
        throw KickException("unreachable")
    }

    private suspend fun ok(method: String, path: String, json: String? = null): String {
        val (code, text) = call(method, path, json)
        if (code !in 200..299) {
            val msg = try { JSONObject(text).optString("message") } catch (e: Exception) { "" }
            throw KickException("api_$code" + (if (msg.isNotEmpty()) " $msg" else ""))
        }
        return text
    }

    private fun data(text: String): org.json.JSONArray? = try { JSONObject(text).optJSONArray("data") } catch (e: Exception) { null }

    private fun thumbOf(v: Any?): String = when (v) {
        is JSONObject -> v.optString("src").ifEmpty { v.optString("url") }
        null, JSONObject.NULL -> ""
        else -> v.toString()
    }

    private fun streamFrom(o: JSONObject): Stream? {
        val slug = o.optString("slug")
        if (slug.isEmpty()) return null
        val st = o.optJSONObject("stream")
        if (st != null && !st.optBoolean("is_live", true)) return null
        val cat = o.optJSONObject("category")
        return Stream(
            slug = slug,
            name = slug,
            viewers = st?.optInt("viewer_count", 0) ?: o.optInt("viewer_count", 0),
            title = o.optString("stream_title"),
            category = cat?.optString("name").orEmpty(),
            categorySlug = "",
            thumb = thumbOf(st?.opt("thumbnail") ?: o.opt("thumbnail")),
            avatar = o.optString("profile_picture"),
            language = (st?.optString("language") ?: o.optString("language")).orEmpty(),
            userId = o.optLong("broadcaster_user_id", 0L),
        )
    }

    /** Live info (thumbnail, title, language, viewers) for many channels at once. Offline channels are left out. */
    suspend fun channels(slugs: List<String>): Map<String, Stream> {
        val out = HashMap<String, Stream>()
        for (chunk in slugs.chunked(40)) {
            val q = chunk.joinToString("&") { "slug=" + java.net.URLEncoder.encode(it, "UTF-8") }
            val arr = data(ok("GET", "/public/v1/channels?$q")) ?: continue
            for (i in 0 until arr.length()) {
                val s = streamFrom(arr.optJSONObject(i) ?: continue) ?: continue
                out[s.slug.lowercase()] = s
            }
        }
        // /channels has no thumbnail: /livestreams (filtered by broadcaster id) does
        val need = out.values.filter { it.thumb.isEmpty() && it.userId > 0 }.map { it.userId }
        for (chunk in need.chunked(50)) {
            try {
                val q = chunk.joinToString("&") { "broadcaster_user_id=$it" }
                val arr = data(ok("GET", "/public/v1/livestreams?$q&limit=50")) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val s = streamFrom(o) ?: continue
                    val cur = out[s.slug.lowercase()] ?: continue
                    out[s.slug.lowercase()] = cur.copy(thumb = s.thumb.ifEmpty { cur.thumb }, viewers = if (s.viewers > 0) s.viewers else cur.viewers)
                }
            } catch (e: KickException) {
            }
        }
        return out
    }

    /** GET /livestreams with the official language / category filters. */
    suspend fun livestreams(language: String?, limit: Int = 50, categoryId: Int = 0): List<Stream> {
        val q = StringBuilder("/public/v1/livestreams?sort=viewer_count&limit=$limit")
        if (!language.isNullOrEmpty()) q.append("&language=").append(java.net.URLEncoder.encode(language, "UTF-8"))
        if (categoryId > 0) q.append("&category_id=").append(categoryId)
        val arr = data(ok("GET", q.toString())) ?: return emptyList()
        val out = ArrayList<Stream>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = streamFrom(o) ?: continue
            out.add(s)
        }
        return out
    }

    /** How many channels are live right now. */
    suspend fun liveCount(): Int {
        val text = ok("GET", "/public/v1/livestreams/stats")
        fun find(v: Any?): Int? = when (v) {
            is Number -> v.toInt()
            is JSONObject -> v.keys().asSequence().mapNotNull { find(v.opt(it)) }.firstOrNull()
            is org.json.JSONArray -> (0 until v.length()).asSequence().mapNotNull { find(v.opt(it)) }.firstOrNull()
            else -> null
        }
        return find(JSONObject(text).opt("data")) ?: 0
    }

    /** Profile pictures for many user ids at once. */
    suspend fun users(ids: List<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        for (chunk in ids.chunked(50)) {
            val q = chunk.joinToString("&") { "id=$it" }
            val arr = data(ok("GET", "/public/v1/users?$q")) ?: continue
            for (i in 0 until arr.length()) {
                val u = arr.optJSONObject(i) ?: continue
                val p = u.optString("profile_picture")
                if (p.isNotEmpty()) out[u.optLong("user_id")] = p
            }
        }
        return out
    }

    suspend fun me(): OfficialUser? {
        val o = data(ok("GET", "/public/v1/users"))?.optJSONObject(0) ?: return null
        return OfficialUser(o.optLong("user_id"), o.optString("name"), o.optString("email"), o.optString("profile_picture"))
    }

    /** Categories (v2) with thumbnails; optional name search. */
    suspend fun categories(name: String? = null, limit: Int = 100): List<Cat> {
        val q = StringBuilder("/public/v2/categories?limit=$limit")
        if (!name.isNullOrEmpty()) q.append("&name=").append(java.net.URLEncoder.encode(name, "UTF-8"))
        val arr = data(ok("GET", q.toString())) ?: return emptyList()
        val out = ArrayList<Cat>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val nm = o.optString("name")
            if (nm.isEmpty()) continue
            val tags = ArrayList<String>()
            o.optJSONArray("tags")?.let { t -> for (j in 0 until minOf(t.length(), 4)) tags.add(t.optString(j)) }
            out.add(Cat(nm.lowercase().replace(" ", "-"), nm, 0, thumbOf(o.opt("thumbnail")), tags, o.optInt("id")))
        }
        return out
    }

    // ---- chat + moderation ----
    suspend fun sendChat(broadcasterId: Long, text: String, replyTo: String?) {
        val j = JSONObject().put("broadcaster_user_id", broadcasterId).put("content", text).put("type", "user")
        if (!replyTo.isNullOrEmpty()) j.put("reply_to_message_id", replyTo)
        ok("POST", "/public/v1/chat", j.toString())
    }

    suspend fun deleteMessage(messageId: String) {
        ok("DELETE", "/public/v1/chat/" + java.net.URLEncoder.encode(messageId, "UTF-8"))
    }

    /** minutes == null -> permanent ban; otherwise a timeout of 1..10080 minutes. */
    suspend fun ban(broadcasterId: Long, userId: Long, minutes: Int?, reason: String = "") {
        val j = JSONObject().put("broadcaster_user_id", broadcasterId).put("user_id", userId)
        if (reason.isNotEmpty()) j.put("reason", reason)
        if (minutes != null) j.put("duration", minutes)
        ok("POST", "/public/v1/moderation/bans", j.toString())
    }

    suspend fun unban(broadcasterId: Long, userId: Long) {
        val j = JSONObject().put("broadcaster_user_id", broadcasterId).put("user_id", userId)
        ok("DELETE", "/public/v1/moderation/bans", j.toString())
    }

    // ---- my channel ----
    suspend fun myChannel(): MyChan {
        val o = data(ok("GET", "/public/v1/channels"))?.optJSONObject(0) ?: throw KickException("no_channel")
        val st = o.optJSONObject("stream")
        val cat = o.optJSONObject("category")
        return MyChan(
            id = o.optLong("broadcaster_user_id"), slug = o.optString("slug"), title = o.optString("stream_title"),
            category = cat?.optString("name").orEmpty(), categoryId = cat?.optInt("id") ?: 0,
            live = st?.optBoolean("is_live") ?: false, viewers = st?.optInt("viewer_count") ?: 0,
            description = o.optString("channel_description"),
        )
    }

    suspend fun updateChannel(title: String?, categoryId: Int?) {
        val j = JSONObject()
        if (title != null) j.put("stream_title", title)
        if (categoryId != null && categoryId > 0) j.put("category_id", categoryId)
        ok("PATCH", "/public/v1/channels", j.toString())
    }

    // ---- channel points rewards ----
    suspend fun rewards(): List<Reward> {
        val arr = data(ok("GET", "/public/v1/channels/rewards")) ?: return emptyList()
        val out = ArrayList<Reward>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Reward(
                    o.opt("id").toString(), o.optString("title"), o.optInt("cost"), o.optString("description"),
                    o.optBoolean("is_enabled", true), o.optString("background_color"), o.optBoolean("is_user_input_required"),
                )
            )
        }
        return out
    }

    suspend fun createReward(title: String, cost: Int, description: String, enabled: Boolean, needsInput: Boolean) {
        val j = JSONObject().put("title", title).put("cost", cost).put("description", description.ifEmpty { title })
            .put("is_enabled", enabled).put("is_user_input_required", needsInput).put("background_color", "#53FC18")
        ok("POST", "/public/v1/channels/rewards", j.toString())
    }

    suspend fun setRewardEnabled(id: String, enabled: Boolean) {
        ok("PATCH", "/public/v1/channels/rewards/$id", JSONObject().put("is_enabled", enabled).toString())
    }

    suspend fun deleteReward(id: String) {
        ok("DELETE", "/public/v1/channels/rewards/$id")
    }

    suspend fun redemptions(): List<Redemption> {
        val text = try { ok("GET", "/public/v1/channels/rewards/redemptions?status=pending") } catch (e: KickException) { ok("GET", "/public/v1/channels/rewards/redemptions") }
        val arr = data(text) ?: return emptyList()
        val out = ArrayList<Redemption>()
        fun add(r: JSONObject, rewardTitle: String) {
            val who = r.optJSONObject("redeemer") ?: r.optJSONObject("user")
            out.add(
                Redemption(
                    r.opt("id").toString(),
                    rewardTitle.ifEmpty { r.optJSONObject("reward")?.optString("title").orEmpty() },
                    who?.optString("username").orEmpty().ifEmpty { r.optString("username") },
                    r.optString("user_input"), r.optString("status"), r.optString("redeemed_at"),
                )
            )
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val inner = o.optJSONArray("redemptions")
            if (inner != null) {
                val title = o.optJSONObject("reward")?.optString("title").orEmpty().ifEmpty { o.optString("title") }
                for (j in 0 until inner.length()) inner.optJSONObject(j)?.let { add(it, title) }
            } else add(o, "")
        }
        return out
    }

    suspend fun decideRedemption(id: String, accept: Boolean) {
        val j = JSONObject().put("ids", org.json.JSONArray().put(id))
        ok("POST", "/public/v1/channels/rewards/redemptions/" + (if (accept) "accept" else "reject"), j.toString())
    }

    // ---- KICKs leaderboard ----
    suspend fun leaderboard(top: Int = 20): Leaderboard {
        val d = JSONObject(ok("GET", "/public/v1/kicks/leaderboard?top=$top")).optJSONObject("data") ?: throw KickException("no_data")
        fun rows(k: String): List<LbRow> {
            val a = d.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { LbRow(it.optInt("rank"), it.optString("username"), it.optInt("gifted_amount")) }
        }
        return Leaderboard(rows("week"), rows("month"), rows("lifetime"))
    }
}
