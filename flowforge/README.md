# FlowForge ⚡

A Make.com-style visual automation builder that runs **natively on Android**, with real access to
the device: SMS, calls, notifications (including replying to them), Wi-Fi, Bluetooth, NFC, camera,
microphone, screen capture, OCR, contacts, calendar, files, a local SQLite database, UI automation
through the accessibility service, and — if you want it — a privileged tier via Shizuku or root.

You build a **scenario** the way you would in Make: pick a trigger, chain modules after it, and map
one module's output into the next with `{{2.json.items[0].name}}` tokens. Every run is logged with
per-module input and output so you can see exactly what happened.

**19 triggers, 94 actions.**

---

## Getting it on your phone — no computer needed

The APK is built in the cloud by GitHub Actions. You never install Android Studio.

1. On your phone, open this repository on **github.com** or in the **GitHub mobile app**.
2. Go to **Actions → Build FlowForge APK → Run workflow**.
   (It also runs automatically on every push that touches `flowforge/`.)
3. Wait ~5 minutes. When it finishes, open **Releases** — the newest one is
   `FlowForge build <n>` with a `FlowForge-<n>.apk` attached (~39 MB).
4. Tap the `.apk` to download, then tap the download to install. Android will ask you to allow
   installs from unknown sources for your browser — allow it once.
5. Open FlowForge → **Settings** and grant the access your scenarios need.

The APK is signed with the standard Android debug key, so no signing secrets are stored anywhere.
It is ARM-only (every real Android phone); it will not install on an x86 emulator.

---

## How a scenario works

```
[1] Trigger          SMS received
 │
[2] Filter           {{1.text}} contains "deploy"
 │
[3] HTTP request     POST https://…  body: {"from":"{{1.from}}"}
 │
[4] Router  ─── Route A (if {{3.status}} equals 200) ─→ [5] Send notification
            └── Route B                                 └→ [6] Speak text
```

* **Module numbers are stable.** Module 3 is always `{{3.…}}`, wherever you move it.
* **Bundles flow forward.** Each module's output becomes available to everything after it.
* **Filters stop the run**; router branches run independently of each other.
* **Iterators fan out** — everything after an iterator runs once per array item.

---

## Triggers (19)

| Trigger | Fires on |
|---|---|
| Run manually | Tap Run, the quick-settings tile, a `flowforge://run/<name>` link, or a broadcast |
| Schedule | Every N minutes, or daily at a time with an optional day filter |
| Webhook | A local HTTP listener — anything on your network can start the scenario |
| SMS received | Optionally filtered by sender or body text |
| Notification posted | Any app's notifications, with the key needed to reply or dismiss |
| Notification dismissed | Same, on removal |
| Phone call state | Ringing / answered / ended |
| Power connected | Plugged in or unplugged |
| Battery level | Crossing a threshold, up or down |
| Wi-Fi state | Connected / disconnected, optionally to a named SSID |
| Bluetooth device | A named device connecting or disconnecting |
| Screen / unlock | Screen on, screen off, unlocked |
| Headset plugged | In or out |
| Airplane mode | On or off |
| Shake device | Accelerometer, adjustable sensitivity |
| Folder changed | A file created, modified or deleted in a watched folder |
| App opened | The foreground app changes (needs usage access) |
| NFC tag scanned | A tag tapped while FlowForge is open |
| Device booted | After a restart |

## Actions (94)

**Network** — HTTP request (all verbs, headers, bearer/basic auth, JSON parsing), download file,
multipart file upload, ping / TCP port check, WebSocket send-and-await, MQTT publish, webhook response.

**Communication** — send SMS, place a call, answer or end the current call, compose an email
(with attachment), open the share sheet.

**Notifications** — post a notification with up to three buttons that each run another scenario;
dismiss by key, by app, or all; **reply inline** to another app's notification (answer a chat
message without opening it); snooze a notification; speak text aloud; show a toast.

**Apps & intents** — launch an app, go home, fire an arbitrary intent (activity / broadcast /
service, with typed extras), open a URL or deep link, open any of 16 settings screens, set an alarm
or timer through the clock app.

**Device controls** — torch, screen brightness, screen timeout, volume per stream, ringer mode,
Do Not Disturb, vibrate patterns, wakelock (keep the screen awake), lock the screen, set the
wallpaper, set and read the clipboard, a full device-state bundle, the current foreground app, and
a one-shot sensor snapshot (light, proximity, accelerometer, pressure, humidity, temperature).

**Audio & media** — media transport keys, now-playing track metadata, play a file / URL / system
tone / beep, record audio from the microphone.

**Files** — read, write, append, copy, move, delete, create folder, list a folder (with glob and
recursion), zip and unzip, and **SQLite queries** against a local database file.

**Camera & screen** — take a photo silently with CameraX, take a screenshot via MediaProjection,
**OCR text from an image** and **scan barcodes / QR codes** — both on-device with ML Kit, nothing
leaves the phone.

**Location** — get a fix, start/stop background tracking to a JSONL log, open navigation.

**Connectivity** — suggest a Wi-Fi network to join, toggle Bluetooth, connect/disconnect a paired
Bluetooth audio device, write an NFC tag.

**Contacts & calendar** — look up a contact, create or update one, create a calendar event with a
reminder, query upcoming events.

**UI automation** (accessibility service) — tap by text / view id / content description /
coordinates, swipe and custom gestures, type into a field, scrape all visible screen text, and press
Back / Home / Recents / notification shade / quick settings / power dialog / lock screen.

**Privileged** (Shizuku or root) — run a shell command, toggle Wi-Fi / mobile data / airplane mode /
Bluetooth silently, force-stop or clear or enable/disable another app, grant and revoke its
permissions, send hardware key events, and read or write any system setting.

