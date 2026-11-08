package com.tuff.kick

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pinned message and prediction of a chatroom.
 *
 * Kick does not document these, so everything here is read defensively: several possible URLs are tried, and the
 * JSON is searched for the fields instead of expecting one exact shape. If Kick changes names, the card simply
 * does not show; chat itself is never affected.
 */
object ChatExtras {
    private const val B = KickBridge.BASE
    private val closedStates = setOf("resolved", "cancelled", "canceled", "completed", "ended", "refunded", "finished", "paid")

    // ------------------------------------------------------------ pinned --
    fun parsePinned(d: JSONObject, tv: Map<String, EmoteItem>, subBadges: JSONArray?): PinnedMsg? {
        val m = d.optJSONObject("message") ?: d.optJSONObject("pinned_message") ?: (if (d.has("content")) d else return null)
        val inner = m.optJSONObject("message") ?: m
        if (inner.optString("content").isEmpty()) return null
        val msg = ChatRender.message(inner, tv, subBadges)
        val by = d.optJSONObject("pinned_by")?.optString("username").orEmpty()
            .ifEmpty { d.optJSONObject("pinnedBy")?.optString("username").orEmpty() }
            .ifEmpty { msg.name }
        return PinnedMsg(msg, by)
    }

    // -------------------------------------------------------- prediction --
    private fun firstStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            if (v is String && v.isNotEmpty()) return v
        }
        return ""
    }

    private fun firstNum(o: JSONObject, vararg keys: String): Long {
        for (k in keys) {
            val v = o.opt(k)
            if (v is Number) return v.toLong()
            if (v is String) v.toLongOrNull()?.let { return it }
        }
        return 0L
    }

    /** First object anywhere in the JSON that has an "outcomes" array. */
    private fun findWithOutcomes(node: Any?, depth: Int = 0): JSONObject? {
        if (depth > 5) return null
        when (node) {
            is JSONObject -> {
                if (node.optJSONArray("outcomes") != null) return node
                val it = node.keys()
                while (it.hasNext()) {
                    val r = findWithOutcomes(node.opt(it.next()), depth + 1)
                    if (r != null) return r
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val r = findWithOutcomes(node.opt(i), depth + 1)
                if (r != null) return r
            }
        }
        return null
    }

    fun parsePrediction(root: Any?): Prediction? {
        val o = findWithOutcomes(root) ?: return null
        val arr = o.optJSONArray("outcomes") ?: return null
        val outs = ArrayList<PredOutcome>()
        for (i in 0 until arr.length()) {
            val ob = arr.optJSONObject(i) ?: continue
            outs.add(
                PredOutcome(
                    firstStr(ob, "title", "name", "label"),
                    firstNum(ob, "total_vote_amount", "total_points", "vote_amount", "total_amount", "amount", "total", "votes", "total_votes"),
                    ob.opt("id")?.toString().orEmpty(),
                    firstNum(ob, "vote_count", "votes_count", "voters", "total_voters"),
                )
            )
        }
        if (outs.size < 2) return null
        return Prediction(
            id = o.opt("id")?.toString().orEmpty(),
            title = firstStr(o, "title", "name", "question"),
            state = firstStr(o, "state", "status").lowercase(),
            outcomes = outs,
            winnerId = o.opt("winning_outcome_id")?.takeIf { it != JSONObject.NULL }?.toString().orEmpty()
                .ifEmpty { o.opt("winner_outcome_id")?.takeIf { it != JSONObject.NULL }?.toString().orEmpty() }
                .ifEmpty { o.opt("winning_outcome")?.let { w -> (w as? JSONObject)?.opt("id")?.toString() ?: w.toString() }?.takeIf { it != "null" }.orEmpty() },
        )
    }

    fun isOpen(p: Prediction) = p.state !in closedStates

    // -------------------------------------------------------------- fetch --
    private suspend fun get(url: String): Any? = try {
        KickApi.getJsonPublic(url, false)
    } catch (e: Exception) {
        null
    }

    /** The newest prediction of the channel in any state (running or finished). */
    suspend fun latest(slug: String): Prediction? {
        for (u in listOf("$B/api/v2/channels/$slug/predictions/latest", "$B/api/v2/channels/$slug/predictions", "$B/api/v2/channels/$slug/prediction")) {
            val dd = get(u) ?: continue
            val p = try { parsePrediction(dd) } catch (e: Exception) { null }
            if (p != null) return p
        }
        val room = get("$B/api/v2/channels/$slug/chatroom")
        return try { parsePrediction(room) } catch (e: Exception) { null }
    }

    suspend fun load(slug: String, tv: Map<String, EmoteItem>, subBadges: JSONArray?): Pair<PinnedMsg?, Prediction?> {
        var pin: PinnedMsg? = null
        val room = get("$B/api/v2/channels/$slug/chatroom") as? JSONObject
        if (room != null) {
            val pm = room.optJSONObject("pinned_message") ?: room.optJSONObject("data")?.optJSONObject("pinned_message")
            if (pm != null) pin = try { parsePinned(pm, tv, subBadges) } catch (e: Exception) { null }
        }
        var pred: Prediction? = null
        val urls = listOf(
            "$B/api/v2/channels/$slug/predictions/latest",
            "$B/api/v2/channels/$slug/predictions",
            "$B/api/v2/channels/$slug/prediction",
        )
        for (u in urls) {
            val d = get(u) ?: continue
            val p = try { parsePrediction(d) } catch (e: Exception) { null }
            if (p != null) { pred = p; break }
        }
        if (pred == null && room != null) pred = try { parsePrediction(room) } catch (e: Exception) { null }
        return Pair(pin, pred?.takeIf { isOpen(it) })
    }
}
