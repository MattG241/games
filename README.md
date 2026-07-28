# games

## FlowForge ⚡

A **Make.com-style visual automation builder for Android** — triggers, chained modules, `{{2.body}}`
data mapping, routers, iterators, filters, and a run log showing every module's input and output.
**19 triggers and 94 actions** reach the device for real: SMS, calls, notifications (including
replying to them inline), Wi-Fi, Bluetooth, NFC, camera, microphone, screen capture, on-device OCR
and barcode scanning, contacts, calendar, files, SQLite, UI automation via the accessibility
service, any HTTP API — plus an optional privileged tier through Shizuku or root.

Built as a native Kotlin/Compose app, but the APK is produced **entirely in the cloud** by GitHub
Actions — run the workflow from your phone, then install the APK it publishes. No computer, no
Android Studio.

➡️ **See [`flowforge/`](./flowforge/) for the module catalog, mapping language, and install steps.**

## RetroPlay 🎮

A complete, phone-only retro game emulator app (NES / SNES / Game Boy / GBA / Genesis) that can be
generated and installed as an APK **entirely on an Android phone** — no computer or Android Studio
required. Built as a self-contained HTML/CSS/JS + WebAssembly bundle for on-device APK builders
(iappyxOS, VibeApp, and similar WebView-shell tools).

➡️ **See [`retroplay/`](./retroplay/) for the full app and step-by-step build/install instructions.**

> RetroPlay ships **no games**. Load only ROM files you legally own.
