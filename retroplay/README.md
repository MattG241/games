# 🎮 RetroPlay — phone-only retro emulator

A clean, dark-themed, fully on-device retro game emulator for **NES, SNES, Game Boy / Color, GBA and Genesis / Mega Drive**. It's a self-contained HTML/CSS/JS bundle designed to be wrapped into a signed, installable **APK entirely on an Android phone** — no computer, no Android Studio, no build server.

The emulation itself is done by **[EmulatorJS](https://emulatorjs.org)** (libretro cores compiled to WebAssembly), which RetroPlay loads on demand. RetroPlay provides the app shell: a library, ROM storage, controls, shaders, save states, settings, and a native-feeling UI.

> ⚖️ **Legal note:** RetroPlay contains **no games**. Load only ROM files of titles **you legally own**. You are responsible for the content you add.

---

## 📁 What's in the bundle

| File | Purpose |
|------|---------|
| `index.html` | App entry — library UI, modals, player container |
| `styles.css` | Full dark theme, responsive grid, CRT overlay, animations |
| `app.js` | Library, ROM storage (IndexedDB), console detection, settings, launch logic |
| `player.html` | The emulator surface — boots EmulatorJS inside an isolated iframe |
| `manifest.json` | PWA / app manifest (name, icons, fullscreen, theme) |
| `icons/` | App icons (192, 512, apple-touch) |

Everything is **relative-path** and self-contained. The only runtime network call is fetching the WASM emulator core from a CDN the first time you open a game (see *Offline* below).

---

## ✨ Features

- **Library** — grid of your games with per-console badges, filter chips, and "remove" controls.
- **Add any ROM you own** — file picker accepts `.nes .smc .sfc .gb .gbc .gba .md .gen .smd .bin .zip .7z`. Zip/bin files prompt you to pick the console.
- **Persistent storage** — ROMs are saved in **IndexedDB**, so they stay in your library between launches.
- **Save states** — quick save/load, persisted per-game in the browser (handled by EmulatorJS, keyed by a stable game id).
- **Touch controls** — responsive on-screen D-pad + buttons (from EmulatorJS), tuned for phones.
- **Hardware gamepads** — auto-detected when connected via USB/Bluetooth.
- **Shaders** — GLSL CRT/scanline shaders (Easymode, Aperture, Geom, Mattias, Scanlines, SABR) plus a lightweight always-on **CSS CRT overlay** toggle.
- **Pixel-perfect or smooth scaling** — your choice in Settings.
- **Auto-pause on background** — pauses when you leave the app.
- **Fullscreen / immersive** — hides system chrome for a console feel.
- **Android back button** — returns to the library instead of quitting.

---

## 📲 Build it into an APK on your phone (no PC)

You can use any on-device "HTML → APK" builder that injects a web bundle into a signed WebView shell. Popular options: **iappyxOS**, **VibeApp**, **WebView Gold-style builders**, or **Median/GoNative-style** wrappers. The steps below are generic; adapt the button names to your tool.

### Step 1 — Get the files onto your phone
1. Download this `retroplay/` folder (or zip it) to your phone's storage.
2. If you only have the raw text, create the 5 files in a folder using any file-manager or code editor app (e.g. **Acode**, **Spck Editor**), keeping the exact filenames and the `icons/` subfolder.

### Step 2 — Install an on-device builder
1. Install **iappyxOS**, **VibeApp**, or a similar phone-based APK builder from its official source.
2. Open it and choose **Create new app → Web / HTML app** (wording varies).

### Step 3 — Point the builder at RetroPlay
- **Folder / bundle mode (best):** select the whole `retroplay/` folder as the app's web content, and set **`index.html`** as the entry/start page.
- **Single-page paste mode:** if the tool only accepts one HTML file, use the inlined build (see *"Single-file build"* below) and paste it as the app content.

### Step 4 — Configure the app shell
Match these settings in the builder so RetroPlay feels native and runs at full speed:

| Setting | Value |
|---------|-------|
| App name | `RetroPlay` |
| Package id | e.g. `com.yourname.retroplay` |
| App icon | `icons/icon-512.png` |
| Orientation | Sensor / Auto |
| Fullscreen / immersive | **On** |
| Hardware acceleration | **On** |
| JavaScript | **Enabled** |
| DOM storage / IndexedDB | **Enabled** (required for the library & saves) |
| File access / storage permission | **Enabled** (required to pick ROMs) |
| Allow file uploads (`<input type=file>`) | **Enabled** |
| Internet permission | **Enabled** (to fetch the emulator core once) |
| Mixed content / allow file access from URLs | **Enabled** |

### Step 5 — Build & install (one tap)
1. Tap **Build APK** (the builder signs it with its own debug/managed key on-device).
2. When it finishes, tap **Install** — approve *"install from unknown sources"* if Android asks.
3. Launch **RetroPlay**, tap **+ Add Game**, pick a ROM you own, and play. 🎉

---

## 🌐 Offline / self-hosting the cores

By default the emulator core (a few hundred KB–few MB of WASM per system) is fetched from
`https://cdn.emulatorjs.org/stable/data/` the first time you open a game, then cached by the WebView.

To run **100% offline** inside the APK:
1. Download the EmulatorJS **`data/`** folder from the [EmulatorJS releases](https://github.com/EmulatorJS/EmulatorJS) and place it next to these files (e.g. `retroplay/emulatorjs/data/`).
2. In the app, open **⚙ Settings → Emulator core source** and set it to the local path, e.g. `emulatorjs/data/`.
3. Rebuild the APK with that folder included. No further network access is needed.

---

## 🕹️ Supported systems & file types

| Console | Core | Accepts |
|---------|------|---------|
| NES | `nes` | `.nes` |
| SNES | `snes` | `.smc .sfc .fig .swc` |
| Game Boy / Color | `gb` | `.gb .gbc` |
| Game Boy Advance | `gba` | `.gba` |
| Genesis / Mega Drive | `segaMD` | `.md .gen .smd .68k` |
| Any of the above | — | `.zip .7z .bin` → you pick the console |

---

## 🛠️ Single-file build (paste-mode builders)

Some minimal builders only take one HTML file. To produce one, inline `styles.css` and `app.js` into `index.html`:

- Replace `<link rel="stylesheet" href="styles.css" />` with `<style> …contents of styles.css… </style>`.
- Replace `<script src="app.js"></script>` with `<script> …contents of app.js… </script>`.
- Host `player.html` alongside it, **or** point the iframe at a hosted copy. (The player must stay a separate document so EmulatorJS can be torn down cleanly between games.)

The multi-file folder build is recommended whenever the tool supports it.

---

## 🧯 Troubleshooting

- **"Could not load emulator core."** — the WebView has no internet, or the CDN is blocked. Enable Internet permission, or self-host the cores (see *Offline*).
- **File picker does nothing** — enable Storage permission and "allow file uploads" in the builder.
- **Library empties on restart** — DOM storage / IndexedDB is disabled in the shell; enable it.
- **Slow / stutters** — turn on Hardware acceleration in the builder, disable heavy CRT shaders, keep smoothing off.
- **Controls too small** — use fullscreen/immersive; EmulatorJS control size can also be adjusted from its in-game menu.

---

Built to be beautiful, fast, and 100% phone-installable. Enjoy responsibly — **your ROMs, your rights.**
