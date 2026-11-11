package com.tuff.kick

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import org.json.JSONTokener

data class Live(
    val slug: String,
    val name: String,
    val viewers: Int,
    val title: String,
    val category: String,
    val thumb: String,
    val avatar: String,
)

class KickException(val code: String) : Exception(code)

private fun JSONObject?.str(vararg keys: String): String {
    if (this == null) return ""
    for (k in keys) {
        val v = this.opt(k)
        if (v != null && v != JSONObject.NULL) {
            val s = v.toString()
            if (s.isNotEmpty()) return s
        }
    }
    return ""
}

private fun JSONObject?.num(vararg keys: String): Int {
    if (this == null) return 0
    for (k in keys) {
        val v = this.opt(k)
        if (v is Number) return v.toInt()
        if (v is String) v.toIntOrNull()?.let { return it }
    }
    return 0
}

private fun Any?.img(): String = when (this) {
    is JSONObject -> this.str("src", "url", "original")
    is String -> this
    else -> ""
}

/**
 * Kick's private web API. These endpoints are undocumented (they are what kick.com itself calls) and can change.
 * Field names are read defensively; anything missing is simply left empty - nothing is ever invented.
 */
object KickApi {
    private const val BASE = KickBridge.BASE

    fun authHeaders(): Map<String, String> = headers(true)

    suspend fun getJsonPublic(url: String, auth: Boolean): Any = getJson(url, auth)

    private fun headers(auth: Boolean): Map<String, String> {
        val h = HashMap<String, String>()
        h["Accept"] = "application/json"
        if (auth) {
            val tok = KickBridge.token() ?: throw KickException("not_logged_in")
            h["Authorization"] = "Bearer $tok"
            KickBridge.xsrf()?.let { h["X-XSRF-TOKEN"] = it }
            h["Content-Type"] = "application/json"
        }
        return h
    }

    private suspend fun getJson(url: String, auth: Boolean): Any {
        val (status, text) = KickBridge.request("GET", url, headers(auth))
        if (status != 200) {
            val snippet = text.take(140).replace("\n", " ")
            throw KickException("http_$status $snippet")
        }
        return try {
            JSONTokener(text).nextValue()
        } catch (e: Exception) {
            throw KickException("bad_json")
        }
    }

    private fun rowsOf(d: Any, vararg keys: String): List<JSONObject> {
        val arr: JSONArray? = when (d) {
            is JSONArray -> d
            is JSONObject -> {
                var found: JSONArray? = null
                for (k in keys) {
                    val v = d.opt(k)
                    if (v is JSONArray) { found = v; break }
                }
                found
            }
            else -> null
        }
        val out = ArrayList<JSONObject>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i)
                if (o != null) out.add(o)
            }
        }
        return out
    }

    /** Username of the signed-in user, or null. */
    suspend fun me(): String? {
        if (KickBridge.token() == null) return null
        return try {
            val d = getJson("$BASE/api/v1/user", true) as? JSONObject ?: return null
            d.str("username").ifEmpty { d.optJSONObject("streamer_channel").str("slug") }.ifEmpty { null }
        } catch (e: KickException) {
            null
        }
    }

    /** (username, channel slug, avatar url) of the signed-in user, or null. */
    suspend fun meFull(): Triple<String, String, String>? {
        if (KickBridge.token() == null) return null
        return try {
            val d = getJson("$BASE/api/v1/user", true) as? JSONObject ?: return null
            val sc = d.optJSONObject("streamer_channel")
            val name = d.str("username").ifEmpty { sc.str("slug") }
            val slug = sc.str("slug").ifEmpty { name.lowercase().replace("_", "-").replace(" ", "-") }
            val avatar = d.opt("profilepic").img().ifEmpty { d.opt("profile_pic").img() }.ifEmpty { d.opt("profile_picture").img() }
                .ifEmpty { sc?.optJSONObject("user")?.opt("profile_pic").img() }
            if (name.isEmpty()) null else Triple(name, slug, avatar)
        } catch (e: KickException) {
            null
        }
    }

    /** Channels the signed-in user follows that are live right now, most viewers first. */
    suspend fun followedLive(maxPages: Int = 3): List<Live> {
        val out = ArrayList<Live>()
        var cursor = 0
        for (page in 0 until maxPages) {
            val d = getJson("$BASE/api/v2/channels/followed?cursor=$cursor", true)
            val rows = rowsOf(d, "channels", "data")
            for (c in rows) {
                val liveFlag = c.opt("is_live")
                val live = if (liveFlag is Boolean) liveFlag else c.opt("livestream") != null && c.opt("livestream") != JSONObject.NULL
                if (!live) continue
                val slug = c.str("channel_slug", "slug")
                if (slug.isEmpty()) continue
                out.add(
                    Live(
                        slug = slug,
                        name = c.str("user_username", "username").ifEmpty { slug },
                        viewers = c.num("viewer_count", "viewers"),
                        title = c.str("session_title", "title"),
                        category = c.str("category_name", "category"),
                        thumb = "",
                        avatar = c.str("profile_picture", "profile_pic"),
                    )
                )
            }
            val next = (d as? JSONObject)?.opt("nextCursor")
            if (next == null || next == JSONObject.NULL || rows.isEmpty()) break
            cursor = (next as? Number)?.toInt() ?: break
        }
        return out.sortedByDescending { it.viewers }
    }

    /** Live channels Kick features on its own front page (public). */
    suspend fun featuredLive(maxPages: Int = 2): List<Live> {
        val out = ArrayList<Live>()
        var url: String? = "$BASE/stream/featured-livestreams/en"
        var n = 0
        while (url != null && n < maxPages) {
            n++
            val d = getJson(url, false)
            for (r in rowsOf(d, "data")) {
                val ch = r.optJSONObject("channel")
                val slug = ch.str("slug").ifEmpty { r.str("slug") }
                if (slug.isEmpty()) continue
                val user = ch?.optJSONObject("user")
                val cats = r.optJSONArray("categories")
                val cat = if (cats != null && cats.length() > 0) cats.optJSONObject(0).str("name") else ""
                out.add(
                    Live(
                        slug = slug,
                        name = user.str("username").ifEmpty { ch.str("username") }.ifEmpty { slug },
                        viewers = r.num("viewer_count", "viewers"),
                        title = r.str("session_title"),
                        category = cat,
                        thumb = r.opt("thumbnail").img(),
                        avatar = user.str("profilepic", "profile_pic").ifEmpty { ch.str("profile_picture") },
                    )
                )
            }
            val nx = (d as? JSONObject)?.opt("next_page_url")
            url = if (nx is String && nx.isNotEmpty()) nx else null
        }
        return out
    }
}

