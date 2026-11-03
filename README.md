# TUFF KICK

A fast, native Android app for [Kick.com](https://kick.com): watch live streams with low delay, read and send chat, and browse channels, in a Kick-style dark UI with Kick green.

> **Unofficial.** TUFF KICK is a fan project. It is not made by, affiliated with or endorsed by Kick. "Kick" and its logos belong to their owners.

[Persian / فارسی](README.fa.md)

## Download

Get the newest `TUFF-KICK-*.apk` from the [Releases](../../releases) page, open it and install. Android 7.0 or newer.

- If Google Play Protect says *"App scan recommended"*, tap **Install without scanning** (or **Scan app**). The app is not from the Play Store, so Google has not seen it before.
- Every release is signed with the same key, so updates install over the old version. Check the download with the `.sha256` file next to it: `sha256sum -c TUFF-KICK-vX.Y.Z.apk.sha256`

## Features

**Watching**
- Live player with the lowest delay the stream allows (Amazon IVS player, ExoPlayer as fallback), Low / Minimum delay mode, LIVE button showing how far behind live you are
- Quality: Auto, Crisp, Lite or a fixed resolution (kept when you refresh the player)
- 10 second back / forward, player refresh, mute, rotate to fullscreen

**Chat**
- Live chat in the stream page and in a channel's Chat tab
- Kick, 7TV and emoji emotes with real sizes (size setting), animated emotes, favourites, `@` mentions and `:` emote search
- Official Kick badges, level badges by tier and collectibles; only a user's active badges show in chat
- Pinned message and prediction cards, replies, mute, moderation (delete, timeout, ban) when you have the rights
- Tap a name for the user card: banner, level colour, all badges, follow date, subscription and streak
- Channel points and gifter leaderboard

**Browsing**
- Home, Browse (categories, livestreams, clips), Following, Search
- Channel pages with links, about, clips and videos

## Privacy and security

- No analytics, no ads, no tracking. The app talks only to Kick, 7TV and Amazon IVS (Kick's video host).
- Your Kick password is typed into Kick's own page inside the app; the app never sees it.
- Cloud backup and device transfer are switched off, so cookies and tokens are not copied anywhere. All traffic is HTTPS.
- Report a security problem privately: see [SECURITY.md](SECURITY.md).

## Problems and ideas

Open an [issue](../../issues/new/choose) and say which app version you use (shown at the bottom of the account menu).

## Credits

[Kick](https://kick.com), [7TV](https://7tv.app), [Amazon IVS Player](https://docs.aws.amazon.com/ivs/), [AndroidX Media3](https://developer.android.com/media/media3), [Coil](https://coil-kt.github.io/coil/), [OkHttp](https://square.github.io/okhttp/), Jetpack Compose.

## License

Free to install and use for personal use. The source code is not published; do not redistribute modified copies. See [LICENSE](LICENSE).
