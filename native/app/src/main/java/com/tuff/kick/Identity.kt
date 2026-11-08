package com.tuff.kick

import org.json.JSONArray
import org.json.JSONObject

class IdBadge(val type: String, val text: String, val count: Int, val active: Boolean, val url: String, val v2: Boolean = false)
class IdentityData(val color: String, val badges: List<IdBadge>, val colors: List<String> = emptyList())

/** The viewer's chat identity (username colour and which badges are shown). Kick's website API; the shapes are best-effort. */
object Identity {
    private const val B = KickBridge.BASE

    // exact for the two Kick confirmed (00F1FF, FF55B3); the rest are close and get replaced by exact colours learned from chat
    val COLORS = listOf(
        "#00F1FF", "#4CFF74", "#56FFC8", "#6F87FF", "#AAA9FF", "#BDFF28", "#E26EFF", "#E4D88F",
        "#E5FFAB", "#FEA0CF", "#FF2C56", "#FF4116", "#FF55B3", "#FFA602", "#FFAF76", "#FFFFFF",
    )

    private suspend fun myId(): Long {
        val d = KickApi.getJsonPublic("$B/api/v1/user", true) as? JSONObject
        return d?.optLong("id") ?: 0L
    }

    private fun urls(slug: String, channelId: Long, uid: Long) = listOf(
        "$B/api/v2/channels/$channelId/users/$uid/identity",
        "$B/api/internal/v1/channels/$slug/chatroom/users/$uid/identity",
        "$B/api/internal/v1/chatroom/users/$uid/identity",
    )

    private fun parseColors(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            val s = when (v) {
                is String -> v
                is JSONObject -> v.optString("color").ifEmpty { v.optString("hex") }.ifEmpty { v.optString("value") }
                else -> ""
            }
            if (s.startsWith("#") && s.length >= 7 && s !in out) out.add(s.uppercase())
        }
        return out
    }

    /** The colours Kick offers for usernames; empty when Kick does not tell. */
    private suspend fun loadColors(root: JSONObject?, o: JSONObject?): List<String> {
        for (j in listOf(o, root)) {
            if (j == null) continue
            for (k in listOf("colors", "available_colors", "color_options", "username_colors")) {
                val c = parseColors(j.optJSONArray(k))
                if (c.isNotEmpty()) return c
            }
        }
        for (u in listOf("$B/api/v2/chat/colors", "$B/api/v2/chatroom/colors", "$B/api/internal/v1/chatroom/colors", "$B/api/v2/chatroom/identity/colors")) {
            try {
                val d = KickApi.getJsonPublic(u, true)
                val c = when (d) {
                    is JSONArray -> parseColors(d)
                    is JSONObject -> listOf("colors", "data").firstNotNullOfOrNull { k -> parseColors(d.optJSONArray(k)).ifEmpty { null } } ?: emptyList()
                    else -> emptyList()
                }
                if (c.isNotEmpty()) return c
            } catch (e: KickException) {
            }
        }
        return emptyList()
    }

    suspend fun load(slug: String, channelId: Long): IdentityData {
        val uid = myId()
        var last = "identity_unavailable"
        for (u in urls(slug, channelId, uid)) {
            try {
                val root = KickApi.getJsonPublic(u, true) as? JSONObject ?: continue
                val dat = root.optJSONObject("data")
                val o = dat?.optJSONObject("identity") ?: root.optJSONObject("identity") ?: dat ?: root
                val arr = o.optJSONArray("badges") ?: o.optJSONArray("available_badges") ?: JSONArray()
                val badges = ArrayList<IdBadge>()
                for (i in 0 until arr.length()) {
                    val raw = arr.opt(i)
                    if (raw is String) { if (raw.isNotEmpty()) badges.add(IdBadge(raw, "", 0, true, "")); continue }
                    val b = raw as? JSONObject ?: continue
                    // only badges the account really has
                    if (b.has("owned") && !b.optBoolean("owned")) continue
                    if (b.has("unlocked") && !b.optBoolean("unlocked")) continue
                    if (b.has("available") && !b.optBoolean("available")) continue
                    val active = when {
                        b.has("active") -> b.optBoolean("active")
                        b.has("enabled") -> b.optBoolean("enabled")
                        b.has("selected") -> b.optBoolean("selected")
                        else -> true
                    }
                    badges.add(
                        IdBadge(
                            b.optString("type"), b.optString("text").ifEmpty { b.optString("name") }, b.optInt("count", 0), active,
                            b.optJSONObject("image")?.optString("src").orEmpty().ifEmpty { b.optString("image") },
                        ),
                    )
                }
                // newer badges (level) come in a second list
                val v2 = o.optJSONArray("badges_v2")
                if (v2 != null) for (i in 0 until v2.length()) {
                    val b = v2.optJSONObject(i) ?: continue
                    val n = b.optString("name")
                    if (n.isNotEmpty()) badges.add(IdBadge(n, n, b.optJSONObject("metadata")?.optInt("level", 0) ?: 0, b.optBoolean("selected", true), b.optString("image_url"), true))
                }
                return IdentityData(o.optString("color"), badges, loadColors(root, o))
            } catch (e: KickException) {
                last = e.code
            }
        }
        throw KickException(last)
    }

    suspend fun save(slug: String, channelId: Long, color: String, badges: List<IdBadge>, known: Set<String> = emptySet()) {
        val uid = myId()
        fun norm(t: String) = t.lowercase().replace("_", "").replace("-", "")
        val u = "$B/api/v2/channels/$channelId/users/$uid/identity"
        var last = 0
        var okAny = false
        val log = ArrayList<String>()
        suspend fun put(tag: String, vararg bodies: String): Boolean {
            for (method in listOf("PUT", "PATCH", "POST")) for (body in bodies) {
                val (s, t) = KickBridge.request(method, u, KickApi.authHeaders(), body)
                if (s in 200..299) return true
                last = s
                val m = try { JSONObject(t).optJSONObject("status")?.optString("message") ?: JSONObject(t).optString("message") } catch (e: Exception) { "" }
                log.add("$tag $method $s" + (if (m.isNotEmpty() && m != "OK") " $m".take(40) else ""))
            }
            return false
        }
        // 1) colour and the older badges (only names Kick itself listed)
        val old = JSONArray()
        for (b in badges) if (!b.v2 && b.active) old.put(b.type)
        val body1 = JSONObject().put("identity", JSONObject().put("color", color).put("badges", old)).toString()
        val body1b = JSONObject().put("color", color).put("badges", old).toString()   // the plain shape that worked in v58
        if (put("A", body1b, body1)) okAny = true
        // 2) the newer badges (level) with their on/off state
        val v2 = JSONArray()
        val v2full = JSONArray()
        for (b in badges) if (b.v2 || norm(b.type) == "level") {
            v2.put(JSONObject().put("name", b.type).put("badge_type", "global").put("selected", b.active))
            v2full.put(JSONObject().put("name", b.type).put("badge_type", "global").put("image_url", b.url).put("metadata", JSONObject().put("level", b.count)).put("selected", b.active).put("sort_order", 1))
        }
        var v2ok = true
        if (v2.length() > 0) {
            val body2 = JSONObject().put("color", color).put("badges_v2", v2).toString()
            val body3 = JSONObject().put("color", color).put("badges_v2", v2full).toString()
            v2ok = put("B", body2, body3)
            if (v2ok) okAny = true
        }
        // the level badge is part of what was asked, so say so when only the rest was saved
        if (okAny && v2ok) return
        throw KickException(log.joinToString(" | ").ifEmpty { "identity_http_$last" })
    }
}