data class ChannelInfo(
    val slug: String,
    val name: String,
    val id: Long,
    val userId: Long,
    val chatroomId: Long,
    val live: Boolean,
    val title: String,
    val playbackUrl: String,
    val avatar: String,
    val subBadges: JSONArray?,
    val viewers: Int = 0,
    val category: String = "",
    val language: String = "",
    val verified: Boolean = false,
    val followers: Int = 0,
)

private const val BASE_URL = KickBridge.BASE

object Channels {
    private var globalTv: List<EmoteItem>? = null

    /** Channel info + chatroom id (from /api/v2/channels/<slug>). */
    suspend fun info(slug: String): ChannelInfo {
        val d = KickApi.getJsonPublic("$BASE_URL/api/v2/channels/$slug", false) as? JSONObject
            ?: throw KickException("bad_json")
        val chatroom = d.optJSONObject("chatroom")
        val chatroomId = chatroom?.optLong("id", 0L) ?: 0L
        if (chatroomId == 0L) throw KickException("no_chatroom")
        val user = d.optJSONObject("user")
        val ls = d.optJSONObject("livestream")
        return ChannelInfo(
            slug = d.optString("slug", slug).ifEmpty { slug },
            name = user.str("username").ifEmpty { slug },
            id = d.optLong("id", 0L),
            userId = d.optLong("user_id", 0L),
            chatroomId = chatroomId,
            live = ls != null,
            title = ls.str("session_title"),
            playbackUrl = d.optString("playback_url", ""),
            avatar = user.str("profile_pic", "profilepic"),
            subBadges = d.optJSONArray("subscriber_badges"),
            viewers = ls.num("viewer_count"),
            category = ls?.optJSONArray("categories")?.optJSONObject(0).str("name"),
            language = ls.str("language"),
            verified = d.optBoolean("verified", false),
            followers = d.num("followers_count", "followersCount"),
        )
    }

    /** Recent chat history, oldest first. */
    suspend fun history(channelId: Long, tv: Map<String, EmoteItem>, subBadges: JSONArray?): List<ChatMsg> {
        return try {
            val d = KickApi.getJsonPublic("$BASE_URL/api/v2/channels/$channelId/messages", false) as? JSONObject
            val arr = d?.optJSONObject("data")?.optJSONArray("messages") ?: return emptyList()
            val list = ArrayList<ChatMsg>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(ChatRender.message(o, tv, subBadges))
            }
            // Kick may return newest first
            val first = arr.optJSONObject(0)?.optString("created_at") ?: ""
            val last = arr.optJSONObject(arr.length() - 1)?.optString("created_at") ?: ""
            val ordered = if (first > last) list.reversed() else list
            ordered.takeLast(100)
        } catch (e: KickException) {
            emptyList()
        }
    }

    private fun walkKick(node: Any?, out: MutableList<Pair<String, String>>) {
        when (node) {
            is JSONObject -> {
                if (node.has("id") && node.has("name") && !node.has("emotes")) {
                    out.add(Pair(node.optString("name"), node.opt("id").toString()))
                }
                val it = node.keys()
                while (it.hasNext()) {
                    val v = node.opt(it.next())
                    if (v is JSONObject || v is JSONArray) walkKick(v, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) walkKick(node.opt(i), out)
        }
    }

    private fun tvSet(d: Any?): List<EmoteItem> {
        val o = d as? JSONObject ?: return emptyList()
        val set = o.optJSONObject("emote_set") ?: o
        val arr = set.optJSONArray("emotes") ?: return emptyList()
        val out = ArrayList<EmoteItem>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val name = e.optString("name")
            val id = e.optString("id")
            if (name.isEmpty() || id.isEmpty()) continue
            // real size of the emote (its 1x file), so wide emotes are not squeezed into a square
            var w = 0
            var h = 0
            val files = e.optJSONObject("data")?.optJSONObject("host")?.optJSONArray("files")
            if (files != null) {
                for (k in 0 until files.length()) {
                    val f = files.optJSONObject(k) ?: continue
                    if (f.optString("name").startsWith("1x")) {
                        w = f.optInt("width"); h = f.optInt("height")
                        if (f.optString("format").equals("WEBP", true)) break
                    }
                }
            }
            out.add(EmoteItem(name, "https://cdn.7tv.app/emote/$id/2x.webp", "7tv", w, h))
        }
        return out
    }

    private suspend fun plainGet(url: String): Any? = withContext(Dispatchers.IO) {
        try {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) TuffKick")
            if (c.responseCode != 200) return@withContext null
            JSONTokener(c.inputStream.bufferedReader().readText()).nextValue()
        } catch (e: Exception) {
            null
        }
    }

    /** Kick + 7TV emotes of a channel (7TV channel emotes override the global ones). */
    suspend fun emotes(slug: String, userId: Long): List<EmoteItem> {
        val out = LinkedHashMap<String, EmoteItem>()
        try {
            val d = KickApi.getJsonPublic("$BASE_URL/emotes/$slug", false)
            val raw = ArrayList<Pair<String, String>>()
            walkKick(d, raw)
            for ((name, id) in raw) out[name] = EmoteItem(name, ChatRender.KICK_EMOTE_URL.format(id), "kick")
        } catch (e: KickException) {
            // channel has no Kick emotes endpoint answer; 7TV may still work
        }
        if (globalTv == null) globalTv = tvSet(plainGet("https://7tv.io/v3/emote-sets/global"))
        for (e in globalTv.orEmpty()) out[e.name] = e
        if (userId != 0L) for (e in tvSet(plainGet("https://7tv.io/v3/users/kick/$userId"))) out[e.name] = e
        return out.values.toList()
    }

    /** Send a chat message (needs a signed-in user). */
    suspend fun send(chatroomId: Long, text: String, reply: ReplyRef?, replyUserId: String?, broadcasterId: Long = 0L) {
        if (broadcasterId != 0L && KickOAuth.linked()) {
            try {
                PublicApi.sendChat(broadcasterId, text, reply?.id)
                return
            } catch (e: KickException) {
                // fall back to the website route below
            }
        }
        val body = JSONObject()
        if (reply == null) {
            body.put("content", text).put("type", "message")
        } else {
            body.put("content", text).put("type", "reply").put(
                "metadata", JSONObject()
                    .put("original_message", JSONObject().put("id", reply.id).put("content", reply.content))
                    .put("original_sender", JSONObject().put("id", replyUserId?.toLongOrNull() ?: JSONObject.NULL).put("username", reply.username))
            )
        }
        val (status, _) = KickBridge.request(
            "POST", "$BASE_URL/api/v2/messages/send/$chatroomId", KickApi.authHeaders(), body.toString()
        )
        if (status != 200 && status != 201) throw KickException("send_failed_$status")
    }
}

