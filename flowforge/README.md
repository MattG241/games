# FlowForge ⚡

A Make.com-style visual automation builder that runs **natively on Android**, with real access to
the device: SMS, notifications, calls, Wi-Fi, Bluetooth, battery, location, torch, volume, media
keys, files, other apps' intents, and any HTTP API.

You build a **scenario** the way you would in Make: pick a trigger, chain modules after it, and map
one module's output into the next with `{{2.json.items[0].name}}` tokens. Every run is logged with
per-module input and output so you can see exactly what happened.

---

## Getting it on your phone — no computer needed

The APK is built in the cloud by GitHub Actions. You never install Android Studio.

1. On your phone, open this repository on **github.com** or in the **GitHub mobile app**.
2. Go to **Actions → Build FlowForge APK → Run workflow**.
   (It also runs automatically on every push that touches `flowforge/`.)
3. Wait ~4 minutes. When it finishes, open **Releases** — the newest one is
   `FlowForge build <n>` with a `FlowForge-<n>.apk` attached.
4. Tap the `.apk` to download, then tap the download to install. Android will ask you to allow
   installs from unknown sources for your browser — allow it once.
5. Open FlowForge → **Settings** and grant the access your scenarios need.

The APK is signed with the standard Android debug key, so no signing secrets are stored anywhere.

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

### Triggers

| | |
|---|---|
| Run manually | Tap Run, the quick-settings tile, or a `flowforge://run/<name>` link |
| Schedule | Every N minutes, or daily at a time with an optional day filter |
| Webhook | A local HTTP listener — anything on your network can start the scenario |
| SMS received | Optionally filtered by sender or body text |
| Notification posted | Any app's notifications (needs notification access) |
| Phone call state | Ringing / answered / ended |
| Power, Battery level, Wi-Fi, Bluetooth, Headset, Airplane mode, Screen & unlock | |
| Shake device | Accelerometer, with adjustable sensitivity |
| Device booted | |

### Actions

**HTTP** — request (all verbs, headers, bearer/basic auth, JSON parsing), download file, webhook response.

**Notify** — post a notification, send an SMS, speak text aloud, show a toast.

**Device** — open an app, send *any* intent (activity / broadcast / service, with extras), open a
URL, read & write the clipboard, vibrate, set volume, ringer mode, Do Not Disturb, brightness,
torch, media transport keys, get location, read full device state, open a settings panel.

**Files** — read and write text files.

**Flow control** — filter, router with per-route conditions, iterator, repeater, sleep, stop,
running aggregate.

**Tools** — set variable, compose/transform expression, parse JSON, regex match, key/value data
store, log a message, run another scenario.

### The mapping language

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

* Runtime permissions — SMS, phone, contacts, location, camera (torch), notifications, Bluetooth.
* **Notification access** — for the Notification posted trigger.
* **Modify system settings** — for screen brightness.
* **Do Not Disturb access** — for silent/DND modules.
* **Ignore battery optimisation** — so Android stops pausing scheduled scenarios.
* **Exact alarms** — so schedules fire on time.

A quiet ongoing notification keeps the engine's listeners alive; you can turn it off in Settings,
but then only schedules, webhooks and manual runs will work reliably.

### Where Android says no

Some things are simply not available to a normal app, and FlowForge is honest about them rather
than failing silently:

* **Wi-Fi and Bluetooth cannot be toggled silently** on Android 10+. The *Open settings panel*
  module opens the one-tap panel instead.
* **Reading the clipboard in the background** returns empty on Android 10+ — only the focused app
  can read it.
* **Incoming call numbers** are often blank without being the default dialer.
* Scenarios that need the screen (opening an app, sending an activity intent) will be queued by
  the system if the phone is locked.

For anything deeper, the *Send intent* module is the escape hatch — it will drive Tasker, Termux,
Home Assistant, or any app that exposes an intent.

---

## Blueprints

Every scenario is one JSON document, exactly like a Make blueprint. The editor's overflow menu has
**View blueprint JSON** and **Copy blueprint**; Settings has **Import a blueprint**. That is how you
move a scenario between phones or share one.

---

## Project layout

```
flowforge/app/src/main/java/com/flowforge/android/
  model/       Blueprint, ModuleNode, and the catalog of every trigger and action
  engine/      Expression (the {{ }} language), Values, Engine (the executor)
    runners/   Net, Notify, Device and Tool module implementations
  core/        FlowService (live triggers), Scheduler (alarms), WebhookServer
  triggers/    Broadcast receivers, notification listener, quick-settings tile
  ui/          Compose screens — list, editor canvas, config sheets, history, settings
```

`app/src/test` holds JVM tests for the expression engine — they run in CI before every APK build.

## Building locally (optional)

```bash
cd flowforge
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle testDebugUnitTest assembleRelease
```
