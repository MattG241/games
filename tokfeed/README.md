# TokFeed 📱

Paste video links → watch them as a full-screen, swipeable vertical feed (TikTok / Reels / Shorts
style).

**Everything runs in the browser.** There is no server, no build step, no `npm install`, and no
`yt-dlp` binary to keep alive — so you can host it and use it entirely from your phone.

---

## Get it running on your phone (no computer)

### Option A — GitHub Pages (recommended, ~2 minutes)

All from the phone browser:

1. Open **github.com/MattG241/games** → **Settings** → **Pages**.
2. Under *Build and deployment* → **Source: Deploy from a branch**.
3. Pick the branch that contains this folder (`claude/tiktok-video-feed-app-qbhy1l`, or `main`
   once it's merged), folder **`/ (root)`** → **Save**.
4. Wait ~1 minute, then open:

   ```
   https://mattg241.github.io/games/tokfeed/
   ```

5. Chrome menu (⋮) → **Add to Home screen**. On iPhone: Share → **Add to Home Screen**.

That gives you a real installed app: own icon, no browser chrome, full-screen feed.

### Option B — wrap it as an APK on-device

Same approach as [`../retroplay/`](../retroplay/): zip this folder and feed it to an on-device
WebView-shell APK builder (iappyxOS, VibeApp, etc.), with `index.html` as the entry point. No
Android Studio needed.

> HTTPS matters. Installing as a PWA, the clipboard **Paste** button, and the service worker all
> require `https://` — which GitHub Pages gives you for free. Opening the files over `file://`
> still works for a quick look, but you lose those three things.

---

## Using it

1. Paste one or more links into the box — **one per line**. Pasting messy share text is fine
   (`look at this 😂 https://vm.tiktok.com/ZSxxx/`); TokFeed pulls the URLs out and keeps your
   order, dropping duplicates.
2. And/or tap **Add videos from this phone** to pick video files from your camera roll or files
   app — see [Your own video files](#your-own-video-files) below.
3. Tap **Extract & Load**. Each link reports its own status, so a bad link never blocks the rest.
4. The feed opens full-screen.

| Gesture | Does |
|---|---|
| Swipe up / down | Next / previous video |
| Tap the video | Pause / play |
| Speaker button | Mute / unmute (starts muted — browsers require it for autoplay) |
| Drag the bottom bar | Scrub |
| Back arrow / Android back | Return to the link list |
| Tap the small link text | Open the original post |

Only the visible video plays; the one either side is preloaded, and anything further away is
unloaded so your phone isn't holding a stack of video decoders open.

### Share straight from TikTok

Once installed as a PWA on Android, TokFeed registers as a **share target**. In the TikTok app:
**Share → TokFeed**. The link lands in the box (stacking up if you share several), and extraction
starts automatically.

### Your own video files

Tap **Add videos from this phone** to pick one or more videos from your camera roll or files app.
They play in the feed exactly like extracted links — same swipe, autoplay, mute and scrubbing.

- Files are stored **in the app** (IndexedDB), so they survive closing the app and reloading —
  you pick them once, not every time.
- They never leave your phone. There's no server, so nothing is uploaded anywhere.
- Feed order is **pasted links first, then files** in the order you added them.
- Files on their own are a valid feed — leave the link box empty and tap **Extract & Load**.
- Remove one with the **×** next to it, or all of them via *Settings → Clear cache*.

Videos are large, so keep an eye on space: the *On this phone* card shows the total. If your phone
refuses to store one, TokFeed tells you which file and why instead of failing silently.

### Saved lists

**Settings & advanced → Save current list** stores the links under a name. Saved lists show up as
chips on the input screen; tap one to reload it. Your last feed is also restored automatically the
next time you open the app.

---

## What it can play

| Source | How | Notes |
|---|---|---|
| **TikTok** | [tikwm.com](https://www.tikwm.com) public API | **No watermark.** Gets HD when available, plus title, author, duration and cover |
| **Direct video** (`.mp4`, `.webm`, `.mov`…) | Played as-is | Any link ending in a media file |
| **YouTube / Shorts** | Official embedded player | Autoplays muted; the feed drives play/pause via the iframe API |
| **Instagram Reels** | Instagram's embed | ⚠️ Needs a tap to start — Instagram blocks autoplay and has no public API |
| **Video files on your phone** | Picked with the file button, kept in IndexedDB | Never uploaded anywhere |
| **Anything else** | Tried as a direct video | Works if the URL is really a video file |

If a TikTok extraction fails, TokFeed retries the rate limiter, then a second tikwm endpoint, then
two public CORS relays before giving up — and tells you exactly what went wrong.

### Links expire — that's normal

TikTok's CDN URLs are signed and die after a couple of hours. TokFeed handles this for you: it
re-extracts anything older than 75 minutes when you open a cached feed, and re-extracts once more
automatically if a video fails to load. If it still won't play you get a **Retry** button and a
link to the original.

---

## Settings

- **Prefer HD** — ask for the higher-quality file. Off = smaller/faster, still watermark-free.
- **Auto-advance when a video ends** — off (default) loops each video like TikTok; on plays through
  the list and wraps around.
- **Custom resolver endpoint** — optional. Tried *first* for every link. Point it at your own
  service and TokFeed will use it:

  ```
  https://my-server.example/extract?url={url}
  ```

  It must allow CORS and return JSON containing a direct video URL in any of `url`, `play`,
  `video`, `direct`, `link`, `src` (nested under `data` or `result` is fine). This is the hook to
  add `yt-dlp` later — for Instagram, or any site the built-in resolvers can't handle. **TokFeed
  works fully without it.**

- **Clear cache** — wipes the saved feed and lists.

---

## Files

```
tokfeed/
├── index.html      both screens (input + feed)
├── styles.css      dark mobile-first UI, safe-area aware
├── resolvers.js    link → playable video (tikwm / YouTube / Instagram / direct / custom)
├── feed.js         scroll-snap feed, autoplay, preloading, progress, seeking
├── app.js          input screen, localStorage + IndexedDB, share target, glue
├── sw.js           service worker — caches the app shell only, never video
├── manifest.json   PWA manifest + Android share target
└── icons/
```

Plain scripts, no modules or bundler, so it works identically on GitHub Pages, from `file://`, and
inside a WebView APK shell.

---

## Known limits

- **HLS (`.m3u8`)** plays on iOS Safari but not Android Chrome, which has no built-in HLS support.
- **Instagram** won't autoplay (see above).
- **tikwm** is a free third-party service. It rate-limits (TokFeed paces itself to ~1 request/sec)
  and could change; the custom-resolver setting is the escape hatch.
- Needs a connection — the app shell is cached offline, videos are not.

## Please note

Built for personal use: watching links you already have in a nicer viewer. Bulk-downloading or
redistributing other people's videos violates TikTok's terms.
