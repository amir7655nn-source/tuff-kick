package com.tuff.kick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WatchViewModel(val slug: String) : ViewModel() {
    private val _info = MutableStateFlow<Load<ChannelInfo>>(Load.Idle)
    val info: StateFlow<Load<ChannelInfo>> = _info
    private val _messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val messages: StateFlow<List<ChatMsg>> = _messages
    private val _status = MutableStateFlow("Connecting…")
    val status: StateFlow<String> = _status
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _emotes = MutableStateFlow<List<EmoteItem>>(emptyList())
    val emotes: StateFlow<List<EmoteItem>> = _emotes
    private val _reply = MutableStateFlow<ChatMsg?>(null)
    val reply: StateFlow<ChatMsg?> = _reply
    private val _sendError = MutableStateFlow("")
    val sendError: StateFlow<String> = _sendError

    private val _pinned = MutableStateFlow<PinnedMsg?>(null)
    val pinned: StateFlow<PinnedMsg?> = _pinned
    private val _prediction = MutableStateFlow<Prediction?>(null)
    val prediction: StateFlow<Prediction?> = _prediction

    private var chat: ChatClient? = null
    private var users = 0
    private var tvMap: Map<String, EmoteItem> = emptyMap()
    private var started = false

    fun start() {
        if (started) return
        started = true
        _info.value = Load.Loading
        _messages.value = emptyList()
        viewModelScope.launch {
            val ci = try {
                Channels.info(slug)
            } catch (e: KickException) {
                _info.value = Load.Fail(e.code)
                started = false
                return@launch
            } catch (e: Exception) {
                _info.value = Load.Fail(e.message ?: "error")
                started = false
                return@launch
            }
            _info.value = Load.Ok(ci)
            // emotes first, so history and live messages can already show 7TV emotes
            val em = try { Channels.emotes(slug, ci.userId) } catch (e: Exception) { emptyList() }
            _emotes.value = em
            tvMap = em.filter { it.src != "kick" }.associateBy { it.name }
            val hist = Channels.history(ci.id, tvMap, ci.subBadges)
            _messages.value = hist
            val client = ChatClient(ci.chatroomId, ci.id, viewModelScope, { tvMap }, ci.subBadges) { ev -> onEvent(ev) }
            chat = client
            client.start()
            // pinned message + running prediction (live events may already have set them: do not overwrite those)
            val ex = try { ChatExtras.load(slug, tvMap, ci.subBadges) } catch (e: Exception) { Pair(null, null) }
            if (_pinned.value == null) _pinned.value = ex.first
            if (_prediction.value == null) _prediction.value = ex.second
        }
    }

    /** Several screens (watch page, profile chat tab) can show the same chat: it runs while at least one is open. */
    fun attach() {
        users++
        start()
    }

    fun detach() {
        users--
        if (users <= 0) {
            users = 0
            stop()
        }
    }

    fun stop() {
        chat?.stop()
        chat = null
        started = false
        _pinned.value = null
        _prediction.value = null
    }

    private fun onEvent(ev: ChatEvent) {
        when (ev) {
            is ChatEvent.Message -> {
                val cur = _messages.value
                val next = if (cur.size >= 300) cur.drop(cur.size - 299) + ev.msg else cur + ev.msg
                _messages.value = next
            }
            is ChatEvent.Deleted -> _messages.value = _messages.value.map { if (it.id == ev.id) it.copy(deleted = true, seg = listOf(Seg.Text("Deleted by a moderator"))) else it }
            is ChatEvent.Pinned -> _pinned.value = ev.pin
            is ChatEvent.PredictionChanged -> _prediction.value = ev.p
            is ChatEvent.Status -> {
                _status.value = ev.text
                _connected.value = ev.connected
            }
        }
    }

    fun setReply(m: ChatMsg?) {
        _reply.value = m
    }

    fun clearError() {
        _sendError.value = ""
    }

    /** Kick emotes are sent as [emote:ID:name]; 7TV emotes are plain words. */
    private fun encode(text: String): String {
        val kick = _emotes.value.filter { it.src == "kick" }.associateBy { it.name }
        if (kick.isEmpty()) return text
        return text.split(" ").joinToString(" ") { w ->
            val e = kick[w]
            if (e != null) {
                val id = Regex("/emotes/(\\d+)/").find(e.url)?.groupValues?.get(1)
                if (id != null) "[emote:$id:$w]" else w
            } else w
        }
    }

    fun send(text: String) {
        val info = (_info.value as? Load.Ok<ChannelInfo>)?.data ?: return
        val t = text.trim()
        if (t.isEmpty()) return
        val r = _reply.value
        viewModelScope.launch {
            try {
                Channels.send(
                    info.chatroomId,
                    encode(t),
                    r?.let { ReplyRef(it.id, it.name, it.seg.joinToString("") { s -> if (s is Seg.Text) s.v else (s as Seg.Emote).name }) },
                    r?.userId,
                    info.userId,
                )
                _reply.value = null
            } catch (e: KickException) {
                _sendError.value = if (e.code == "not_logged_in") "Sign in to send messages" else "Message not sent (" + e.code + ")"
            } catch (e: Exception) {
                _sendError.value = "Message not sent"
            }
        }
    }

    override fun onCleared() {
        chat?.stop()
        super.onCleared()
    }
}

class WatchFactory(private val slug: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WatchViewModel(slug) as T
}
