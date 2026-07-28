package com.flowforge.android.model

enum class ParamType { TEXT, MULTILINE, NUMBER, BOOL, SELECT, APP, TIME }

data class ParamSpec(
    val key: String,
    val label: String,
    val type: ParamType = ParamType.TEXT,
    val default: String = "",
    val options: List<String> = emptyList(),
    val hint: String = "",
    /** Mapping tokens like {{2.body}} can be typed into this field. */
    val mappable: Boolean = true,
)

data class ModuleSpec(
    val type: String,
    val name: String,
    val group: String,
    val icon: String,
    val color: Long,
    val isTrigger: Boolean = false,
    val summaryKey: String? = null,
    val params: List<ParamSpec> = emptyList(),
    /** Field names this module puts on the bundle, offered in the mapping picker. */
    val outputs: List<String> = emptyList(),
    val description: String = "",
    /** Runtime permission strings this module needs. */
    val permissions: List<String> = emptyList(),
    val specialAccess: SpecialAccess? = null,
)

enum class SpecialAccess { NOTIFICATION_LISTENER, WRITE_SETTINGS, DND_POLICY, BATTERY_OPTIMISATION }

private const val C_TRIGGER = 0xFF7C4DFFL
private const val C_HTTP = 0xFF2E7DF6L
private const val C_DEVICE = 0xFF00A870L
private const val C_MSG = 0xFFF2A13BL
private const val C_FLOW = 0xFFE0457BL
private const val C_TOOL = 0xFF6E7B8BL

object ModuleCatalog {

