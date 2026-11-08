package com.tuff.kick

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

// ------------------------------------------------------------------ models --
sealed interface Seg {
    data class Text(val v: String) : Seg
    data class Emote(val name: String, val url: String, val w: Int = 0, val h: Int = 0) : Seg
}

data class Badge(val type: String, val url: String?, val text: String, val count: Int = 0)

data class ReplyRef(val id: String, val username: String, val content: String)

data class ChatMsg(
    val id: String,
    val userId: String,
    val name: String,
    val color: String,
    val badges: List<Badge>,
    val seg: List<Seg>,
    val reply: ReplyRef?,
    val time: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

data class EmoteItem(val name: String, val url: String, val src: String, val w: Int = 0, val h: Int = 0)

sealed interface ChatEvent {
    data class Message(val msg: ChatMsg) : ChatEvent
    data class Deleted(val id: String) : ChatEvent
    data class Status(val text: String, val connected: Boolean) : ChatEvent
    /** msg == null: the pinned message was removed. */
    data class Pinned(val pin: PinnedMsg?) : ChatEvent
    /** p == null: no running prediction any more. */
    data class PredictionChanged(val p: Prediction?) : ChatEvent
}

data class PinnedMsg(val msg: ChatMsg, val by: String)

data class PredOutcome(val title: String, val amount: Long, val id: String = "", val votes: Long = 0)

data class Prediction(val id: String, val title: String, val state: String, val outcomes: List<PredOutcome>, val winnerId: String = "")

// ------------------------------------------------------- message rendering --
object ChatRender {
    private val kickEmote = Regex("\\[emote:(\\d+):([^\\]]+)\\]")
    const val KICK_EMOTE_URL = "https://files.kick.com/emotes/%s/fullsize"

    private fun words(chunk: String, tv: Map<String, EmoteItem>, out: MutableList<Seg>) {
        // split into alternating word / whitespace tokens, keeping the whitespace
        var i = 0
        while (i < chunk.length) {
            val ws = chunk[i].isWhitespace()
            var j = i
            while (j < chunk.length && chunk[j].isWhitespace() == ws) j++
            val tok = chunk.substring(i, j)
            i = j
            val e = tv[tok]
            if (e != null) {
                out.add(Seg.Emote(e.name, e.url, e.w, e.h))
            } else {
                val last = out.lastOrNull()
                if (last is Seg.Text) out[out.size - 1] = Seg.Text(last.v + tok) else out.add(Seg.Text(tok))
            }
        }
    }

    fun segments(text: String, tv: Map<String, EmoteItem>): List<Seg> {
        val out = ArrayList<Seg>()
        var pos = 0
        for (m in kickEmote.findAll(text)) {
            if (m.range.first > pos) words(text.substring(pos, m.range.first), tv, out)
            out.add(Seg.Emote(m.groupValues[2], KICK_EMOTE_URL.format(m.groupValues[1])))
            pos = m.range.last + 1
        }
        if (pos < text.length) words(text.substring(pos), tv, out)
        return out
    }

    private fun subBadgeUrl(count: Int, subBadges: JSONArray?): String? {
        if (subBadges == null) return null
        var bestMonths = -1
        var best: String? = null
        for (i in 0 until subBadges.length()) {
            val b = subBadges.optJSONObject(i) ?: continue
            val months = b.optInt("months", 0)
            val src = b.optJSONObject("badge_image")?.optString("src") ?: ""
            if (src.isNotEmpty() && months <= count && months > bestMonths) {
                bestMonths = months
                best = src
            }
        }
        return best
    }

    private fun firstStr(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.opt(k)
            if (v is String && v.isNotEmpty()) return v
        }
        return ""
    }

    /**
     * Kick's collectible badges (level, Golden K, GOAT, Flyby...) come in "badges_v2" with an image url instead of in
     * "badges". The field names are not documented, so they are read by several possible names.
     */
    /** false when Kick marks a badge as not equipped by its owner. */
    private fun isActive(o: JSONObject): Boolean {
        for (k in listOf("active", "is_active", "isActive", "equipped", "is_equipped", "isEquipped", "selected", "is_selected", "enabled", "displayed", "visible")) {
            val v = o.opt(k)
            if (v is Boolean && !v) return false
        }
        return true
    }

    private fun collectibles(identity: JSONObject?, out: MutableList<Badge>, onlyActive: Boolean) {
        val arr = identity?.optJSONArray("badges_v2") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                val name = arr.optString(i)
                if (name.isNotEmpty()) out.add(Badge(name.lowercase(), null, name))
                continue
            }
            if (onlyActive && !isActive(o)) continue
            val name = firstStr(o, "name", "type", "slug", "key", "id", "title")
            val img = firstStr(o, "image_url", "imageUrl", "image", "src", "url")
            var level = 0
            for (k in listOf("level", "count", "value")) {
                val v = o.opt(k)
                if (v is Number && v.toInt() > 0) { level = v.toInt(); break }
                if (v is String && v.toIntOrNull() != null && v.toInt() > 0) { level = v.toInt(); break }
            }
            val text = firstStr(o, "text", "label", "title", "name")
            out.add(Badge(name.lowercase(), img.ifEmpty { null }, text.ifEmpty { name }, level))
        }
    }

    /** onlyActive = true in chat (only what the user has switched on); false on the user card (everything they own). */
    fun badges(identity: JSONObject?, subBadges: JSONArray?, onlyActive: Boolean = true): List<Badge> {
        val out = ArrayList<Badge>()
        collectibles(identity, out, onlyActive)
        val arr = identity?.optJSONArray("badges")
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            if (onlyActive && !isActive(b)) continue
            val kind = b.optString("type")
            if (kind == "subscriber") {
                val count = b.optInt("count", 0)
                out.add(Badge(kind, subBadgeUrl(count, subBadges), "$count mo"))
            } else {
                out.add(Badge(kind, null, b.optString("text", kind), b.optInt("count", 0)))
            }
        }
        return out
    }

    fun message(raw: JSONObject, tv: Map<String, EmoteItem>, subBadges: JSONArray?): ChatMsg {
        val sender = raw.optJSONObject("sender")
        val ident = sender?.optJSONObject("identity")
        val meta = raw.optJSONObject("metadata")
        var reply: ReplyRef? = null
        val om = meta?.optJSONObject("original_message")
        if (raw.optString("type") == "reply" && om != null) {
            reply = ReplyRef(
                id = om.optString("id"),
                username = meta?.optJSONObject("original_sender")?.optString("username") ?: "",
                content = kickEmote.replace(om.optString("content")) { it.groupValues[2] },
            )
        }
        val color = ident?.optString("color").orEmpty().ifEmpty { "#c9ced3" }
        return ChatMsg(
            id = raw.opt("id")?.toString() ?: "",
            userId = sender?.opt("id")?.toString() ?: "",
            name = sender?.optString("username").orEmpty().ifEmpty { "?" },
            color = color,
            badges = badges(ident, subBadges),
            seg = segments(raw.optString("content"), tv),
            reply = reply,
        )
    }
}

