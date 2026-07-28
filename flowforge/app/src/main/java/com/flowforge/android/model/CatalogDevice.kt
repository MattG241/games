package com.flowforge.android.model

internal val APP_INTENT_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "app.open", "Launch app", "Apps & intents", "📲", C_DEVICE,
        summaryKey = "package",
        params = listOf(ParamSpec("package", "App", ParamType.APP, "")),
        outputs = listOf("package", "launched"),
    ),
    ModuleSpec(
        "app.home", "Go home / close FlowForge", "Apps & intents", "🏠", C_DEVICE,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Go home", listOf("Go home", "Close FlowForge"), mappable = false)
        ),
        outputs = listOf("action"),
    ),
    ModuleSpec(
        "intent.send", "Fire an intent", "Apps & intents", "⚡", C_DEVICE,
        summaryKey = "action",
        description = "The escape hatch — drive any app that exposes an intent (Tasker, Termux, Home Assistant, share sheets).",
        params = listOf(
            ParamSpec("kind", "Deliver as", ParamType.SELECT, "Activity", listOf("Activity", "Broadcast", "Service"), mappable = false),
            ParamSpec("action", "Action", ParamType.TEXT, "", hint = "android.intent.action.VIEW"),
            ParamSpec("uri", "Data URI", ParamType.TEXT, "", hint = "https://… or geo:… or tel:…"),
            ParamSpec("package", "Target package (optional)", ParamType.APP, ""),
            ParamSpec("component", "Component class (optional)", ParamType.TEXT, ""),
            ParamSpec("mimeType", "MIME type (optional)", ParamType.TEXT, ""),
            ParamSpec("extras", "Extras (one per line, key=value)", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("sent", "action"),
    ),
    ModuleSpec(
        "device.url", "Open URL or deep link", "Apps & intents", "🔗", C_DEVICE,
        summaryKey = "url",
        params = listOf(ParamSpec("url", "URL", ParamType.TEXT, "")),
        outputs = listOf("opened"),
    ),
    ModuleSpec(
        "device.settingsPanel", "Open a settings screen", "Apps & intents", "⚙", C_DEVICE,
        summaryKey = "panel",
        description = "Android 10+ blocks silent Wi-Fi/Bluetooth toggles; this opens the one-tap panel instead.",
        params = listOf(
            ParamSpec(
                "panel", "Screen", ParamType.SELECT, "Wi-Fi",
                listOf(
                    "Wi-Fi", "Internet", "Bluetooth", "NFC", "Volume", "Airplane mode",
                    "Do Not Disturb", "Battery saver", "Display", "Sound", "Location",
                    "Accessibility", "Date & time", "Developer options", "App details", "All settings",
                ),
                mappable = false,
            )
        ),
        outputs = listOf("panel"),
    ),
    ModuleSpec(
        "clock.alarm", "Set alarm or timer", "Apps & intents", "⏱", C_DEVICE,
        summaryKey = "kind",
        description = "Hands the request to the phone's clock app.",
        params = listOf(
            ParamSpec("kind", "Create", ParamType.SELECT, "Timer", listOf("Timer", "Alarm"), mappable = false),
            ParamSpec("seconds", "Timer length (seconds)", ParamType.NUMBER, "300"),
            ParamSpec("time", "Alarm time", ParamType.TIME, "07:00"),
            ParamSpec("label", "Label", ParamType.TEXT, "FlowForge"),
            ParamSpec("skipUi", "Create without opening the clock app", ParamType.BOOL, "true", mappable = false),
        ),
        outputs = listOf("created", "kind"),
    ),
)

internal val DEVICE_CONTROL_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "device.torch", "Torch", "Device controls", "🔦", C_DEVICE,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "State", ParamType.SELECT, "On", listOf("On", "Off", "Toggle"), mappable = false)
        ),
        outputs = listOf("state"),
    ),
    ModuleSpec(
        "device.brightness", "Set screen brightness", "Device controls", "☀", C_DEVICE,
        params = listOf(
            ParamSpec("percent", "Brightness (0-100)", ParamType.NUMBER, "60"),
            ParamSpec("auto", "Turn off auto-brightness first", ParamType.BOOL, "true", mappable = false),
        ),
        outputs = listOf("percent"),
        specialAccess = SpecialAccess.WRITE_SETTINGS,
    ),
    ModuleSpec(
        "device.screenTimeout", "Set screen timeout", "Device controls", "⌛", C_DEVICE,
        summaryKey = "seconds",
        params = listOf(ParamSpec("seconds", "Timeout (seconds)", ParamType.NUMBER, "60")),
        outputs = listOf("seconds"),
        specialAccess = SpecialAccess.WRITE_SETTINGS,
    ),
    ModuleSpec(
        "device.volume", "Set volume", "Device controls", "🔊", C_DEVICE,
        summaryKey = "stream",
        params = listOf(
            ParamSpec("stream", "Stream", ParamType.SELECT, "Media", listOf("Media", "Ring", "Notification", "Alarm", "Call"), mappable = false),
            ParamSpec("percent", "Level (0-100)", ParamType.NUMBER, "50"),
        ),
        outputs = listOf("stream", "level", "max"),
    ),
    ModuleSpec(
        "device.ringer", "Set ringer mode", "Device controls", "🔕", C_DEVICE,
        summaryKey = "mode",
        params = listOf(
            ParamSpec("mode", "Mode", ParamType.SELECT, "Silent", listOf("Normal", "Vibrate", "Silent"), mappable = false)
        ),
        outputs = listOf("mode"),
        specialAccess = SpecialAccess.DND_POLICY,
    ),
    ModuleSpec(
        "device.dnd", "Do not disturb", "Device controls", "🌙", C_DEVICE,
        summaryKey = "mode",
        params = listOf(
            ParamSpec("mode", "Mode", ParamType.SELECT, "On", listOf("On", "Off", "Priority only", "Alarms only"), mappable = false)
        ),
        outputs = listOf("mode"),
        specialAccess = SpecialAccess.DND_POLICY,
    ),
    ModuleSpec(
        "device.vibrate", "Vibrate", "Device controls", "📳", C_DEVICE,
        params = listOf(
            ParamSpec("pattern", "Pattern (ms, comma separated)", ParamType.TEXT, "0,200,100,200", mappable = false)
        ),
        outputs = listOf("vibrated", "durationMs"),
    ),
    ModuleSpec(
        "device.wakelock", "Keep screen awake", "Device controls", "👁", C_DEVICE,
        summaryKey = "action",
        description = "Holds a wakelock until you release it or the timeout expires.",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Acquire", listOf("Acquire", "Release"), mappable = false),
            ParamSpec("kind", "Keep", ParamType.SELECT, "Screen on", listOf("Screen on", "CPU only"), mappable = false),
            ParamSpec("minutes", "Auto-release after (minutes)", ParamType.NUMBER, "10", mappable = false),
        ),
        outputs = listOf("held", "action"),
    ),
    ModuleSpec(
        "device.lock", "Lock the screen", "Device controls", "🔒", C_DEVICE,
        description = "Uses the accessibility service, or device admin if you enable it.",
        outputs = listOf("locked", "via"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
    ModuleSpec(
        "device.wallpaper", "Set wallpaper", "Device controls", "🖼", C_DEVICE,
        summaryKey = "path",
        params = listOf(
            ParamSpec("path", "Image file", ParamType.TEXT, ""),
            ParamSpec("target", "Apply to", ParamType.SELECT, "Home", listOf("Home", "Lock", "Both"), mappable = false),
        ),
        outputs = listOf("applied", "target"),
    ),
    ModuleSpec(
        "clipboard.set", "Set clipboard", "Device controls", "📋", C_DEVICE,
        summaryKey = "text",
        params = listOf(ParamSpec("text", "Text", ParamType.MULTILINE, "")),
        outputs = listOf("text"),
    ),
    ModuleSpec(
        "clipboard.get", "Read clipboard", "Device controls", "📋", C_DEVICE,
        description = "Android 10+ only lets the focused app read the clipboard, so this can return empty in the background.",
        outputs = listOf("text"),
    ),
    ModuleSpec(
        "device.info", "Device state", "Device controls", "ℹ", C_DEVICE,
        description = "Battery, network, storage, screen and audio state in one bundle.",
        outputs = listOf(
            "battery", "charging", "network", "ssid", "signal", "wifiEnabled", "airplane", "screenOn",
            "locked", "ringerMode", "volumeMedia", "freeStorageMb", "totalStorageMb", "model",
            "androidVersion", "sdk", "time", "date", "timestamp",
        ),
    ),
    ModuleSpec(
        "device.foregroundApp", "Foreground app", "Device controls", "👀", C_DEVICE,
        description = "Which app is on screen right now. Needs usage access.",
        outputs = listOf("package", "appName", "since"),
        specialAccess = SpecialAccess.USAGE_STATS,
    ),
    ModuleSpec(
        "device.sensors", "Sensor snapshot", "Device controls", "🧭", C_DEVICE,
        description = "Reads one sample from the phone's sensors.",
        params = listOf(
            ParamSpec(
                "sensors", "Sensors", ParamType.SELECT, "All",
                listOf("All", "Light", "Proximity", "Accelerometer", "Pressure", "Humidity", "Temperature"),
                mappable = false,
            ),
            ParamSpec("timeout", "Give up after (ms)", ParamType.NUMBER, "2000", mappable = false),
        ),
        outputs = listOf("light", "proximity", "accelerometer", "pressure", "humidity", "temperature"),
    ),
)

internal val MEDIA_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "media.control", "Media control", "Audio & media", "⏯", C_MEDIA,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Play/Pause", listOf("Play/Pause", "Play", "Pause", "Next", "Previous", "Stop"), mappable = false)
        ),
        outputs = listOf("action"),
    ),
    ModuleSpec(
        "media.nowPlaying", "Now playing", "Audio & media", "🎵", C_MEDIA,
        description = "Reads the active media session's track metadata. Needs notification access.",
        outputs = listOf("title", "artist", "album", "app", "playing", "durationMs", "positionMs"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "media.play", "Play a sound", "Audio & media", "🔈", C_MEDIA,
        summaryKey = "source",
        params = listOf(
            ParamSpec("source", "Play", ParamType.SELECT, "File", listOf("File", "URL", "Notification tone", "Alarm tone", "Ringtone", "Beep"), mappable = false),
            ParamSpec("path", "File or URL", ParamType.TEXT, ""),
            ParamSpec("stream", "Output stream", ParamType.SELECT, "Media", listOf("Media", "Notification", "Alarm", "Ring"), mappable = false),
            ParamSpec("wait", "Wait until it finishes", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("played", "durationMs"),
    ),
    ModuleSpec(
        "media.record", "Record audio", "Audio & media", "🎙", C_MEDIA,
        summaryKey = "seconds",
        params = listOf(
            ParamSpec("seconds", "Length (seconds)", ParamType.NUMBER, "10"),
            ParamSpec("filename", "Save as", ParamType.TEXT, "recordings/clip.m4a"),
        ),
        outputs = listOf("path", "bytes", "seconds"),
        permissions = listOf("android.permission.RECORD_AUDIO"),
    ),
)