**Flow control** — filter, router with per-route conditions, iterator, repeater, sleep, stop, aggregate.

**Data & logic** — set variable, compose/transform, parse JSON, build JSON, text tools (replace,
split, join, trim, case, substring, template, pad, reverse), regex match, math, date/time
(format, parse, add, difference), hash / HMAC / Base64 / URL encoding, random values, a persistent
key/value data store, log a message, and run another scenario.

---

## The mapping language

Anywhere you see `{ }` next to a field you can insert a token.

```
{{1.text}}                          a field from module 1
{{2.json.items[0].name}}            nested objects and arrays
{{vars.counter}}                    a scenario variable
{{now}}  {{uuid}}                   built-ins
{{upper(trim(1.text))}}             functions, ";" separates arguments
{{1.level + 7}}   {{1.level < 20}}  arithmetic and comparisons
{{if(2.status == 200; "ok"; "bad")}}
{{join(pluck(2.json.items; "name"); ", ")}}
{{formatDate(now; "yyyy-MM-dd HH:mm")}}
```

Around 60 functions are available: text (`upper`, `replace`, `split`, `match`…), arrays (`first`,
`pluck`, `sum`, `sort`, `slice`…), logic (`if`, `and`, `default`…), numbers (`round`, `abs`,
`randomInt`…), dates (`formatDate`, `addDays`…) and encoding (`json`, `parseJSON`, `base64`,
`encodeURL`).

Unknown fields resolve to empty rather than crashing the run.

---

## Reaching FlowForge from outside the phone

**Webhook** — enable a scenario with a Webhook trigger, then from anything on the same network:

```bash
curl -X POST "http://<phone-ip>:8420/hook" -d '{"hello":"world"}'
```

The address and port are shown in Settings. Add a shared secret and pass it as `?key=` or an
`X-Key` header. A `Webhook response` module lets the scenario reply with its own body.

**Deep link** — `flowforge://run/My%20Scenario` from a browser, shortcut, or another app.

**Broadcast** — from Tasker, Termux, ADB, or any app:

```bash
am broadcast -a com.flowforge.android.RUN_SCENARIO \
  --es scenario "My Scenario" --es payload '{"key":"value"}'
```

The payload arrives as the trigger bundle, so `{{1.key}}` works.

---

## Access it asks for

Nothing is requested until you ask for it. Settings has one screen with everything:

* **Runtime permissions** — SMS, phone, calls, contacts, calendar, location, camera, microphone,
  notifications, Bluetooth.
* **Notification access** — the Notification triggers, and dismissing / replying / snoozing.
* **Accessibility service** — every UI automation module, and Lock the screen.
* **Usage access** — Foreground app and the App opened trigger.
* **Modify system settings** — screen brightness and screen timeout.
* **Do Not Disturb access** — silent mode and the DND module.
* **Ignore battery optimisation** — so Android stops pausing scheduled scenarios.
* **Exact alarms** — so schedules fire on time.
* **Screen capture** — Android asks for consent each time MediaProjection starts.

### The privileged tier

The **Privileged** modules need a channel Android does not give ordinary apps. Two ways to get one:

* **Shizuku** — install the Shizuku app, start it (via wireless debugging or ADB once), then grant
  FlowForge access from FlowForge's Settings. No root needed. This gives ADB-level rights.
* **Root** — if the phone is rooted, FlowForge will use `su` directly.

Every privileged module reports which channel actually ran it in its `via` output. Without either,
those modules fail with a clear message; nothing else in the app is affected.

### Where Android says no

Some things are genuinely not available, and FlowForge says so rather than failing silently:

* **Wi-Fi and Bluetooth cannot be toggled silently** by a normal app on Android 10+/13+. The
  *Open a settings screen* module opens the one-tap panel; the privileged *Toggle a radio* module
  does it silently if you have Shizuku or root.
* **Reading the clipboard in the background** returns empty on Android 10+ — only the focused app
  can read it.
* **Incoming call numbers** are often blank without being the default dialer.
* **Screen capture asks for consent** on every session from Android 14 — that is a platform rule.
* **Connecting to a named Wi-Fi network** uses the suggestion API, so Android decides when to
  actually join and may prompt once.
* Scenarios that need the screen (opening an app, sending an activity intent, UI automation) will be
  queued by the system if the phone is locked.

For anything deeper, the *Fire an intent* module is the escape hatch — it will drive Tasker,
Termux, Home Assistant, or any app that exposes an intent.

---

## Blueprints

Every scenario is one JSON document, exactly like a Make blueprint. The editor's overflow menu has
**View blueprint JSON** and **Copy blueprint**; Settings has **Import a blueprint**. That is how you
move a scenario between phones or share one.

---

## Project layout

```
flowforge/app/src/main/java/com/flowforge/android/
  model/       Blueprint, ModuleNode, and the catalog split by group
  engine/      Expression (the {{ }} language), Values, Engine (the executor)
    runners/   Net, Comms, Notify, Device, Media, Connectivity, File, Vision,
               UI, Privileged, Data and Tool module implementations
  core/        FlowService (live triggers), Scheduler, WebhookServer,
               ScreenCapture (MediaProjection), ShellRunner (Shizuku / root)
  triggers/    Receivers, notification listener, accessibility service,
               NFC activity, quick-settings tile
  ui/          Compose screens — list, editor canvas, config sheets, history, settings
```

`app/src/test` holds JVM tests for the expression engine — they run in CI before every APK build.

## Building locally (optional)

```bash
cd flowforge
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle testDebugUnitTest assembleRelease
```
