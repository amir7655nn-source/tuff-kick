package com.tuff.kick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    object Idle : Load<Nothing>
    object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Fail(val message: String) : Load<Nothing>
}

class AppViewModel : ViewModel() {
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username
    private val _avatar = MutableStateFlow("")
    val avatar: StateFlow<String> = _avatar

    private val _following = MutableStateFlow<Load<List<Live>>>(Load.Idle)
    val following: StateFlow<Load<List<Live>>> = _following
    private val _discover = MutableStateFlow<Load<List<Live>>>(Load.Idle)
    val discover: StateFlow<Load<List<Live>>> = _discover

    init {
        refreshAll()
    }

    fun refreshAll() {
        refreshAuth()
        loadDiscover()
    }

    /** Checks the shared cookie jar; loads the Following list when signed in. */
    fun refreshAuth() {
        viewModelScope.launch {
            val logged = KickBridge.token() != null
            _loggedIn.value = logged
            if (logged) {
                val m = KickApi.meFull()
                _username.value = m?.first ?: (KickApi.me() ?: "")
                var av = m?.third ?: ""
                if (av.isEmpty() && m != null && m.second.isNotEmpty()) {
                    av = try { Browse.profile(m.second, withMe = false).avatar } catch (e: Exception) { "" }
                }
                _avatar.value = av
                loadFollowing()
            } else {
                _username.value = ""
                _avatar.value = ""
                _following.value = Load.Idle
            }
        }
    }

    fun loadFollowing() {
        if (KickBridge.token() == null) {
            _following.value = Load.Idle
            return
        }
        _following.value = Load.Loading
        viewModelScope.launch {
            _following.value = try {
                Load.Ok(KickApi.followedLive())
            } catch (e: KickException) {
                Load.Fail(e.code)
            } catch (e: Exception) {
                Load.Fail(e.message ?: "error")
            }
        }
    }

    fun loadDiscover() {
        _discover.value = Load.Loading
        viewModelScope.launch {
            _discover.value = try {
                Load.Ok(KickApi.featuredLive())
            } catch (e: KickException) {
                Load.Fail(e.code)
            } catch (e: Exception) {
                Load.Fail(e.message ?: "error")
            }
        }
    }

    fun logout() {
        val cm = android.webkit.CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        KickBridge.reload()
        _loggedIn.value = false
        _username.value = ""
        _avatar.value = ""
        _following.value = Load.Idle
    }

    fun onLoginClosed() {
        KickBridge.reload()
        refreshAuth()
        loadDiscover()
    }
}