    val ALL: List<ModuleSpec> = buildList {
        // ---------------------------------------------------------------- triggers
        add(
            ModuleSpec(
                "trigger.manual", "Run manually", "Triggers", "▶", C_TRIGGER, isTrigger = true,
                description = "Fires when you tap Run, use the quick-settings tile, or open flowforge://run/<id>.",
                outputs = listOf("source", "timestamp"),
            )
        )
        add(
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
            )
        )
        add(
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
            )
        )
        add(
            ModuleSpec(
                "trigger.sms", "SMS received", "Triggers", "✉", C_TRIGGER, isTrigger = true,
                summaryKey = "from",
                params = listOf(
                    ParamSpec("from", "Only from (blank = any)", ParamType.TEXT, "", hint = "+61400000000"),
                    ParamSpec("contains", "Body contains", ParamType.TEXT, ""),
                ),
                outputs = listOf("from", "text", "timestamp"),
                permissions = listOf("android.permission.RECEIVE_SMS"),
            )
        )
        add(
            ModuleSpec(
                "trigger.notification", "Notification posted", "Triggers", "🔔", C_TRIGGER, isTrigger = true,
                summaryKey = "package",
                params = listOf(
                    ParamSpec("package", "From app (blank = any)", ParamType.APP, ""),
                    ParamSpec("contains", "Title or text contains", ParamType.TEXT, ""),
                    ParamSpec("ignoreOngoing", "Ignore ongoing notifications", ParamType.BOOL, "true", mappable = false),
                ),
                outputs = listOf("package", "appName", "title", "text", "subText", "postedAt"),
                specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
            )
        )
        add(
            ModuleSpec(
                "trigger.call", "Phone call state", "Triggers", "📞", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "State", ParamType.SELECT, "Ringing", listOf("Ringing", "Answered", "Ended"), mappable = false)
                ),
                outputs = listOf("state", "number"),
                permissions = listOf("android.permission.READ_PHONE_STATE"),
            )
        )
        add(
            ModuleSpec(
                "trigger.power", "Power connected", "Triggers", "🔌", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false)
                ),
                outputs = listOf("state", "level", "plug"),
            )
        )
        add(
            ModuleSpec(
                "trigger.battery", "Battery level", "Triggers", "🔋", C_TRIGGER, isTrigger = true,
                summaryKey = "level",
                params = listOf(
                    ParamSpec("compare", "When level is", ParamType.SELECT, "Below", listOf("Below", "Above"), mappable = false),
                    ParamSpec("level", "Percent", ParamType.NUMBER, "20", mappable = false),
                ),
                outputs = listOf("level", "charging", "temperature"),
            )
        )
        add(
            ModuleSpec(
                "trigger.wifi", "Wi-Fi state", "Triggers", "📶", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false),
                    ParamSpec("ssid", "Only network named (blank = any)", ParamType.TEXT, ""),
                ),
                outputs = listOf("state", "ssid"),
                permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
            )
        )
        add(
            ModuleSpec(
                "trigger.bluetooth", "Bluetooth device", "Triggers", "🎧", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Connected", listOf("Connected", "Disconnected"), mappable = false),
                    ParamSpec("device", "Device name contains (blank = any)", ParamType.TEXT, ""),
                ),
                outputs = listOf("state", "device", "address"),
                permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
            )
        )
        add(
            ModuleSpec(
                "trigger.screen", "Screen / unlock", "Triggers", "📱", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Screen on", listOf("Screen on", "Screen off", "Unlocked"), mappable = false)
                ),
                outputs = listOf("state", "timestamp"),
            )
        )
        add(
            ModuleSpec(
                "trigger.headset", "Headset plugged", "Triggers", "🎧", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Plugged in", listOf("Plugged in", "Unplugged"), mappable = false)
                ),
                outputs = listOf("state", "hasMic"),
            )
        )
        add(
            ModuleSpec(
                "trigger.airplane", "Airplane mode", "Triggers", "✈", C_TRIGGER, isTrigger = true,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "When", ParamType.SELECT, "Turned on", listOf("Turned on", "Turned off"), mappable = false)
                ),
                outputs = listOf("state"),
            )
        )
        add(
            ModuleSpec(
                "trigger.shake", "Shake device", "Triggers", "🤚", C_TRIGGER, isTrigger = true,
                params = listOf(
                    ParamSpec("sensitivity", "Sensitivity (10 = gentle, 25 = hard)", ParamType.NUMBER, "16", mappable = false)
                ),
                outputs = listOf("force", "timestamp"),
            )
        )
        add(
            ModuleSpec(
                "trigger.boot", "Device booted", "Triggers", "🚀", C_TRIGGER, isTrigger = true,
                outputs = listOf("timestamp"),
            )
        )

        // ---------------------------------------------------------------- http
        add(
            ModuleSpec(
                "http.request", "HTTP request", "HTTP", "🌐", C_HTTP,
                summaryKey = "url",
                params = listOf(
                    ParamSpec("method", "Method", ParamType.SELECT, "GET", listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"), mappable = false),
                    ParamSpec("url", "URL", ParamType.TEXT, "", hint = "https://api.example.com/v1/things"),
                    ParamSpec("headers", "Headers (one per line, Name: value)", ParamType.MULTILINE, ""),
                    ParamSpec("contentType", "Body type", ParamType.SELECT, "application/json", listOf("application/json", "application/x-www-form-urlencoded", "text/plain", "none"), mappable = false),
                    ParamSpec("body", "Body", ParamType.MULTILINE, ""),
                    ParamSpec("bearer", "Bearer token (optional)", ParamType.TEXT, ""),
                    ParamSpec("basicUser", "Basic auth user (optional)", ParamType.TEXT, ""),
                    ParamSpec("basicPass", "Basic auth password", ParamType.TEXT, ""),
                    ParamSpec("timeout", "Timeout (seconds)", ParamType.NUMBER, "30", mappable = false),
                    ParamSpec("parseJson", "Parse response as JSON", ParamType.BOOL, "true", mappable = false),
                ),
                outputs = listOf("status", "body", "json", "headers", "ok"),
            )
        )
        add(
            ModuleSpec(
                "http.download", "Download file", "HTTP", "⬇", C_HTTP,
                summaryKey = "url",
                params = listOf(
                    ParamSpec("url", "URL", ParamType.TEXT, ""),
                    ParamSpec("filename", "Save as", ParamType.TEXT, "download.bin"),
                ),
                outputs = listOf("path", "bytes", "uri"),
            )
        )
        add(
            ModuleSpec(
                "webhook.respond", "Webhook response", "HTTP", "↩", C_HTTP,
                description = "Sends a custom reply to the caller of a webhook trigger.",
                params = listOf(
                    ParamSpec("status", "Status code", ParamType.NUMBER, "200", mappable = false),
                    ParamSpec("contentType", "Content type", ParamType.TEXT, "application/json", mappable = false),
                    ParamSpec("body", "Body", ParamType.MULTILINE, "{\"ok\":true}"),
                ),
                outputs = listOf("sent"),
            )
        )

        // ---------------------------------------------------------------- messaging / notify
        add(
            ModuleSpec(
                "notify.send", "Send notification", "Notify", "🔔", C_MSG,
                summaryKey = "title",
                params = listOf(
                    ParamSpec("title", "Title", ParamType.TEXT, "FlowForge"),
                    ParamSpec("text", "Text", ParamType.MULTILINE, ""),
                    ParamSpec("channel", "Importance", ParamType.SELECT, "Default", listOf("Default", "High (heads up)", "Silent"), mappable = false),
                    ParamSpec("ongoing", "Ongoing (not swipeable)", ParamType.BOOL, "false", mappable = false),
                    ParamSpec("tag", "Replace notification with tag", ParamType.TEXT, ""),
                ),
                outputs = listOf("id"),
                permissions = listOf("android.permission.POST_NOTIFICATIONS"),
            )
        )
        add(
            ModuleSpec(
                "sms.send", "Send SMS", "Notify", "✉", C_MSG,
                summaryKey = "to",
                params = listOf(
                    ParamSpec("to", "To number", ParamType.TEXT, ""),
                    ParamSpec("message", "Message", ParamType.MULTILINE, ""),
                ),
                outputs = listOf("to", "parts"),
                permissions = listOf("android.permission.SEND_SMS"),
            )
        )
        add(
            ModuleSpec(
                "device.tts", "Speak text", "Notify", "🗣", C_MSG,
                summaryKey = "text",
                params = listOf(
                    ParamSpec("text", "Text", ParamType.MULTILINE, ""),
                    ParamSpec("rate", "Speed", ParamType.NUMBER, "1.0", mappable = false),
                    ParamSpec("queue", "Queue behind current speech", ParamType.BOOL, "false", mappable = false),
                ),
                outputs = listOf("spoken"),
            )
        )
        add(
            ModuleSpec(
                "device.toast", "Show toast", "Notify", "💬", C_MSG,
                summaryKey = "text",
                params = listOf(
                    ParamSpec("text", "Text", ParamType.TEXT, ""),
                    ParamSpec("long", "Long duration", ParamType.BOOL, "false", mappable = false),
                ),
                outputs = listOf("shown"),
            )
        )

        // ---------------------------------------------------------------- device
        add(
            ModuleSpec(
                "app.open", "Open app", "Device", "📲", C_DEVICE,
                summaryKey = "package",
                params = listOf(ParamSpec("package", "App", ParamType.APP, "")),
                outputs = listOf("package", "launched"),
            )
        )
        add(
            ModuleSpec(
                "intent.send", "Send intent", "Device", "⚡", C_DEVICE,
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
            )
        )
        add(
            ModuleSpec(
                "device.url", "Open URL", "Device", "🔗", C_DEVICE,
                summaryKey = "url",
                params = listOf(ParamSpec("url", "URL", ParamType.TEXT, "")),
                outputs = listOf("opened"),
            )
        )
        add(
            ModuleSpec(
                "clipboard.set", "Set clipboard", "Device", "📋", C_DEVICE,
                summaryKey = "text",
                params = listOf(ParamSpec("text", "Text", ParamType.MULTILINE, "")),
                outputs = listOf("text"),
            )
        )
        add(
            ModuleSpec(
                "clipboard.get", "Read clipboard", "Device", "📋", C_DEVICE,
                outputs = listOf("text"),
            )
        )
        add(
            ModuleSpec(
                "device.vibrate", "Vibrate", "Device", "📳", C_DEVICE,
                params = listOf(
                    ParamSpec("pattern", "Pattern (ms, comma separated)", ParamType.TEXT, "0,200,100,200", mappable = false)
                ),
                outputs = listOf("vibrated"),
            )
        )
        add(
            ModuleSpec(
                "device.volume", "Set volume", "Device", "🔊", C_DEVICE,
                summaryKey = "stream",
                params = listOf(
                    ParamSpec("stream", "Stream", ParamType.SELECT, "Media", listOf("Media", "Ring", "Notification", "Alarm", "Call"), mappable = false),
                    ParamSpec("percent", "Level (0-100)", ParamType.NUMBER, "50"),
                ),
                outputs = listOf("stream", "level"),
            )
        )
        add(
            ModuleSpec(
                "device.ringer", "Set ringer mode", "Device", "🔕", C_DEVICE,
                summaryKey = "mode",
                params = listOf(
                    ParamSpec("mode", "Mode", ParamType.SELECT, "Silent", listOf("Normal", "Vibrate", "Silent"), mappable = false)
                ),
                outputs = listOf("mode"),
                specialAccess = SpecialAccess.DND_POLICY,
            )
        )
        add(
            ModuleSpec(
                "device.dnd", "Do not disturb", "Device", "🌙", C_DEVICE,
                summaryKey = "mode",
                params = listOf(
                    ParamSpec("mode", "Mode", ParamType.SELECT, "On", listOf("On", "Off", "Priority only", "Alarms only"), mappable = false)
                ),
                outputs = listOf("mode"),
                specialAccess = SpecialAccess.DND_POLICY,
            )
        )
        add(
            ModuleSpec(
                "device.brightness", "Set brightness", "Device", "☀", C_DEVICE,
                params = listOf(
                    ParamSpec("percent", "Brightness (0-100)", ParamType.NUMBER, "60"),
                    ParamSpec("auto", "Turn off auto-brightness first", ParamType.BOOL, "true", mappable = false),
                ),
                outputs = listOf("percent"),
                specialAccess = SpecialAccess.WRITE_SETTINGS,
            )
        )
        add(
            ModuleSpec(
                "device.torch", "Torch", "Device", "🔦", C_DEVICE,
                summaryKey = "state",
                params = listOf(
                    ParamSpec("state", "State", ParamType.SELECT, "On", listOf("On", "Off", "Toggle"), mappable = false)
                ),
                outputs = listOf("state"),
            )
        )
        add(
            ModuleSpec(
                "device.media", "Media control", "Device", "⏯", C_DEVICE,
                summaryKey = "action",
                params = listOf(
                    ParamSpec("action", "Action", ParamType.SELECT, "Play/Pause", listOf("Play/Pause", "Play", "Pause", "Next", "Previous", "Stop"), mappable = false)
                ),
                outputs = listOf("action"),
            )
        )
        add(
            ModuleSpec(
                "device.location", "Get location", "Device", "📍", C_DEVICE,
                params = listOf(
                    ParamSpec("accuracy", "Accuracy", ParamType.SELECT, "Coarse", listOf("Coarse", "Fine"), mappable = false),
                    ParamSpec("maxAgeMinutes", "Accept cached fix up to (min)", ParamType.NUMBER, "10", mappable = false),
                ),
                outputs = listOf("latitude", "longitude", "accuracy", "provider", "age"),
                permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
            )
        )
        add(
            ModuleSpec(
                "device.info", "Device state", "Device", "ℹ", C_DEVICE,
                description = "Battery, network, storage, screen and audio state in one bundle.",
                outputs = listOf(
                    "battery", "charging", "network", "ssid", "wifiEnabled", "airplane", "screenOn",
                    "ringerMode", "volumeMedia", "freeStorageMb", "model", "androidVersion", "time", "date",
                ),
            )
        )
        add(
            ModuleSpec(
                "device.settingsPanel", "Open settings panel", "Device", "⚙", C_DEVICE,
                summaryKey = "panel",
                description = "Android 10+ blocks silent Wi-Fi/Bluetooth toggles; this opens the one-tap panel instead.",
                params = listOf(
                    ParamSpec("panel", "Panel", ParamType.SELECT, "Wi-Fi", listOf("Wi-Fi", "Internet", "Bluetooth", "NFC", "Volume", "Airplane mode", "App details"), mappable = false)
                ),
                outputs = listOf("panel"),
            )
        )

        // ---------------------------------------------------------------- files
        add(
            ModuleSpec(
                "file.write", "Write file", "Files", "💾", C_TOOL,
                summaryKey = "path",
                params = listOf(
                    ParamSpec("path", "File name or absolute path", ParamType.TEXT, "flowforge/out.txt"),
                    ParamSpec("content", "Content", ParamType.MULTILINE, ""),
                    ParamSpec("mode", "Mode", ParamType.SELECT, "Overwrite", listOf("Overwrite", "Append"), mappable = false),
                ),
                outputs = listOf("path", "bytes"),
            )
        )
        add(
            ModuleSpec(
                "file.read", "Read file", "Files", "📄", C_TOOL,
                summaryKey = "path",
                params = listOf(
                    ParamSpec("path", "File name or absolute path", ParamType.TEXT, ""),
                    ParamSpec("parseJson", "Parse as JSON", ParamType.BOOL, "false", mappable = false),
                ),
                outputs = listOf("content", "json", "bytes", "exists"),
            )
        )

        // ---------------------------------------------------------------- flow control
        add(
            ModuleSpec(
                "flow.filter", "Filter", "Flow control", "✂", C_FLOW,
                description = "Stops the run unless the condition passes.",
                summaryKey = "left",
                params = listOf(
                    ParamSpec("left", "Value", ParamType.TEXT, ""),
                    ParamSpec("op", "Condition", ParamType.SELECT, "equals", FilterRule.OPERATORS, mappable = false),
                    ParamSpec("right", "Compare to", ParamType.TEXT, ""),
                ),
                outputs = listOf("passed"),
            )
        )
        add(
            ModuleSpec(
                "flow.router", "Router", "Flow control", "⑂", C_FLOW,
                description = "Splits the flow into branches. Each branch runs when its own filter passes.",
                outputs = listOf("routes"),
            )
        )
        add(
            ModuleSpec(
                "flow.iterator", "Iterator", "Flow control", "🔁", C_FLOW,
                description = "Splits an array into bundles — everything after it runs once per item.",
                summaryKey = "array",
                params = listOf(ParamSpec("array", "Array", ParamType.TEXT, "", hint = "{{2.json.items}}")),
                outputs = listOf("value", "index", "total"),
            )
        )
        add(
            ModuleSpec(
                "flow.repeater", "Repeater", "Flow control", "🔂", C_FLOW,
                summaryKey = "count",
                params = listOf(
                    ParamSpec("count", "Repeats", ParamType.NUMBER, "3"),
                    ParamSpec("gapMs", "Gap between repeats (ms)", ParamType.NUMBER, "0", mappable = false),
                ),
                outputs = listOf("index", "total"),
            )
        )
        add(
            ModuleSpec(
                "flow.sleep", "Sleep", "Flow control", "⏸", C_FLOW,
                summaryKey = "ms",
                params = listOf(ParamSpec("ms", "Milliseconds", ParamType.NUMBER, "1000")),
                outputs = listOf("slept"),
            )
        )
        add(
            ModuleSpec(
                "flow.stop", "Stop", "Flow control", "⏹", C_FLOW,
                params = listOf(
                    ParamSpec("status", "Finish as", ParamType.SELECT, "Success", listOf("Success", "Error"), mappable = false),
                    ParamSpec("message", "Message", ParamType.TEXT, ""),
                ),
                outputs = listOf("message"),
            )
        )
        add(
            ModuleSpec(
                "flow.aggregate", "Aggregate to text", "Flow control", "📦", C_FLOW,
                description = "Collects a value from every iteration into one joined string.",
                params = listOf(
                    ParamSpec("value", "Value per bundle", ParamType.TEXT, ""),
                    ParamSpec("separator", "Separator", ParamType.TEXT, ", ", mappable = false),
                ),
                outputs = listOf("text", "count"),
            )
        )

        // ---------------------------------------------------------------- tools
        add(
            ModuleSpec(
                "tool.setVariable", "Set variable", "Tools", "🏷", C_TOOL,
                summaryKey = "name",
                params = listOf(
                    ParamSpec("name", "Name", ParamType.TEXT, "myVar", mappable = false),
                    ParamSpec("value", "Value", ParamType.MULTILINE, ""),
                ),
                outputs = listOf("name", "value"),
            )
        )
        add(
            ModuleSpec(
                "tool.transform", "Compose / transform", "Tools", "✨", C_TOOL,
                description = "Builds a value out of mappings and functions, e.g. upper(trim({{1.text}})).",
                summaryKey = "value",
                params = listOf(ParamSpec("value", "Expression", ParamType.MULTILINE, "")),
                outputs = listOf("value"),
            )
        )
        add(
            ModuleSpec(
                "tool.json", "Parse JSON", "Tools", "{ }", C_TOOL,
                summaryKey = "text",
                params = listOf(ParamSpec("text", "JSON text", ParamType.MULTILINE, "")),
                outputs = listOf("json", "valid"),
            )
        )
        add(
            ModuleSpec(
                "tool.regex", "Match pattern", "Tools", "🔍", C_TOOL,
                summaryKey = "pattern",
                params = listOf(
                    ParamSpec("text", "Text", ParamType.MULTILINE, ""),
                    ParamSpec("pattern", "Regex", ParamType.TEXT, "", hint = "(\\d+)"),
                    ParamSpec("all", "Find all matches", ParamType.BOOL, "false", mappable = false),
                ),
                outputs = listOf("matched", "match", "groups", "matches", "count"),
            )
        )
        add(
            ModuleSpec(
                "tool.datastore", "Data store", "Tools", "🗃", C_TOOL,
                description = "Key/value storage that survives between runs and scenarios.",
                summaryKey = "action",
                params = listOf(
                    ParamSpec("action", "Action", ParamType.SELECT, "Get", listOf("Get", "Set", "Add number", "Delete", "List keys"), mappable = false),
                    ParamSpec("key", "Key", ParamType.TEXT, ""),
                    ParamSpec("value", "Value", ParamType.MULTILINE, ""),
                ),
                outputs = listOf("key", "value", "existed", "keys"),
            )
        )
        add(
            ModuleSpec(
                "tool.log", "Log message", "Tools", "📝", C_TOOL,
                summaryKey = "message",
                params = listOf(ParamSpec("message", "Message", ParamType.MULTILINE, "")),
                outputs = listOf("message"),
            )
        )
        add(
            ModuleSpec(
                "scenario.run", "Run another scenario", "Tools", "▶", C_TOOL,
                summaryKey = "scenario",
                params = listOf(
                    ParamSpec("scenario", "Scenario name or id", ParamType.TEXT, "", mappable = false),
                    ParamSpec("payload", "Payload (JSON, arrives as trigger bundle)", ParamType.MULTILINE, ""),
                    ParamSpec("wait", "Wait for it to finish", ParamType.BOOL, "true", mappable = false),
                ),
                outputs = listOf("status", "output"),
            )
        )
    }

    private val byType = ALL.associateBy { it.type }

    fun spec(type: String): ModuleSpec? = byType[type]

    fun specOrUnknown(type: String): ModuleSpec =
        byType[type] ?: ModuleSpec(type, type, "Unknown", "❓", C_TOOL)

    val triggers: List<ModuleSpec> get() = ALL.filter { it.isTrigger }
    val actions: List<ModuleSpec> get() = ALL.filterNot { it.isTrigger }

    fun defaultParams(spec: ModuleSpec): Map<String, String> =
        spec.params.filter { it.default.isNotEmpty() }.associate { it.key to it.default }
}
