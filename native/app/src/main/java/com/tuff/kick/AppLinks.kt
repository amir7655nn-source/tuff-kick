package com.tuff.kick

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Links shown in the app. Put the Telegram channel name (without @) here; empty hides the buttons. */
object AppLinks {
    const val TELEGRAM = "TuffKick"

    fun openTelegram(ctx: Context) {
        if (TELEGRAM.isEmpty()) {
            android.widget.Toast.makeText(ctx, "Telegram not set yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$TELEGRAM")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
}

/** Categories the viewer has hearted (kept on this device). */
object CatFollows {
    private const val KEY = "cat_follows"
    private fun prefs() = TuffApp.instance.getSharedPreferences("tuff", Context.MODE_PRIVATE)
    fun load(): Set<String> = prefs().getStringSet(KEY, emptySet()) ?: emptySet()
    fun save(s: Set<String>) { prefs().edit().putStringSet(KEY, s).apply() }
}

/** The signed-in user's name, so chat can outline messages that tag them. */
object Me {
    @Volatile var name: String = ""
}

/** Lets a long link break at / . - _ ? & = so it can start on the same line as the username. */
fun softBreaks(t: String): String {
    if (t.length < 16) return t
    val sb = StringBuilder(t.length + 8)
    var run = 0
    for (i in t.indices) {
        val c = t[i]
        sb.append(c)
        if (c.isWhitespace()) run = 0 else run++
        if (run >= 12 && c in "/.-_?&=#,:" && i < t.length - 1 && !t[i + 1].isWhitespace()) sb.append('\u200B')
    }
    return sb.toString()
}