// -------------------------------------------------------- realtime (Centrifugo) --
object Realtime {
    private const val BASE = "https://web.kick.com/api/v1/realtime"
    private val headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json")

    private suspend fun post(path: String, payload: JSONObject): JSONObject {
        val (status, text) = KickBridge.request("POST", BASE + path, headers, payload.toString())
        if (status != 200) throw KickException("realtime $path http $status")
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw KickException("realtime $path bad json")
        }
    }

    suspend fun url(clientId: String): String {
        val body = JSONObject()
            .put("client", JSONObject().put("id", clientId).put("type", "web"))
            .put(
                "capabilities", JSONObject().put(
                    "accepted_providers",
                    JSONArray().put(JSONObject().put("provider", "pusher")).put(JSONObject().put("provider", "centrifugo"))
                )
            )
        val data = post("/connection", body).optJSONObject("data")
        val conns = data?.optJSONArray("connections")
        if (conns != null) {
            for (i in 0 until conns.length()) {
                val c = conns.optJSONObject(i) ?: continue
                if (c.optString("provider") == "centrifugo") {
                    val u = c.optJSONObject("credentials")?.optString("url").orEmpty()
                    if (u.isNotEmpty()) return u
                }
            }
        }
        throw KickException("no centrifugo provider offered")
    }

    suspend fun token(clientId: String): String {
        val d = post("/auth/connection", JSONObject().put("client_id", clientId))
        val tok = d.optJSONObject("data")?.optString("token").orEmpty()
        if (tok.isEmpty()) throw KickException("no token in answer")
        return tok
    }
}

