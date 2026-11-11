package com.tuff.kick

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------- home --
@Composable
fun HomeScreen(nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    val featured = rememberLoader("home-featured") { Browse.featured() }
    val forYou = rememberLoader("home-live") { Browse.live(null, 2) }
    val cats = rememberLoader("home-cats") { Browse.categoriesQuick() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("Featured Creators")
        LoadView(featured, skeleton = { SkeletonStreamRow() }) { list -> StreamRow(list.take(12), nav) }
        SectionTitle("Streams For You")
        LoadView(forYou, skeleton = { SkeletonStreamRow() }) { list -> StreamRow(list.take(20), nav) }
        SectionTitle("Categories For You")
        LoadView(cats, skeleton = { SkeletonCatRow() }) { list ->
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list.take(20), key = { it.slug }) { c -> CatCard(c, Modifier.width(110.dp)) { nav.category(c.slug, c.name) } }
            }
            list.take(5).forEach { c -> CategoryRow(c, nav) }
        }
        Spacer(Modifier.height(bottomPad))
    }
}

@Composable
fun CategoryRow(c: Cat, nav: Nav) {
    val l = rememberLoader("catrow", c.slug) { Browse.live(c.slug, 1) }
    val s = l.state
    if (s is Load.Ok && s.data.isEmpty()) return
    SectionTitle(c.name) { nav.category(c.slug, c.name) }
    LoadView(l, skeleton = { SkeletonStreamRow() }) { list -> StreamRow(list.take(12), nav) }
}

// ----------------------------------------------------------------- browse --
val LANGS = listOf(
    "Afrikaans" to "af", "Albanian" to "sq", "Amharic" to "am", "Arabic" to "ar", "Aragonese" to "an", "Armenian" to "hy",
    "Asturian" to "ast", "Azerbaijani" to "az", "Basque" to "eu", "Belarusian" to "be", "Bengali" to "bn", "Bosnian" to "bs",
    "Breton" to "br", "Bulgarian" to "bg", "Burmese" to "my", "Catalan" to "ca", "Chinese" to "zh", "Croatian" to "hr",
    "Czech" to "cs", "Danish" to "da", "Dutch" to "nl", "English" to "en", "Esperanto" to "eo", "Estonian" to "et",
    "Filipino" to "tl", "Finnish" to "fi", "French" to "fr", "Galician" to "gl", "Georgian" to "ka", "German" to "de",
    "Greek" to "el", "Gujarati" to "gu", "Hebrew" to "he", "Hindi" to "hi", "Hungarian" to "hu", "Icelandic" to "is",
    "Indonesian" to "id", "Irish" to "ga", "Italian" to "it", "Japanese" to "ja", "Kannada" to "kn", "Kazakh" to "kk",
    "Khmer" to "km", "Korean" to "ko", "Kurdish" to "ku", "Latvian" to "lv", "Lithuanian" to "lt", "Macedonian" to "mk",
    "Malay" to "ms", "Malayalam" to "ml", "Maltese" to "mt", "Marathi" to "mr", "Mongolian" to "mn", "Nepali" to "ne",
    "Norwegian" to "no", "Persian" to "fa", "Polish" to "pl", "Portuguese" to "pt", "Punjabi" to "pa", "Romanian" to "ro",
    "Russian" to "ru", "Serbian" to "sr", "Sinhala" to "si", "Slovak" to "sk", "Slovenian" to "sl", "Spanish" to "es",
    "Swahili" to "sw", "Swedish" to "sv", "Tamil" to "ta", "Telugu" to "te", "Thai" to "th", "Turkish" to "tr",
    "Ukrainian" to "uk", "Urdu" to "ur", "Uzbek" to "uz", "Vietnamese" to "vi", "Welsh" to "cy", "Zulu" to "zu",
)

/** Kick only shows full language names ("Persian"), never codes ("fa"). */
fun langName(v: String): String {
    if (v.isEmpty()) return v
    val hit = LANGS.firstOrNull { it.second.equals(v, true) || it.first.equals(v, true) }
    return hit?.first ?: v.replaceFirstChar { it.uppercase() }
}

