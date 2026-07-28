package com.flowforge.android.model

internal val TRIGGER_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "trigger.manual", "Run manually", "Triggers", "▶", C_TRIGGER, isTrigger = true,
        description = "Fires when you tap Run, use the quick-settings tile, or open flowforge://run/<id>.",
        outputs = listOf("source", "timestamp"),
    ),
    ModuleSpec(
        "trigger.schedule", "Schedule", "Triggers", "⏰", C_TRIGGER, isTrigger = true,
        summaryKey = "mode",
        params = listOf(
            ParamSpec("mode", "Mode", ParamType.SELECT, "Every N minutes", listOf("Every N minutes", "Daily at time"), mappable = false),
            ParamSpec("minutes", "Interval (minutes)", ParamType.NUMBER, "15", mappable = false),
            ParamSpec("time", "Time of day", ParamType.TIME, "09:00", mappable = false),
            ParamSpec("days", "Days (blank = every day)", ParamType.TEXT, "", hint = "Mon,Tue,Wed", mappable = false),
        ),
        outputs = listOf("timestamp", "time", "date"),
    ),
    ModuleSpec(
        "trigger.webhook", "Webhook", "Triggers", "☁", C_TRIGGER, isTrigger = true,
        summaryKey = "path",
        description = "Starts a local HTTP listener. Anything on your network can POST to it.",
        params = listOf(
            ParamSpec("path", "Path", ParamType.TEXT, "hook", hint = "http://<phone-ip>:8420/hook", mappable = false),
            ParamSpec("method", "Method", ParamType.SELECT, "ANY", listOf("ANY", "GET", "POST"), mappable = false),
            ParamSpec("secret", "Shared secret (optional)", ParamType.TEXT, "", hint = "sent as ?key= or X-Key header", mappable = false),
        ),
        outputs = listOf("body", "json", "query", "headers", "method", "ip"),
    ),
    ModuleSpec(
        "trigger.sms", "SMS received", "Triggers", "✉", C_TRIGGER, isTrigger = true,
        summaryKey = "from",
        params = listOf(
            ParamSpec("from", "Only from (blank = any)", ParamType.TEXT, "", hint = "+61400000000"),
            ParamSpec("contains", "Body contains", ParamType.TEXT, ""),
        ),
        outputs = listOf("from", "text", "timestamp"),
        permissions = listOf("android.permission.RECEIVE_SMS"),
    ),
    ModuleSpec(
        "trigger.notification", "Notification posted", "Triggers", "🔔", C_TRIGGER, isTrigger = true,
        summaryKey = "package",
        params = listOf(
            ParamSpec("package", "From app (blank = any)", ParamType.APP, ""),
            ParamSpec("contains", "Title or text contains", ParamType.TEXT, ""),
            ParamSpec("ignoreOngoing", "Ignore ongoing notifications", ParamType.BOOL, "true", mappable = false),
        ),
        outputs = listOf("package", "appName", "title", "text", "subText", "postedAt", "key", "canReply"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "trigger.notificationRemoved", "Notification dismissed", "Triggers", "🔕", C_TRIGGER, isTrigger = true,
        summaryKey = "package",
        params = listOf(
            ParamSpec("package", "From app (blank = any)", ParamType.APP, ""),
            ParamSpec("contains", "Title or text contains", ParamType.TEXT, ""),
        ),
        outputs = listOf("package", "appName", "title", "text", "key"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "trigger.call", "Phone call state", "Triggers", "📞", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "State", ParamType.SELECT, "Ringing", listOf("Ringing", "Answered", "Ended"), mappable = false)
        ),
        outputs = listOf("state", "number"),
        permissions = listOf("android.permission.READ_PHONE_STATE"),
    ),
    ModuleSpec(
        "trigger.power", "Power connected", "Triggers", "🔌", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false)
        ),
        outputs = listOf("state", "level", "plug"),
    ),
    ModuleSpec(
        "trigger.battery", "Battery level", "Triggers", "🔋", C_TRIGGER, isTrigger = true,
        summaryKey = "level",
        params = listOf(
            ParamSpec("compare", "When level is", ParamType.SELECT, "Below", listOf("Below", "Above"), mappable = false),
            ParamSpec("level", "Percent", ParamType.NUMBER, "20", mappable = false),
        ),
        outputs = listOf("level", "charging", "temperature"),
    ),
    ModuleSpec(
        "trigger.wifi", "Wi-Fi state", "Triggers", "📶", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false),
            ParamSpec("ssid", "Only network named (blank = any)", ParamType.TEXT, ""),
        ),
        outputs = listOf("state", "ssid"),
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
    ),
    ModuleSpec(
        "trigger.bluetooth", "Bluetooth device", "Triggers", "🎧", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false),
            ParamSpec("device", "Device name contains (blank = any)", ParamType.TEXT, ""),
        ),
        outputs = listOf("state", "device", "address"),
        permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
    ),
    ModuleSpec(
        "trigger.screen", "Screen / unlock", "Triggers", "📱", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Screen on", listOf("Screen on", "Screen off", "Unlocked"), mappable = false)
        ),
        outputs = listOf("state", "timestamp"),
    ),
    ModuleSpec(
        "trigger.headset", "Headset plugged", "Triggers", "🎧", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Plugged in", listOf("Plugged in", "Unplugged"), mappable = false)
        ),
        outputs = listOf("state", "hasMic"),
    ),
    ModuleSpec(
        "trigger.airplane", "Airplane mode", "Triggers", "✈", C_TRIGGER, isTrigger = true,
        summaryKey = "state",
        params = listOf(
            ParamSpec("state", "When", ParamType.SELECT, "Turned on", listOf("Turned on", "Turned off"), mappable = false)
        ),
        outputs = listOf("state"),
    ),
    ModuleSpec(
        "trigger.shake", "Shake device", "Triggers", "🤚", C_TRIGGER, isTrigger = true,
        params = listOf(
            ParamSpec("sensitivity", "Sensitivity (10 = gentle, 25 = hard)", ParamType.NUMBER, "16", mappable = false)
        ),
        outputs = listOf("force", "timestamp"),
    ),
    ModuleSpec(
        "trigger.folder", "Folder changed", "Triggers", "📂", C_TRIGGER, isTrigger = true,
        summaryKey = "path",
        description = "Watches a folder and fires when a file is created, changed or deleted.",
        params = listOf(
            ParamSpec("path", "Folder", ParamType.TEXT, "flowforge", hint = "relative to app storage, or absolute", mappable = false),
            ParamSpec("events", "Watch for", ParamType.SELECT, "Any change", listOf("Any change", "Created", "Modified", "Deleted"), mappable = false),
        ),
        outputs = listOf("event", "file", "path", "timestamp"),
    ),
    ModuleSpec(
        "trigger.foregroundApp", "App opened", "Triggers", "📲", C_TRIGGER, isTrigger = true,
        summaryKey = "package",
        description = "Polls the foreground app and fires when it changes. Needs usage access.",
        params = listOf(
            ParamSpec("package", "Only this app (blank = any change)", ParamType.APP, ""),
        ),
        outputs = listOf("package", "appName", "previous"),
        specialAccess = SpecialAccess.USAGE_STATS,
    ),
    ModuleSpec(
        "trigger.nfc", "NFC tag scanned", "Triggers", "📇", C_TRIGGER, isTrigger = true,
        summaryKey = "contains",
        description = "Fires when a tag is tapped while FlowForge is open, or via an NDEF launch.",
        params = listOf(ParamSpec("contains", "Payload contains (blank = any)", ParamType.TEXT, "")),
        outputs = listOf("id", "payload", "techs"),
    ),
    ModuleSpec(
        "trigger.boot", "Device booted", "Triggers", "🚀", C_TRIGGER, isTrigger = true,
        outputs = listOf("timestamp"),
    ),
)