/** Email + password sign-in through Kick's mobile login endpoint (no website needed). */
sealed interface LoginResult {
    object Ok : LoginResult
    object NeedCode : LoginResult
    data class Fail(val message: String) : LoginResult
}

object KickLogin {
    private const val BASE_URL = KickBridge.BASE

    suspend fun login(email: String, password: String, code: String): LoginResult {
        val tp = KickBridge.request("GET", "$BASE_URL/kick-token-provider", mapOf("Accept" to "application/json"))
        val body = JSONObject()
        body.put("email", email)
        body.put("password", password)
        body.put("isMobileRequest", true)
        if (code.isNotEmpty()) body.put("one_time_password", code)
        if (tp.first == 200) {
            try {
                val j = JSONObject(tp.second)
                val nameField = j.optString("nameFieldName")
                val fromField = j.optString("validFromFieldName")
                if (nameField.isNotEmpty()) body.put(nameField, "")
                if (fromField.isNotEmpty()) body.put(fromField, j.optString("encryptedValidFrom"))
            } catch (e: Exception) {
            }
        }
        val h = mapOf("Accept" to "application/json", "Content-Type" to "application/json")
        val r = KickBridge.request("POST", "$BASE_URL/mobile/login", h, body.toString())
        val text = r.second
        if (r.first == 0) return LoginResult.Fail("Network problem (" + text.take(40) + ")")
        val j = try { JSONObject(text) } catch (e: Exception) { null }
        if (j != null) {
            val token = j.optString("token")
            if (r.first in 200..299 && token.isNotEmpty()) {
                val cm = android.webkit.CookieManager.getInstance()
                cm.setCookie(BASE_URL, "session_token=" + android.net.Uri.encode(token) + "; Path=/; Secure")
                cm.flush()
                return LoginResult.Ok
            }
            if (j.optBoolean("2fa_required") || j.optBoolean("two_factor_required") ||
                text.contains("2fa", ignoreCase = true) || text.contains("one_time", ignoreCase = true)
            ) {
                return if (code.isEmpty()) LoginResult.NeedCode else LoginResult.Fail("Wrong code")
            }
            val msg = j.optString("message").ifEmpty { j.optJSONObject("errors")?.toString() ?: "" }
            if (msg.isNotEmpty()) return LoginResult.Fail(msg.take(120))
        }
        return LoginResult.Fail("Sign-in failed (HTTP " + r.first + ")")
    }
}

// ============================================================ browse data ==
data class Stream(
    val slug: String,
    val name: String,
    val viewers: Int,
    val title: String,
    val category: String,
    val categorySlug: String,
    val thumb: String,
    val avatar: String,
    val language: String,
    val userId: Long = 0,
)

data class Cat(val slug: String, val name: String, val viewers: Int, val image: String, val tags: List<String>, val id: Int = 0)
data class ChannelRow(val slug: String, val name: String, val avatar: String, val followers: Int, val live: Boolean, val verified: Boolean, val title: String)
data class Video(val id: String, val title: String, val thumb: String, val views: Int, val duration: Int, val created: String, val category: String, val source: String)
data class Clip(val id: String, val title: String, val thumb: String, val url: String, val views: Int, val duration: Int, val created: String, val creator: String, val channel: String, val category: String, val vodId: String = "", val startedAt: String = "")

data class ClipSource(val url: String, val startMs: Long, val endMs: Long)
data class Profile(
    val slug: String,
    val id: Long,
    val name: String,
    val bio: String,
    val avatar: String,
    val banner: String,
    val followers: Int,
    val verified: Boolean,
    val live: Boolean,
    val title: String,
    val viewers: Int,
    val category: String,
    val links: List<Pair<String, String>>,
    val subscription: Boolean,
    var following: Boolean?,
)

fun Live.toStream() = Stream(slug, name, viewers, title, category, "", thumb, avatar, "")

object Browse {
    private const val B = KickBridge.BASE
    private var catCache: List<Cat>? = null
    private var catQuick: List<Cat>? = null

    /** Only the first page of the top list: one request, so the Home tab fills quickly. The full merged list loads in the background. */
    suspend fun categoriesQuick(): List<Cat> {
        catCache?.let { return it }
        catQuick?.let { return it }
        val l = try {
            rows(first(listOf("$B/api/v1/categories/top", "$B/api/v1/categories-top", "$B/api/v1/subcategories?limit=60", "$B/api/v1/categories")))
                .mapNotNull { catRow(it) }.sortedByDescending { it.viewers }
        } catch (e: KickException) {
            emptyList()
        }
        if (l.isEmpty()) return categories()
        catQuick = l
        return l
    }

    private suspend fun first(urls: List<String>, auth: Boolean = false): Any {
        var last = "unreachable"
        for (u in urls) {
            try {
                return KickApi.getJsonPublic(u, auth)
            } catch (e: KickException) {
                last = e.code
            }
        }
        throw KickException(last)
    }