@Composable
fun BrowseScreen(nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE) }
    var langs by remember { mutableStateOf<Set<String>>(prefs.getStringSet("br_langs", emptySet()) ?: emptySet()) }
    var sort by remember { mutableIntStateOf(prefs.getInt("br_sort", 0)) }          // 0 recommended, 1 featured, 2 viewers
    var sheet by remember { mutableIntStateOf(0) }      // 0 none, 1 prefs, 2 language, 3 clip prefs
    var clipSort by remember { mutableStateOf("view") }
    var catFollows by remember { mutableStateOf(CatFollows.load()) }
    var clipPeriod by remember { mutableStateOf("all") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tabs(listOf("Categories", "Livestreams", "Clips"), tab, { tab = it }, Modifier.weight(1f), kick = true)
                if (tab != 0) {
                    IconButton(onClick = { sheet = if (tab == 1) 1 else 3 }) {
                        Icon(painterResource(R.drawable.ic_filter), "Filter", tint = Palette.Text)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
            when (tab) {
                0 -> {
                    val cats = rememberLoader("browse-cats") { Browse.categories() }
                    LoadView(cats, skeleton = { SkeletonCatGrid() }) { list ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPad),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(list, key = { it.slug }) { c ->
                                CatCard(
                                    c, followed = c.slug in catFollows,
                                    onFollow = { catFollows = if (c.slug in catFollows) catFollows - c.slug else catFollows + c.slug; CatFollows.save(catFollows) },
                                ) { nav.category(c.slug, c.name) }
                            }
                        }
                    }
                }
                1 -> {
                    val live = rememberLoader("browse-live", langs, sort) {
                        Browse.streamsFor(LANGS.filter { it.second in langs }, sort)
                    }
                    LoadView(live, skeleton = { SkeletonList() }) { list -> FilledStreams(list, nav, bottomPad) }
                }
                else -> {
                    val clips = rememberLoader("browse-clips", clipSort, clipPeriod) { Browse.clips(null, clipSort, clipPeriod) }
                    LoadView(clips, skeleton = { SkeletonList() }) { list ->
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = bottomPad),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(list, key = { it.id }) { c -> ClipItem(c, nav) }
                        }
                    }
                }
            }
        }
        if (sheet == 1) {
            Sheet("Preferences", onClose = { sheet = 0 }) {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Raised)) {
                    listOf("Featured" to 1, "Recommended" to 0, "Viewers (High to Low)" to 2).forEachIndexed { i, (label, v) ->
                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Palette.Line))
                        Row(
                            Modifier.fillMaxWidth().clickable { sort = v; prefs.edit().putInt("br_sort", v).apply() }.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = Palette.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (sort == v) Icon(painterResource(R.drawable.ic_check), "Selected", tint = Palette.Text, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OptionRow("Filter Language", trailing = if (langs.isEmpty()) "All" else "${langs.size} selected") { sheet = 2 }
                Button(
                    onClick = { langs = emptySet(); sort = 0; prefs.edit().putStringSet("br_langs", emptySet()).putInt("br_sort", 0).apply(); sheet = 0 },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Raised, contentColor = Palette.Text),
                ) { Text("Reset To Default", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
        if (sheet == 2) {
            LanguageSheet(
                selected = langs,
                onSave = { langs = it; prefs.edit().putStringSet("br_langs", it).apply(); sheet = 1 },
                onBack = { sheet = 1 },
                onClose = { sheet = 0 },
            )
        }
        if (sheet == 3) {
            Sheet("Preferences", onClose = { sheet = 0 }) {
                Text("Sort", color = Palette.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                OptionRow("Most Recent", checked = clipSort == "date") { clipSort = "date" }
                OptionRow("Views", checked = clipSort == "view") { clipSort = "view" }
                Text("Filter", color = Palette.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                OptionRow("Last Day", checked = clipPeriod == "day") { clipPeriod = "day" }
                OptionRow("Last Week", checked = clipPeriod == "week") { clipPeriod = "week" }
                OptionRow("Last Month", checked = clipPeriod == "month") { clipPeriod = "month" }
                OptionRow("All Time", checked = clipPeriod == "all") { clipPeriod = "all" }
            }
        }
    }
}

/** Multi-select language filter like Kick's own: ticked languages float to the top, Save applies, back/close cancels. */
@Composable
private fun LanguageSheet(selected: Set<String>, onSave: (Set<String>) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(selected) }
    var q by remember { mutableStateOf("") }
    val shown = LANGS.filter { q.isEmpty() || it.first.contains(q, ignoreCase = true) }
    val picked = shown.filter { it.second in draft }
    val rest = shown.filter { it.second !in draft }
    fun toggle(code: String) { draft = if (code in draft) draft - code else draft + code }

    @Composable
    fun group(items: List<Pair<String, String>>) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised)) {
            items.forEachIndexed { i, (name, code) ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Palette.Line))
                Row(
                    Modifier.fillMaxWidth().clickable { toggle(code) }.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = Palette.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    val on = code in draft
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (on) Color.White else Color.Transparent)
                            .border(1.5.dp, if (on) Color.White else Palette.Muted, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(painterResource(R.drawable.ic_check), "Selected", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    Sheet("Filter By Language", onClose = onClose, onBack = onBack) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Palette.Muted.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), "Search", tint = Palette.Text, modifier = Modifier.size(22.dp))
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search languages") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
            if (picked.isNotEmpty()) {
                group(picked)
                Spacer(Modifier.height(12.dp))
            }
            group(rest)
        }
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
        ) { Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        Button(
            onClick = { draft = emptySet() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Raised, contentColor = Palette.Text),
        ) { Text("Clear", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
    }
}

/** The followed list has no thumbnails: show it at once, then fill each card in as its channel info arrives. */
@Composable
fun EnrichedStreams(base: List<Stream>, nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    var shown by remember(base) { mutableStateOf(base) }
    LaunchedEffect(base) {
        if (KickOAuth.available()) {
            // official API: thumbnails, title, language and viewers of every channel in one call
            try {
                val info = PublicApi.channels(base.map { it.slug })
                shown = shown.map { s -> info[s.slug.lowercase()]?.let { i ->
                    s.copy(
                        thumb = i.thumb.ifEmpty { s.thumb }, title = i.title.ifEmpty { s.title },
                        language = i.language.ifEmpty { s.language }, category = i.category.ifEmpty { s.category },
                        viewers = if (i.viewers > 0) i.viewers else s.viewers,
                    )
                } ?: s }
            } catch (e: Exception) {
            }
        }
        val sem = kotlinx.coroutines.sync.Semaphore(4)
        val lock = kotlinx.coroutines.sync.Mutex()
        val catCache = HashMap<String, List<Stream>>()
        kotlinx.coroutines.coroutineScope {
            shown.filter { it.thumb.isEmpty() }.forEach { s ->
                launch {
                    sem.withPermit {
                        var r = try { Browse.streamInfo(s.slug, s) } catch (e: Exception) { s }
                        if (r.thumb.isEmpty() && r.language.isNotEmpty()) {
                            // same listing as Browse > Livestreams > language filter, which does carry thumbnails
                            val lg = LANGS.firstOrNull { it.first.equals(r.language, true) || it.second.equals(r.language, true) }
                            val code = lg?.second ?: r.language.lowercase()
                            val name = lg?.first ?: r.language
                            val list = lock.withLock {
                                catCache.getOrPut("lang:$code") { try { Browse.webByLang(code, name) } catch (e: Exception) { emptyList() } }
                            }
                            val hit = list.firstOrNull { it.slug.equals(r.slug, true) }
                            if (hit != null) r = r.copy(thumb = hit.thumb)
                        }
                        if (r.thumb.isEmpty() && r.categorySlug.isNotEmpty()) {
                            // same thumbnail the category listing shows
                            val list = lock.withLock {
                                catCache.getOrPut(r.categorySlug) { try { Browse.live(r.categorySlug, 3) } catch (e: Exception) { emptyList() } }
                            }
                            val hit = list.firstOrNull { it.slug.equals(r.slug, true) }
                            if (hit != null) r = r.copy(thumb = hit.thumb)
                        }
                        val done = r
                        shown = shown.map { if (it.slug == done.slug) done else it }
                    }
                }
            }
        }
    }
    StreamList(shown, nav, bottomPad)
}

/** Shows the list at once, then fills in missing profile pictures in the background. */
@Composable
fun FilledStreams(base: List<Stream>, nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    var shown by remember(base) { mutableStateOf(base) }
    LaunchedEffect(base) {
        try { shown = Browse.fillAvatars(base) } catch (e: Exception) { }
    }
    StreamList(shown, nav, bottomPad)
}

@Composable
fun StreamList(list: List<Stream>, nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No live channels found.", color = Palette.Muted)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.slug }) { s -> StreamCard(s, Modifier.fillMaxWidth()) { nav.watch(s.slug) } }
    }
}