/** Live chat of one channel. Reconnects with a fresh guest token before the 45 minute token expires. */
class ChatClient(
    private val chatroomId: Long,
    private val channelId: Long,
    private val scope: CoroutineScope,
    private val tv: () -> Map<String, EmoteItem>,
    private val subBadges: JSONArray?,
    private val onEvent: (ChatEvent) -> Unit,
) {
    private var job: Job? = null
    /** subscribe requests that are allowed to fail without disturbing the chat (extra, guessed channels) */
    private val optionalIds = java.util.Collections.synchronizedSet(HashSet<Int>())
    @Volatile private var socket: WebSocket? = null
    private val http = OkHttpClient()

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close(1000, null)
        socket = null
    }

    private suspend fun loop() {
        var backoff = 3000L
        while (scope.isActive && job?.isActive == true) {
            val clientId = UUID.randomUUID().toString()
            val url: String
            val token: String
            try {
                url = Realtime.url(clientId)
                token = Realtime.token(clientId)
            } catch (e: KickException) {
                onEvent(ChatEvent.Status("Reconnecting… (" + e.code.take(40) + ")", false))
                delay(backoff)
                backoff = minOf(backoff * 2, 30000L)
                continue
            } catch (e: Exception) {
                onEvent(ChatEvent.Status("Reconnecting…", false))
                delay(backoff)
                backoff = minOf(backoff * 2, 30000L)
                continue
            }
            backoff = 3000L
            val closed = CompletableDeferred<Unit>()
            var seq = 0
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    seq++
                    webSocket.send(
                        JSONObject().put("id", seq)
                            .put("connect", JSONObject().put("token", token).put("name", "js")).toString()
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    for (line in text.split("\n")) {
                        val l = line.trim()
                        if (l.isEmpty()) continue
                        val msg = try { JSONObject(l) } catch (e: Exception) { continue }
                        if (msg.length() == 0) {
                            webSocket.send("{}")
                        } else if (msg.has("id") && optionalIds.contains(msg.optInt("id", -1)) && !msg.has("push")) {
                            // answer of an optional extra subscription: ignore it, success or not
                        } else if (msg.has("error")) {
                            val er = msg.optJSONObject("error")
                            onEvent(ChatEvent.Status("Error: " + er?.optString("message").orEmpty(), false))
                        } else if (msg.has("connect")) {
                            seq++
                            webSocket.send(
                                JSONObject().put("id", seq)
                                    .put("subscribe", JSONObject().put("channel", "chatrooms.$chatroomId.v2")).toString()
                            )
                            if (channelId > 0L) {
                                // predictions have their own channel; if Kick names it differently this just stays silent
                                seq++
                                optionalIds.add(seq)
                                webSocket.send(
                                    JSONObject().put("id", seq)
                                        .put("subscribe", JSONObject().put("channel", "predictions.channel.$channelId")).toString()
                                )
                            }
                        } else if (msg.has("subscribe")) {
                            onEvent(ChatEvent.Status("Connected", true))
                        } else if (msg.has("push")) {
                            handlePush(msg.optJSONObject("push"))
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }
            }
            val ws = http.newWebSocket(Request.Builder().url(url).header("Origin", "https://kick.com").build(), listener)
            socket = ws
            withTimeoutOrNull(2_400_000L) { closed.await() }
            ws.close(1000, null)
            if (job?.isActive != true) break
            onEvent(ChatEvent.Status("Reconnecting…", false))
            delay(3000)
        }
    }

    private fun handlePush(push: JSONObject?) {
        val pub = push?.optJSONObject("pub") ?: return
        val d = pub.optJSONObject("data") ?: return
        var ev = d.optString("event")
        if (ev.isEmpty()) ev = pub.optJSONObject("tags")?.optString("event").orEmpty()
        val raw = d.opt("data")
        val data: JSONObject = when (raw) {
            is String -> try { JSONObject(raw) } catch (e: Exception) { return }
            is JSONObject -> raw
            else -> return
        }
        if (ev.endsWith("ChatMessageEvent")) {
            onEvent(ChatEvent.Message(ChatRender.message(data, tv(), subBadges)))
        } else if (ev.contains("PinnedMessageCreated", true)) {
            ChatExtras.parsePinned(data, tv(), subBadges)?.let { onEvent(ChatEvent.Pinned(it)) }
        } else if (ev.contains("PinnedMessageDeleted", true)) {
            onEvent(ChatEvent.Pinned(null))
        } else if (ev.contains("Prediction", true)) {
            val p = ChatExtras.parsePrediction(data)
            onEvent(ChatEvent.PredictionChanged(if (p != null && ChatExtras.isOpen(p)) p else null))
        } else if (ev.endsWith("MessageDeletedEvent")) {
            val id = data.optJSONObject("message")?.opt("id")?.toString() ?: data.opt("id")?.toString() ?: return
            onEvent(ChatEvent.Deleted(id))
        }
    }
}