    private fun rows(d: Any?): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val arr: JSONArray? = when (d) {
            is JSONArray -> d
            is JSONObject -> {
                var f: JSONArray? = null
                for (k in listOf("data", "channels", "results", "hits", "categories", "livestreams", "items", "clips", "videos")) {
                    val v = d.opt(k)
                    if (v is JSONArray) { f = v; break }
                    if (v is JSONObject) {
                        val inner = rows(v)
                        if (inner.isNotEmpty()) return inner
                    }
                }
                f
            }
            else -> null
        }
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(it) }
        return out
    }

    private val avatarCache = HashMap<String, String>()
    private val avatarGate = kotlinx.coroutines.sync.Semaphore(4)

    /** Profile picture of any user by name (cached; empty when none). */
    suspend fun avatarOf(username: String): String {
        val k = username.lowercase().replace('_', '-')
        synchronized(avatarCache) { avatarCache[k] ?: avatarCache[username.lowercase()] }?.let { return it }
        return avatarGate.withPermit {
            val p = try {
                val d = KickApi.getJsonPublic("$B/api/v2/channels/$k", false) as? JSONObject
                val u = d?.optJSONObject("user")
                u?.opt("profile_pic").img().ifEmpty { u?.opt("profilepic").img() }.ifEmpty { d?.opt("profile_picture").img() }
            } catch (e: Exception) { "" }
            synchronized(avatarCache) { avatarCache[k] = p }
            p
        }
    }

    private fun streamRow(r: JSONObject): Stream? {
        val ch = r.optJSONObject("channel")
        val slug = ch.str("slug").ifEmpty { r.str("slug", "channel_slug") }
        if (slug.isEmpty()) return null
        val user = ch?.optJSONObject("user") ?: r.optJSONObject("user")
        val cats = r.optJSONArray("categories")
        val c0 = if (cats != null && cats.length() > 0) cats.optJSONObject(0) else null
        val av = user.str("profilepic", "profile_pic").ifEmpty { ch.str("profile_picture") }
        if (av.isNotEmpty()) avatarCache[slug.lowercase()] = av
        return Stream(
            slug = slug,
            name = user.str("username").ifEmpty { r.str("user_username") }.ifEmpty { slug },
            viewers = r.num("viewer_count", "viewers"),
            title = r.str("session_title", "title"),
            category = c0.str("name"),
            categorySlug = c0.str("slug"),
            thumb = r.opt("thumbnail").img(),
            avatar = av,
            language = r.str("language"),
        )
    }

    /** Official lists carry no profile pictures: fill them from the cache, the official users call, then the channel page. */
    suspend fun fillAvatars(list: List<Stream>): List<Stream> {
        var cur = list.map { s -> if (s.avatar.isEmpty()) avatarCache[s.slug.lowercase()]?.let { s.copy(avatar = it) } ?: s else s }
        val missing = cur.filter { it.avatar.isEmpty() }
        if (missing.isEmpty()) return cur
        if (KickOAuth.available()) {
            try {
                val ids = missing.map { it.userId }.filter { it > 0 }.distinct()
                val users = PublicApi.users(ids)
                cur = cur.map { s -> if (s.avatar.isEmpty() && s.userId > 0) users[s.userId]?.let { s.copy(avatar = it) } ?: s else s }
            } catch (e: Exception) {
            }
        }
        val still = cur.filter { it.avatar.isEmpty() }.take(30)
        if (still.isNotEmpty()) {
            val sem = kotlinx.coroutines.sync.Semaphore(5)
            val found = java.util.concurrent.ConcurrentHashMap<String, String>()
            kotlinx.coroutines.coroutineScope {
                still.forEach { s ->
                    launch {
                        sem.withPermit {
                            try {
                                val d = KickApi.getJsonPublic("$B/api/v2/channels/${s.slug}", false) as? JSONObject
                                val u = d?.optJSONObject("user")
                                val p = u?.opt("profile_pic").img().ifEmpty { u?.opt("profilepic").img() }.ifEmpty { d?.opt("profile_picture").img() }
                                if (p.isNotEmpty()) { found[s.slug] = p; avatarCache[s.slug.lowercase()] = p }
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
            cur = cur.map { s -> if (s.avatar.isEmpty()) found[s.slug]?.let { s.copy(avatar = it) } ?: s else s }
        }
        return cur
    }

    private fun mergeWeb(off: List<Stream>, web: List<Stream>): List<Stream> {
        val m = web.associateBy { it.slug.lowercase() }
        return off.map { s ->
            val w = m[s.slug.lowercase()]
            if (w == null) s else s.copy(thumb = s.thumb.ifEmpty { w.thumb }, avatar = s.avatar.ifEmpty { w.avatar }, name = if (s.name == s.slug) w.name else s.name)
        }
    }

    suspend fun featured(lang: String = "en"): List<Stream> {
        val d = first(listOf("$B/stream/featured-livestreams/$lang"))
        return rows(d).mapNotNull { streamRow(it) }
    }

    /** Live streams; with a category only that category (filtered here, so correct even if Kick ignores the parameter). */
    suspend fun live(categorySlug: String?, pages: Int = 2, lang: String = "en"): List<Stream> {
        val out = ArrayList<Stream>()
        val seen = HashSet<String>()
        for (page in 1..pages) {
            val q = if (categorySlug != null) "subcategory=" + java.net.URLEncoder.encode(categorySlug, "UTF-8") + "&" else ""
            val urls = ArrayList<String>()
            urls.add("$B/stream/livestreams/$lang?${q}page=$page&limit=50&sort=desc")
            if (page == 1) urls.add("$B/stream/featured-livestreams/$lang")
            val d = try { first(urls) } catch (e: KickException) { if (page == 1) throw e else break }
            val rs = rows(d)
            if (rs.isEmpty()) break
            for (r in rs) {
                val s = streamRow(r) ?: continue
                if (seen.add(s.slug)) out.add(s)
            }
            val dj = d as? JSONObject
            if (dj != null && dj.has("next_page_url") && (dj.isNull("next_page_url") || dj.optString("next_page_url").isEmpty())) break
        }
        val res = if (categorySlug != null) out.filter { it.categorySlug == categorySlug } else out
        return res.sortedByDescending { it.viewers }
    }

    /**
     * Live streams of one language. Kick's language filter is not documented, so several URL shapes are tried and
     * every result is checked against the stream's own `language` field; the first shape that really returns
     * streams in that language wins. Last resort: scan more pages of the normal list and filter here.
     */
    suspend fun liveByLang(code: String, name: String): List<Stream> {
        if (KickOAuth.available()) {
            try {
                val off = PublicApi.livestreams(code, 100)
                if (off.isNotEmpty()) {
                    // the official list has no profile pictures and often no thumbnails: merge with the website list
                    val info = try { PublicApi.channels(off.map { it.slug }) } catch (e: KickException) { emptyMap() }
                    var res = off.map { s -> info[s.slug.lowercase()]?.let { i -> s.copy(thumb = i.thumb.ifEmpty { s.thumb }, avatar = i.avatar.ifEmpty { s.avatar }) } ?: s }
                    if (res.any { it.thumb.isEmpty() }) {
                        val web = try { webByLang(code, name) } catch (e: Exception) { emptyList() }
                        res = mergeWeb(res, web)
                    }
                    return fillAvatars(res)
                }
            } catch (e: KickException) {
            }
        }
        return webByLang(code, name)
    }


    private fun langMatches(s: Stream, name: String, code: String) =
        s.language.equals(name, true) || s.language.equals(code, true)

    /**
     * Livestreams for the Browse tab. sort: 0 recommended (Kick's own order), 1 featured, 2 viewers high to low.
     * langs are (name, code) pairs; an empty list means all languages. Several languages are merged.
     */
    suspend fun streamsFor(langs: List<Pair<String, String>>, sort: Int): List<Stream> {
        val targets = if (langs.isEmpty()) listOf(Pair("", "")) else langs
        val lists: List<List<Stream>> = coroutineScope {
            targets.map { (name, code) ->
                async {
                    try {
                        when (sort) {
                            1 -> {
                                val f = featured(code.ifEmpty { "en" })
                                val m = if (code.isEmpty()) f else f.filter { langMatches(it, name, code) }
                                if (m.isEmpty()) f else m
                            }
                            0 -> {
                                // the website list ignores the language, so with a language use the filtered (official) list
                                if (code.isEmpty()) live(null, 2, "en")
                                else liveByLang(code, name).ifEmpty { webByLang(code, name, "") }
                            }
                            else -> if (code.isEmpty()) live(null, 2, "en").sortedByDescending { it.viewers } else liveByLang(code, name)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.map { it.await() }
        }
        if (lists.size == 1) return lists[0].distinctBy { it.slug }
        if (sort == 2) return lists.flatten().distinctBy { it.slug }.sortedByDescending { it.viewers }
        // keep each language's own order, taking turns
        val out = ArrayList<Stream>()
        val seen = HashSet<String>()
        var i = 0
        while (lists.any { i < it.size }) {
            for (l in lists) if (i < l.size && seen.add(l[i].slug)) out.add(l[i])
            i++
        }
        return out
    }

    /** Website listing for a language: always has thumbnails and profile pictures. */
    suspend fun webByLang(code: String, name: String, sort: String = "desc"): List<Stream> {
        val sq = if (sort.isEmpty()) "" else "&sort=$sort"
        fun matches(s: Stream) = s.language.equals(name, true) || s.language.equals(code, true)
        val enc = java.net.URLEncoder.encode(code, "UTF-8")
        val urls = listOf(
            "$B/stream/livestreams/$enc?page=1&limit=50$sq",
            "https://web.kick.com/api/v1/livestreams?language=$enc&limit=50&sort=viewer_count_desc",
            "$B/api/v1/livestreams?language=$enc&limit=50&sort=viewer_count_desc",
            "$B/stream/livestreams/en?language=$enc&page=1&limit=50$sq",
            "$B/stream/livestreams/en?languages=$enc&page=1&limit=50$sq",
        )
        for (u in urls) {
            try {
                val d = KickApi.getJsonPublic(u, false)
                val got = rows(d).mapNotNull { streamRow(it) }.filter { matches(it) }
                if (got.isNotEmpty()) return got.distinctBy { it.slug }.sortedByDescending { it.viewers }
            } catch (e: KickException) {
            }
        }
        return live(null, 8, "en").filter { matches(it) }
    }

    /** Live streams of a category: official filter (with thumbnails) when connected, otherwise the website list. */
    suspend fun liveOfCategory(slug: String, id: Int, pages: Int = 2): List<Stream> {
        if (id > 0 && KickOAuth.available()) {
            try {
                val off = PublicApi.livestreams(null, 50, id)
                if (off.isNotEmpty()) {
                    val info = try { PublicApi.channels(off.map { it.slug }) } catch (e: KickException) { emptyMap() }
                    var res = off.map { s -> info[s.slug.lowercase()]?.let { i -> s.copy(thumb = i.thumb.ifEmpty { s.thumb }, avatar = i.avatar.ifEmpty { s.avatar }) } ?: s }
                    if (res.any { it.thumb.isEmpty() }) {
                        val web = try { live(slug, pages) } catch (e: Exception) { emptyList() }
                        res = mergeWeb(res, web)
                    }
                    return fillAvatars(res)
                }
            } catch (e: KickException) {
            }
        }
        return live(slug, pages)
    }

    private fun catRow(r: JSONObject): Cat? {
        val cat = r.optJSONObject("category")
        val slug = r.str("slug").ifEmpty { cat.str("slug") }
        val name = r.str("name").ifEmpty { cat.str("name") }
        if (slug.isEmpty() || name.isEmpty()) return null
        val tags = ArrayList<String>()
        val ta = r.optJSONArray("tags")
        if (ta != null) for (i in 0 until minOf(ta.length(), 4)) {
            val t = ta.opt(i)
            tags.add(if (t is JSONObject) t.optString("name") else t.toString())
        }
        val image = r.opt("banner").img().ifEmpty { r.opt("image").img() }.ifEmpty { r.opt("thumbnail").img() }
            .ifEmpty { cat?.opt("icon").img() }
        return Cat(slug, name, r.num("viewers", "viewer_count"), image, tags)
    }

    suspend fun categories(): List<Cat> {
        catCache?.let { return it }
        // the full list (official API when available) plus live viewer counts taken from Kick's own lists
        val unofficial = try {
            val d = first(listOf("$B/api/v1/categories/top", "$B/api/v1/categories-top", "$B/api/v1/subcategories?limit=60", "$B/api/v1/categories"))
            val all = ArrayList(rows(d).mapNotNull { catRow(it) })
            // more pages of the same list, one after the other: lower categories carry their viewer counts there
            for (pg in 2..6) {
                val more = try {
                    rows(first(listOf("$B/api/v1/subcategories?limit=60&page=$pg"))).mapNotNull { catRow(it) }
                } catch (e: KickException) {
                    break
                }
                if (more.isEmpty() || more.all { m -> all.any { it.slug == m.slug } }) break
                all.addAll(more.filter { m -> all.none { it.slug == m.slug } })
            }
            all.toList()
        } catch (e: KickException) {
            emptyList()
        }
        val views = HashMap<String, Int>()
        for (c in unofficial) { views[c.slug] = c.viewers; views[c.name.lowercase()] = c.viewers }
        var official = emptyList<Cat>()
        if (KickOAuth.available()) {
            try {
                official = PublicApi.categories(null, 100)
            } catch (e: KickException) {
            }
        }
        val sums = HashMap<String, Int>()
        try {
            for (st in live(null, 8)) {
                if (st.categorySlug.isEmpty()) continue
                sums[st.categorySlug] = (sums[st.categorySlug] ?: 0) + st.viewers
                sums[st.category.lowercase()] = (sums[st.category.lowercase()] ?: 0) + st.viewers
            }
        } catch (e: KickException) {
        }
        for ((k, v) in sums) if ((views[k] ?: 0) < v) views[k] = v
        var res = if (official.size >= unofficial.size) official else unofficial
        if (res.isEmpty()) {
            val m = LinkedHashMap<String, Cat>()
            for (st in live(null, 3)) {
                if (st.categorySlug.isEmpty()) continue
                val c = m[st.categorySlug]
                m[st.categorySlug] = Cat(st.categorySlug, st.category, (c?.viewers ?: 0) + st.viewers, "", emptyList())
            }
            res = m.values.toList()
        }
        res = res.map { c ->
            val v = maxOf(c.viewers, views[c.slug] ?: 0, views[c.name.lowercase()] ?: 0)
            if (v == c.viewers) c else c.copy(viewers = v)
        }.sortedByDescending { it.viewers }
        if (res.isEmpty()) throw KickException("no_categories")
        catCache = res
        return res
    }

    private fun channelRow(o: JSONObject): ChannelRow? {
        val r = o.optJSONObject("document") ?: o
        val ch = r.optJSONObject("channel")
        val user = r.optJSONObject("user") ?: ch?.optJSONObject("user")
        val slug = r.str("slug", "channel_slug").ifEmpty { ch.str("slug") }
        if (slug.isEmpty()) return null
        return ChannelRow(
            slug = slug,
            name = user.str("username").ifEmpty { r.str("username", "user_username", "name") }.ifEmpty { slug },
            avatar = user?.opt("profilepic").img().ifEmpty { user?.opt("profile_pic").img() }
                .ifEmpty { r.opt("profile_pic").img() }.ifEmpty { r.opt("profilepic").img() }
                .ifEmpty { r.opt("profile_picture").img() },
            followers = r.num("followers_count", "followersCount", "followers"),
            live = r.optBoolean("is_live", false) || r.optBoolean("isLive", false) || r.optJSONObject("livestream") != null,
            verified = r.optBoolean("verified", false),
            title = r.str("session_title").ifEmpty { r.optJSONObject("livestream").str("session_title") },
        )
    }

    suspend fun search(q: String): List<ChannelRow> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val qe = java.net.URLEncoder.encode(query, "UTF-8")
        val out = ArrayList<ChannelRow>()
        try {
            var d = first(listOf("$B/api/search?searchable=channels&query=$qe", "$B/api/search?query=$qe", "$B/api/search?searchable=channels&q=$qe"))
            if (d is JSONObject && d.opt("channels") is JSONObject) d = d.getJSONObject("channels")
            for (r in rows(d)) channelRow(r)?.let { out.add(it) }
        } catch (e: KickException) {
        }
        val slug = query.lowercase().replace(" ", "")
        if (slug.isNotEmpty() && out.none { it.slug.equals(slug, true) }) {
            try {
                val p = profile(slug, withMe = false)
                out.add(0, ChannelRow(p.slug, p.name, p.avatar, p.followers, p.live, p.verified, p.title))
            } catch (e: KickException) {
            }
        }
        return out.take(30)
    }

    suspend fun profile(slug: String, withMe: Boolean = true): Profile {
        val info = KickApi.getJsonPublic("$B/api/v2/channels/$slug", false) as? JSONObject ?: throw KickException("bad_json")
        val user = info.optJSONObject("user")
        val ls = info.optJSONObject("livestream")
        val cats = ls?.optJSONArray("categories")
        val links = ArrayList<Pair<String, String>>()
        val defs = listOf(
            Triple("instagram", "Instagram", "https://instagram.com/"), Triple("twitter", "X / Twitter", "https://x.com/"),
            Triple("youtube", "YouTube", "https://youtube.com/@"), Triple("discord", "Discord", "https://discord.gg/"),
            Triple("tiktok", "TikTok", "https://tiktok.com/@"), Triple("facebook", "Facebook", "https://facebook.com/"),
        )
        for ((k, label, base) in defs) {
            val v = user.str(k).trim()
            if (v.isNotEmpty()) links.add(Pair(label, if (v.startsWith("http")) v else base + v.trimStart('@', '/')))
        }
        var following: Boolean? = null
        if (withMe && KickBridge.token() != null) {
            try {
                val me = KickApi.getJsonPublic("$B/api/v2/channels/$slug/me", true) as? JSONObject
                if (me != null && me.has("is_following")) following = me.optBoolean("is_following")
            } catch (e: Exception) {
            }
        }
        return Profile(
            slug = info.optString("slug", slug).ifEmpty { slug },
            id = info.optLong("id", 0L),
            name = user.str("username").ifEmpty { slug },
            bio = user.str("bio"),
            avatar = user?.opt("profile_pic").img().ifEmpty { user?.opt("profilepic").img() },
            banner = info.opt("banner_image").img().ifEmpty { info.opt("offline_banner_image").img() },
            followers = info.num("followers_count", "followersCount"),
            verified = info.optBoolean("verified", false),
            live = ls != null,
            title = ls.str("session_title"),
            viewers = ls.num("viewer_count"),
            category = if (cats != null && cats.length() > 0) cats.optJSONObject(0).str("name") else "",
            links = links,
            subscription = info.optBoolean("subscription_enabled", false),
            following = following,
        )
    }

    /** Fills in thumbnail, category, language and title of a live channel (the followed list does not carry them). */
    suspend fun streamInfo(slug: String, base: Stream): Stream {
        val d = KickApi.getJsonPublic("$B/api/v2/channels/$slug", false) as? JSONObject ?: return base
        val ls = d.optJSONObject("livestream") ?: return base
        val cats = ls.optJSONArray("categories")
        val c0 = if (cats != null && cats.length() > 0) cats.optJSONObject(0) else null
        var thumb = ls.opt("thumbnail").img()
        if (thumb.isEmpty()) {
            try {
                val l2 = KickApi.getJsonPublic("$B/api/v2/channels/$slug/livestream", false) as? JSONObject
                val dd = l2?.optJSONObject("data") ?: l2
                thumb = dd?.opt("thumbnail").img()
            } catch (e: KickException) {
            }
        }
        if (thumb.isEmpty()) {
            try {
                val v1 = KickApi.getJsonPublic("$B/api/v1/channels/$slug", false) as? JSONObject
                thumb = v1?.optJSONObject("livestream")?.opt("thumbnail").img()
            } catch (e: KickException) {
            }
        }
        return base.copy(
            thumb = thumb.ifEmpty { base.thumb },
            category = c0.str("name").ifEmpty { base.category },
            categorySlug = c0.str("slug").ifEmpty { base.categorySlug },
            language = ls.str("language").ifEmpty { base.language },
            title = ls.str("session_title").ifEmpty { base.title },
            viewers = ls.num("viewer_count").takeIf { it > 0 } ?: base.viewers,
        )
    }

    suspend fun isFollowing(slug: String): Boolean? {
        if (KickBridge.token() == null) return null
        return try {
            val me = KickApi.getJsonPublic("$B/api/v2/channels/$slug/me", true) as? JSONObject
            if (me != null && me.has("is_following")) me.optBoolean("is_following") else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun follow(slug: String, on: Boolean) {
        val (s, _) = KickBridge.request(if (on) "POST" else "DELETE", "$B/api/v2/channels/$slug/follow", KickApi.authHeaders(), if (on) "{}" else null)
        if (s !in listOf(200, 201, 204)) throw KickException("follow_failed_$s")
    }


    // ---- viewer-side channel points (website API, may change) ----
    data class ViewerReward(val id: String, val title: String, val cost: Int, val description: String, val color: String, val needsInput: Boolean, val enabled: Boolean)

    private fun numIn(v: Any?, depth: Int = 0): Int? {
        if (depth > 4) return null
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toDoubleOrNull()?.toInt()
            is JSONObject -> {
                for (k in listOf("points", "balance", "amount", "count", "value", "data")) {
                    if (v.has(k)) numIn(v.opt(k), depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    suspend fun myPoints(slug: String): Int? {
        val (s, t) = KickBridge.request("GET", "$B/api/v2/channels/$slug/points", KickApi.authHeaders())
        if (s != 200) throw KickException("points_http_$s")
        return numIn(try { JSONTokener(t).nextValue() } catch (e: Exception) { null })
    }

    suspend fun viewerRewards(slug: String): List<ViewerReward> {
        val (s, t) = KickBridge.request("GET", "$B/api/v2/channels/$slug/rewards", KickApi.authHeaders())
        if (s != 200) throw KickException("rewards_http_$s")
        val d = try { JSONTokener(t).nextValue() } catch (e: Exception) { return emptyList() }
        val out = ArrayList<ViewerReward>()
        for (r in rows(d)) {
            val title = r.str("title", "name")
            if (title.isEmpty()) continue
            out.add(
                ViewerReward(
                    r.opt("id")?.toString().orEmpty(), title, r.optInt("cost", r.optInt("price", 0)),
                    r.str("description"), r.str("background_color", "color"),
                    r.optBoolean("is_user_input_required", r.optBoolean("user_input_required", false)),
                    r.optBoolean("is_enabled", r.optBoolean("enabled", true)) && !r.optBoolean("is_paused", false),
                )
            )
        }
        return out
    }

    suspend fun redeem(slug: String, rewardId: String, input: String) {
        val body = JSONObject().put("reward_id", rewardId).put("user_input", input).toString()
        val (s, t) = KickBridge.request("POST", "$B/api/v2/channels/$slug/rewards/redeem", KickApi.authHeaders(), body)
        if (s !in 200..299) {
            val msg = try { JSONObject(t).optString("message") } catch (e: Exception) { "" }
            throw KickException(if (msg.isNotEmpty()) msg else "redeem_http_$s")
        }
    }

    /** Places a channel-points prediction. The website API is not documented, so a few known shapes are tried. */
    suspend fun vote(slug: String, predictionId: String, outcomeId: String, amount: Int) {
        val body = JSONObject().put("outcome_id", outcomeId.toLongOrNull() ?: outcomeId).put("amount", amount).toString()
        var last = 0
        for (u in listOf(
            "$B/api/v2/channels/$slug/predictions/$predictionId/vote",
            "$B/api/v2/predictions/$predictionId/vote",
            "$B/api/v2/channels/$slug/predictions/vote",
        )) {
            val b = if (u.endsWith("/predictions/vote")) JSONObject(body).put("prediction_id", predictionId).toString() else body
            val (s, t) = KickBridge.request("POST", u, KickApi.authHeaders(), b)
            if (s in 200..299) return
            last = s
            if (s != 404 && s != 405) {
                val msg = try { JSONObject(t).optString("message") } catch (e: Exception) { "" }
                throw KickException(if (msg.isNotEmpty()) msg else "vote_http_$s")
            }
        }
        throw KickException("vote_http_$last")
    }

    /** Top gifters of a channel: pairs of (name, kicks). Empty when the site does not return any. */
    suspend fun topGifters(slug: String): List<Pair<String, Int>> {
        val (s, t) = KickBridge.request("GET", "$B/api/v2/channels/$slug/leaderboards", KickApi.authHeaders())
        if (s != 200) throw KickException("leaderboard_http_$s")
        val d = try { JSONObject(t) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Pair<String, Int>>()
        for (key in listOf("gifts_week", "gifts_month", "gifts", "gifts_lifetime")) {
            val arr = d.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(Pair(o.optString("username").ifEmpty { o.optJSONObject("user")?.optString("username").orEmpty() }, o.optInt("quantity", o.optInt("gifted_amount", 0))))
            }
            if (out.isNotEmpty()) break
        }
        return out.filter { it.first.isNotEmpty() }
    }

    private fun dur(v: Double): Int = if (v > 100000) (v / 1000).toInt() else v.toInt()

    suspend fun videos(slug: String): List<Video> {
        val d = first(listOf("$B/api/v2/channels/$slug/videos"))
        val out = ArrayList<Video>()
        for (r in rows(d)) {
            val vid = r.optJSONObject("video")
            val uuid = vid.str("uuid").ifEmpty { r.str("uuid") }
            if (uuid.isEmpty() && r.str("source").isEmpty()) continue
            val cats = r.optJSONArray("categories")
            val c0 = if (cats != null && cats.length() > 0) cats.optJSONObject(0) else null
            out.add(
                Video(
                    id = uuid.ifEmpty { r.str("id") },
                    title = r.str("session_title", "title"),
                    thumb = r.opt("thumbnail").img().ifEmpty { vid.str("thumb") },
                    views = vid.num("views").takeIf { it > 0 } ?: r.num("views"),
                    duration = dur(r.optDouble("duration", 0.0)),
                    created = r.str("start_time", "created_at").ifEmpty { vid.str("created_at") },
                    category = c0.str("name"),
                    source = r.str("source"),
                )
            )
        }
        return out
    }

    suspend fun videoSource(uuid: String): String {
        val d = first(listOf("$B/api/v1/video/$uuid")) as? JSONObject
        val src = d.str("source")
        if (src.isEmpty()) throw KickException("no_source")
        return src
    }

    private fun clipRow(r: JSONObject): Clip? {
        val ca = r.str("clip_url")
        val cb = r.str("video_url")
        val url = listOf(ca, cb).firstOrNull { it.contains("m3u8") } ?: ca.ifEmpty { cb }
        val id = r.str("id")
        if (id.isEmpty() && url.isEmpty()) return null
        return Clip(
            id = id,
            title = r.str("title"),
            thumb = r.str("thumbnail_url").ifEmpty { r.opt("thumbnail").img() },
            url = url,
            views = r.num("view_count", "views"),
            duration = dur(r.optDouble("duration", 0.0)),
            created = r.str("created_at", "started_at"),
            creator = r.optJSONObject("creator").str("username", "slug"),
            channel = r.optJSONObject("channel").str("username", "slug"),
            category = r.optJSONObject("category").str("name"),
            vodId = r.optJSONObject("vod").str("id", "uuid"),
            startedAt = r.str("started_at"),
        )
    }

    private fun parseUtc(s: String): Long? {
        if (s.length < 19) return null
        return try {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(s.substring(0, 19).replace('T', ' '))?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A clip is a cut of a past broadcast. The broadcast (VOD) has an HLS master playlist with every quality, so when the
     * clip tells which VOD it came from we play that VOD between the clip's start and end instead of the single-quality file.
     */
    suspend fun clipVodSource(c: Clip): ClipSource? {
        if (c.vodId.isEmpty() || c.startedAt.isEmpty()) return null
        return try {
            val d = first(listOf("$B/api/v1/video/${c.vodId}")) as? JSONObject ?: return null
            val src = d.str("source")
            if (src.isEmpty()) return null
            val ls = d.optJSONObject("livestream")
            val t0 = parseUtc(ls.str("start_time", "created_at").ifEmpty { d.str("created_at") }) ?: return null
            val t1 = parseUtc(c.startedAt) ?: return null
            val off = t1 - t0
            if (off < 0 || off > 3L * 24 * 3600 * 1000) return null
            ClipSource(src, off, off + maxOf(c.duration, 1) * 1000L)
        } catch (e: KickException) {
            null
        }
    }

    suspend fun clips(slug: String?, sort: String = "view", period: String = "all"): List<Clip> {
        val urls = if (slug != null) listOf("$B/api/v2/channels/$slug/clips?sort=$sort&time=$period", "$B/api/v2/channels/$slug/clips")
        else listOf("$B/api/v2/clips?cursor=0&sort=$sort&time=$period", "$B/api/v2/clips")
        val d = first(urls)
        return rows(d).mapNotNull { clipRow(it) }
    }

    /** Every channel the signed-in user follows (live ones first). */
    suspend fun followedAll(maxPages: Int = 4): List<ChannelRow> {
        val out = ArrayList<ChannelRow>()
        var cursor = 0
        for (page in 0 until maxPages) {
            val d = first(listOf("$B/api/v2/channels/followed?cursor=$cursor"), true)
            val rs = rows(d)
            for (c in rs) {
                val slug = c.str("channel_slug", "slug")
                if (slug.isEmpty()) continue
                val lf = c.opt("is_live")
                val live = if (lf is Boolean) lf else c.optJSONObject("livestream") != null
                out.add(
                    ChannelRow(
                        slug, c.str("user_username", "username").ifEmpty { slug },
                        c.str("profile_picture", "profile_pic"), 0, live, false, c.str("session_title"),
                    )
                )
            }
            val next = (d as? JSONObject)?.opt("nextCursor")
            if (next == null || next == JSONObject.NULL || rs.isEmpty()) break
            cursor = (next as? Number)?.toInt() ?: break
        }
        return out.sortedByDescending { it.live }
    }
}