@Composable
fun ClipItem(c: Clip, nav: Nav) {
    MediaCard(
        thumb = c.thumb, views = c.views, duration = c.duration, avatar = "", name = c.channel.ifEmpty { c.creator },
        title = c.title,
        sub = (if (c.creator.isNotEmpty()) "Clipped by " + c.creator else "") + (ago(c.created).let { if (it.isNotEmpty()) " • $it" else "" }),
        category = "",
        modifier = Modifier.fillMaxWidth(),
    ) { nav.play(c.title.ifEmpty { "Clip" }, c.url, null, c) }
}

// -------------------------------------------------------------- following --
@Composable
fun FollowingScreen(
    loggedIn: Boolean, following: Load<List<Live>>, nav: Nav, onLogin: () -> Unit, onRetry: () -> Unit,
    bottomPad: androidx.compose.ui.unit.Dp,
) {
    var tab by remember { mutableIntStateOf(0) }
    if (!loggedIn) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sign in to see who you follow", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Live channels you follow on Kick will appear here.", color = Palette.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
            ) { Text("Sign in", fontWeight = FontWeight.Bold) }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Tabs(listOf("Livestreams", "Channels"), tab, { tab = it }, kick = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        if (tab == 0) {
            when (following) {
                is Load.Idle, is Load.Loading -> SkeletonList()
                is Load.Fail -> Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load", color = Palette.Text, fontWeight = FontWeight.Bold)
                    Text(following.message.take(90), color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.AccentInk),
                    ) { Text("Retry", fontWeight = FontWeight.Bold) }
                }
                is Load.Ok -> if (following.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("None of the channels you follow are live right now.", color = Palette.Muted)
                    }
                } else {
                    EnrichedStreams(following.data.map { it.toStream() }, nav, bottomPad)
                }
            }
        } else {
            val all = rememberLoader("following-all") { Browse.followedAll() }
            LoadView(all, skeleton = { SkeletonList() }) { list ->
                LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                    items(list, key = { it.slug }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { if (c.live) nav.watch(c.slug) else nav.profile(c.slug) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.avatar, c.name, 56.dp, ring = c.live)
                            Text(c.name, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
                            if (c.live) Text("LIVE", color = Palette.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Icon(painterResource(R.drawable.ic_heart), null, tint = Color.White, modifier = Modifier.padding(start = 12.dp).size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- search --
private fun history(ctx: Context): List<String> =
    ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE).getString("hist", "")!!.split("\n").filter { it.isNotEmpty() }

private fun saveHistory(ctx: Context, list: List<String>) {
    ctx.getSharedPreferences("tuff", Context.MODE_PRIVATE).edit().putString("hist", list.take(15).joinToString("\n")).apply()
}

@Composable
fun SearchScreen(nav: Nav, bottomPad: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var hist by remember { mutableStateOf(history(ctx)) }
    val top = rememberLoader("search-top") { Browse.live(null, 1) }

    fun go(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        q = t
        submitted = t
        hist = (listOf(t) + hist.filter { !it.equals(t, true) }).take(15)
        saveHistory(ctx, hist)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = q,
                onValueChange = { q = it; if (it.isEmpty()) submitted = "" },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search Kick...") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = Palette.Text) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go(q) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text, cursorColor = Palette.Accent,
                ),
            )
            Text("Go", color = Palette.Accent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 14.dp).clickable { go(q) })
        }
        if (submitted.isNotEmpty()) {
            val res = rememberLoader("search", submitted) { Browse.search(submitted) }
            LoadView(res, skeleton = { SkeletonList(2) }) { list ->
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text("No channels found.", color = Palette.Muted) }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                        items(list, key = { it.slug }) { c -> ChannelLine(c.name, c.avatar, c.live, if (c.live) c.title.ifEmpty { "Live" } else (if (c.followers > 0) fmtViewers(c.followers) + " followers" else ""), c.verified) {
                            if (c.live) nav.watch(c.slug) else nav.profile(c.slug)
                        } }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
                items(hist, key = { "h$it" }) { h ->
                    Row(Modifier.fillMaxWidth().clickable { go(h) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(
                            painterResource(R.drawable.ic_close), "Remove", tint = Palette.Text,
                            modifier = Modifier.size(22.dp).clickable { hist = hist.filter { it != h }; saveHistory(ctx, hist) },
                        )
                    }
                }
                item {
                    LoadView(top) { list ->
                        Column {
                            list.take(12).forEach { s -> ChannelLine(s.name, s.avatar, true, s.category) { nav.watch(s.slug) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelLine(name: String, avatar: String, live: Boolean, sub: String, verified: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(avatar, name, 52.dp, ring = live)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (verified) { Spacer(Modifier.width(5.dp)); VerifiedBadge(17.dp) }
            }
            if (sub.isNotEmpty()) Text(sub, color = Palette.Muted, fontSize = 14.sp, maxLines = 1)
        }
        if (live) Text("LIVE", color = Palette.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

// --------------------------------------------------------------- category --
@Composable
fun CategoryScreen(slug: String, name: String, nav: Nav, onBack: () -> Unit) {
    val l = rememberLoader("category", slug) {
        val id = try { Browse.categories().firstOrNull { it.slug == slug }?.id ?: 0 } catch (e: Exception) { 0 }
        Browse.liveOfCategory(slug, id, 3)
    }
    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_left), "Back", tint = Palette.Text) }
            Text(name, color = Palette.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        LoadView(l, skeleton = { SkeletonList() }) { list -> FilledStreams(list, nav, 24.dp) }
    }
}
