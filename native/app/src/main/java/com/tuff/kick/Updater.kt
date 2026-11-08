package com.tuff.kick

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Looks for a newer release in the public repository. */
object Updater {
    private const val REPO = "amir7655nn-source/tuff-kick"

    class Update(val tag: String, val url: String, val notes: String)

    private fun parts(v: String): List<Int>? {
        val p = v.trim().removePrefix("v").removePrefix("V").split(".")
        val n = p.map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: return null }
        return if (n.isEmpty()) null else n
    }

    private fun newer(latest: List<Int>, mine: List<Int>): Boolean {
        for (i in 0 until maxOf(latest.size, mine.size)) {
            val a = latest.getOrElse(i) { 0 }
            val b = mine.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Null when up to date, on a dev build, or when the check fails. */
    suspend fun check(ctx: Context): Update? = withContext(Dispatchers.IO) {
        try {
            val mine = parts(BuildConfig.VERSION_NAME) ?: return@withContext null
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@withContext null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val latest = parts(tag) ?: return@withContext null
            if (!newer(latest, mine)) return@withContext null
            val skipped = ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE).getString("update_skip", "")
            if (skipped == tag) return@withContext null
            Update(tag, o.optString("html_url").ifEmpty { "https://github.com/$REPO/releases/latest" }, o.optString("body").take(600))
        } catch (e: Exception) {
            null
        }
    }

    fun skip(ctx: Context, tag: String) {
        ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE).edit().putString("update_skip", tag).apply()
    }
}
