# Changelog

All notable changes. Versions before 1.0 were numbered v12 to v33.

## v53
- Build fix

## v52
- Identity: colours come from Kick when it sends them; only badges the account has

## v51
- Sheets open under the player (Kick style); Kick-style chat settings, Identity (colour and badges), Active chatters, Kicks leaderboard, new bar icons

## v50
- Emote card opens with one tap (long press stays for the message menu)

## v49
- Smaller emote card

## v48
- Long press on an emote in chat shows its card (name, source, copy, open page)

## v47
- Messages that tag you get a green outline; long links wrap next to the username

## v46
- Channel points redesigned like Kick (coloured tiles, redeem screen, in-app predictions); white smiley button in chat

## v45
- Update notice: the app checks the public releases and offers the new version
- Rotating to full screen no longer reloads the stream or chat

## v40
- Kick-style tabs on Categories and Following, category viewer counts and follow hearts, optional Telegram link

## v39
- README: accurate description of sign-in; source published to the public repository

## v33
- Release pipeline: signed releases from tags, checksum file, issue templates, README, security policy
- Cloud backup and device transfer disabled; version text now comes from the build
- (the floating mini player is not part of this release)

## v29 - v31
- Only active badges in chat, all badges on the user card
- User card banner, level colour, generated banner when none exists
- "Newer messages" shows only after the viewer scrolls up

## v25 - v28
- Amazon IVS player with ExoPlayer fallback, player refresh button
- Adaptive low-delay logic, Minimum delay mode, LIVE / delay indicator
- ±10 second buttons

## v13 - v24
- Skeleton loading, verified badge, links sheet, inline profile chat, pinned and prediction cards
- Faster emote picker, favourites, `@` and `:` suggestions, 7TV real sizes and size setting
- User card, official Kick badges, tiered level badges, collectible badges
- v54: smaller Kick-size chatters sheet (empty groups hidden, OGs added), chat bar: smaller icons, no gear/Kicks text (long-press chatters icon = chat settings), Identity badges fall back to the account's badges seen in chat
- v55: original gear icon restored in chat bar
- v57: same as v55 (debug button dropped)
- v58: Identity reads Kick's real response (data.identity: color + badges), save tries PUT/PATCH/POST
- v59: chatters show profile pictures, Identity in the profile-page chat, level badge remembered for Identity
- v60: Identity lists every owned badge (from the user card), switched-on ones highlighted
- v61: Identity save falls back to the badges Kick itself listed when it answers 500
- v62: Identity reads/saves the newer badges list (level) separately
- v63: workflow downloads Gradle 8.7 itself from several mirrors (setup-gradle got HTTP 504)
- v64: Identity save uses POST first (the method Kick's site uses); OG listed under channel badges; subscriber image from chat
- v65: Identity save tries PUT first again and reports every failed attempt
- v66: Identity save sends the plain {color, badges} body again (the one that worked in v58), then the wrapped one
- v67: Identity colours snap to the exact colours seen in chat; a switched-off level badge can be switched on again
- v68: Identity reads the level number and picture from badges_v2
- v69: updated base colour list; friendlier colour error
- v70: Identity save reports when the level badge could not be saved and also tries the full badge object
- v71: release workflow also downloads Gradle itself (mirrors)
