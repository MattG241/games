package com.flowforge.android.model

internal val FILE_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "file.write", "Write file", "Files", "💾", C_TOOL,
        summaryKey = "path",
        params = listOf(
            ParamSpec("path", "File name or absolute path", ParamType.TEXT, "flowforge/out.txt"),
            ParamSpec("content", "Content", ParamType.MULTILINE, ""),
            ParamSpec("mode", "Mode", ParamType.SELECT, "Overwrite", listOf("Overwrite", "Append"), mappable = false),
        ),
        outputs = listOf("path", "bytes"),
    ),
    ModuleSpec(
        "file.read", "Read file", "Files", "📄", C_TOOL,
        summaryKey = "path",
        params = listOf(
            ParamSpec("path", "File name or absolute path", ParamType.TEXT, ""),
            ParamSpec("parseJson", "Parse as JSON", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("content", "json", "bytes", "exists"),
    ),
    ModuleSpec(
        "file.manage", "Copy, move or delete", "Files", "🗂", C_TOOL,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Copy", listOf("Copy", "Move", "Delete", "Create folder"), mappable = false),
            ParamSpec("path", "Source", ParamType.TEXT, ""),
            ParamSpec("target", "Destination", ParamType.TEXT, ""),
            ParamSpec("overwrite", "Overwrite if it exists", ParamType.BOOL, "true", mappable = false),
        ),
        outputs = listOf("done", "path", "bytes"),
    ),
    ModuleSpec(
        "file.list", "List a folder", "Files", "📁", C_TOOL,
        summaryKey = "path",
        params = listOf(
            ParamSpec("path", "Folder", ParamType.TEXT, ""),
            ParamSpec("filter", "Name filter (glob)", ParamType.TEXT, "", hint = "*.jpg"),
            ParamSpec("recursive", "Include subfolders", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("files", "names", "count"),
    ),
    ModuleSpec(
        "file.zip", "Zip / unzip", "Files", "🗜", C_TOOL,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Zip", listOf("Zip", "Unzip"), mappable = false),
            ParamSpec("path", "Source file or folder", ParamType.TEXT, ""),
            ParamSpec("target", "Destination", ParamType.TEXT, ""),
        ),
        outputs = listOf("done", "path", "entries", "bytes"),
    ),
    ModuleSpec(
        "tool.sqlite", "SQLite query", "Files", "🗄", C_TOOL,
        summaryKey = "sql",
        description = "Runs SQL against a local database file, created on first use.",
        params = listOf(
            ParamSpec("database", "Database file", ParamType.TEXT, "flowforge/data.db"),
            ParamSpec("sql", "SQL", ParamType.MULTILINE, "", hint = "SELECT * FROM readings WHERE value > ?"),
            ParamSpec("args", "Arguments (one per line)", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("rows", "count", "changed", "columns"),
    ),
)

internal val VISION_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "camera.photo", "Take a photo", "Camera & screen", "📷", C_VISION,
        summaryKey = "lens",
        description = "Captures silently with CameraX — no camera app, no shutter UI.",
        params = listOf(
            ParamSpec("lens", "Camera", ParamType.SELECT, "Back", listOf("Back", "Front"), mappable = false),
            ParamSpec("filename", "Save as", ParamType.TEXT, "photos/shot.jpg"),
        ),
        outputs = listOf("path", "bytes", "uri", "width", "height"),
        permissions = listOf("android.permission.CAMERA"),
    ),
    ModuleSpec(
        "screen.capture", "Take a screenshot", "Camera & screen", "🖥", C_VISION,
        description = "Uses MediaProjection. Android shows a one-time consent prompt the first time.",
        params = listOf(ParamSpec("filename", "Save as", ParamType.TEXT, "screenshots/screen.png")),
        outputs = listOf("path", "bytes", "uri", "width", "height"),
        specialAccess = SpecialAccess.SCREEN_CAPTURE,
    ),
    ModuleSpec(
        "vision.ocr", "Read text from an image", "Camera & screen", "🔎", C_VISION,
        summaryKey = "path",
        description = "On-device OCR with ML Kit — nothing leaves the phone.",
        params = listOf(ParamSpec("path", "Image file", ParamType.TEXT, "")),
        outputs = listOf("text", "blocks", "lines", "found"),
    ),
    ModuleSpec(
        "vision.barcode", "Scan a barcode or QR", "Camera & screen", "🏷", C_VISION,
        summaryKey = "source",
        params = listOf(
            ParamSpec("source", "Read from", ParamType.SELECT, "Image file", listOf("Image file", "Camera"), mappable = false),
            ParamSpec("path", "Image file", ParamType.TEXT, ""),
            ParamSpec("lens", "Camera", ParamType.SELECT, "Back", listOf("Back", "Front"), mappable = false),
            ParamSpec("timeout", "Camera timeout (seconds)", ParamType.NUMBER, "15", mappable = false),
        ),
        outputs = listOf("found", "value", "format", "values"),
        permissions = listOf("android.permission.CAMERA"),
    ),
)

internal val LOCATION_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "device.location", "Get location", "Location", "📍", C_DEVICE,
        params = listOf(
            ParamSpec("accuracy", "Accuracy", ParamType.SELECT, "Coarse", listOf("Coarse", "Fine"), mappable = false),
            ParamSpec("maxAgeMinutes", "Accept cached fix up to (min)", ParamType.NUMBER, "10", mappable = false),
        ),
        outputs = listOf("latitude", "longitude", "accuracy", "provider", "age", "altitude", "speed"),
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
    ),
    ModuleSpec(
        "location.track", "Start or stop tracking", "Location", "🛰", C_DEVICE,
        summaryKey = "action",
        description = "Logs fixes to a file in the background until you stop it.",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Start", listOf("Start", "Stop"), mappable = false),
            ParamSpec("intervalSeconds", "Minimum gap between fixes", ParamType.NUMBER, "60", mappable = false),
            ParamSpec("metres", "Minimum distance moved (m)", ParamType.NUMBER, "25", mappable = false),
            ParamSpec("filename", "Log file", ParamType.TEXT, "flowforge/track.jsonl"),
        ),
        outputs = listOf("tracking", "path"),
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
    ),
    ModuleSpec(
        "location.navigate", "Navigate to a place", "Location", "🗺", C_DEVICE,
        summaryKey = "destination",
        params = listOf(
            ParamSpec("destination", "Address or lat,lng", ParamType.TEXT, ""),
            ParamSpec("mode", "Travel mode", ParamType.SELECT, "Drive", listOf("Drive", "Walk", "Cycle", "Transit"), mappable = false),
        ),
        outputs = listOf("opened", "destination"),
    ),
)

internal val CONNECTIVITY_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "wifi.connect", "Connect to a Wi-Fi network", "Connectivity", "📶", C_DEVICE,
        summaryKey = "ssid",
        description = "Uses the Android 10+ suggestion API — the system decides when to join, and may prompt once.",
        params = listOf(
            ParamSpec("ssid", "Network name", ParamType.TEXT, ""),
            ParamSpec("password", "Password (blank = open network)", ParamType.TEXT, ""),
        ),
        outputs = listOf("suggested", "ssid"),
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
    ),
    ModuleSpec(
        "bluetooth.toggle", "Toggle Bluetooth", "Connectivity", "🔵", C_DEVICE,
        summaryKey = "state",
        description = "Silent toggling only works up to Android 12; newer versions open the settings panel instead.",
        params = listOf(
            ParamSpec("state", "State", ParamType.SELECT, "On", listOf("On", "Off", "Toggle"), mappable = false)
        ),
        outputs = listOf("state", "changed", "via"),
        permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
    ),
    ModuleSpec(
        "bluetooth.device", "Connect a Bluetooth device", "Connectivity", "🎧", C_DEVICE,
        summaryKey = "device",
        description = "Connects or disconnects a paired audio device by name.",
        params = listOf(
            ParamSpec("device", "Paired device name", ParamType.TEXT, ""),
            ParamSpec("action", "Action", ParamType.SELECT, "Connect", listOf("Connect", "Disconnect"), mappable = false),
        ),
        outputs = listOf("done", "device", "address"),
        permissions = listOf("android.permission.BLUETOOTH_CONNECT"),
    ),
    ModuleSpec(
        "nfc.write", "Write an NFC tag", "Connectivity", "📇", C_DEVICE,
        summaryKey = "payload",
        description = "Arms writing, then you tap a tag against the phone.",
        params = listOf(
            ParamSpec("kind", "Record type", ParamType.SELECT, "Text", listOf("Text", "URL"), mappable = false),
            ParamSpec("payload", "Payload", ParamType.MULTILINE, ""),
            ParamSpec("timeout", "Wait for a tag (seconds)", ParamType.NUMBER, "30", mappable = false),
        ),
        outputs = listOf("written", "id", "bytes"),
    ),
)

internal val PEOPLE_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "contacts.lookup", "Look up a contact", "Contacts & calendar", "👤", C_TOOL,
        summaryKey = "query",
        params = listOf(
            ParamSpec("query", "Name or number", ParamType.TEXT, ""),
            ParamSpec("limit", "Maximum results", ParamType.NUMBER, "5", mappable = false),
        ),
        outputs = listOf("found", "name", "number", "email", "contacts", "count"),
        permissions = listOf("android.permission.READ_CONTACTS"),
    ),
    ModuleSpec(
        "contacts.save", "Create or update a contact", "Contacts & calendar", "➕", C_TOOL,
        summaryKey = "name",
        params = listOf(
            ParamSpec("name", "Display name", ParamType.TEXT, ""),
            ParamSpec("number", "Phone number", ParamType.TEXT, ""),
            ParamSpec("email", "Email", ParamType.TEXT, ""),
            ParamSpec("note", "Note", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("saved", "name"),
        permissions = listOf("android.permission.WRITE_CONTACTS"),
    ),
    ModuleSpec(
        "calendar.create", "Create a calendar event", "Contacts & calendar", "📅", C_TOOL,
        summaryKey = "title",
        params = listOf(
            ParamSpec("title", "Title", ParamType.TEXT, ""),
            ParamSpec("start", "Start (timestamp or yyyy-MM-dd HH:mm)", ParamType.TEXT, "{{now}}"),
            ParamSpec("minutes", "Length (minutes)", ParamType.NUMBER, "60"),
            ParamSpec("location", "Location", ParamType.TEXT, ""),
            ParamSpec("description", "Description", ParamType.MULTILINE, ""),
            ParamSpec("calendarId", "Calendar id (blank = default)", ParamType.TEXT, ""),
            ParamSpec("reminderMinutes", "Reminder (minutes before, blank = none)", ParamType.NUMBER, ""),
        ),
        outputs = listOf("created", "eventId", "start", "end"),
        permissions = listOf("android.permission.WRITE_CALENDAR"),
    ),
    ModuleSpec(
        "calendar.query", "Query calendar events", "Contacts & calendar", "🗓", C_TOOL,
        summaryKey = "hours",
        params = listOf(
            ParamSpec("hours", "Look ahead (hours)", ParamType.NUMBER, "24"),
            ParamSpec("contains", "Title contains", ParamType.TEXT, ""),
            ParamSpec("calendarId", "Calendar id (blank = all)", ParamType.TEXT, ""),
            ParamSpec("limit", "Maximum results", ParamType.NUMBER, "20", mappable = false),
        ),
        outputs = listOf("events", "count", "next", "nextStart"),
        permissions = listOf("android.permission.READ_CALENDAR"),
    ),
)

internal val UI_AUTOMATION_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "ui.tap", "Tap the screen", "UI automation", "👆", C_UI,
        summaryKey = "target",
        description = "Taps an element by its text or id, or raw coordinates. Needs the accessibility service.",
        params = listOf(
            ParamSpec("by", "Find by", ParamType.SELECT, "Text", listOf("Text", "View id", "Content description", "Coordinates"), mappable = false),
            ParamSpec("target", "Text, id or description", ParamType.TEXT, ""),
            ParamSpec("x", "X", ParamType.NUMBER, ""),
            ParamSpec("y", "Y", ParamType.NUMBER, ""),
            ParamSpec("longPress", "Long press", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("tapped", "target"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
    ModuleSpec(
        "ui.swipe", "Swipe or gesture", "UI automation", "👉", C_UI,
        summaryKey = "direction",
        params = listOf(
            ParamSpec("direction", "Direction", ParamType.SELECT, "Up", listOf("Up", "Down", "Left", "Right", "Custom"), mappable = false),
            ParamSpec("x1", "From X", ParamType.NUMBER, ""),
            ParamSpec("y1", "From Y", ParamType.NUMBER, ""),
            ParamSpec("x2", "To X", ParamType.NUMBER, ""),
            ParamSpec("y2", "To Y", ParamType.NUMBER, ""),
            ParamSpec("durationMs", "Duration (ms)", ParamType.NUMBER, "300", mappable = false),
        ),
        outputs = listOf("swiped"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
    ModuleSpec(
        "ui.type", "Type text", "UI automation", "⌨", C_UI,
        summaryKey = "text",
        description = "Sets the text of the focused field, or of a field found by its label.",
        params = listOf(
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("intoField", "Into the field labelled (blank = focused)", ParamType.TEXT, ""),
            ParamSpec("submit", "Press enter afterwards", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("typed", "text"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
    ModuleSpec(
        "ui.read", "Read the screen", "UI automation", "📖", C_UI,
        description = "Scrapes every visible text node from the app in the foreground.",
        params = listOf(
            ParamSpec("contains", "Only nodes containing", ParamType.TEXT, ""),
        ),
        outputs = listOf("text", "nodes", "count", "package"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
    ModuleSpec(
        "ui.global", "Press a system button", "UI automation", "⎋", C_UI,
        summaryKey = "action",
        params = listOf(
            ParamSpec(
                "action", "Button", ParamType.SELECT, "Back",
                listOf("Back", "Home", "Recents", "Notification shade", "Quick settings", "Power dialog", "Lock screen", "Split screen"),
                mappable = false,
            )
        ),
        outputs = listOf("performed", "action"),
        specialAccess = SpecialAccess.ACCESSIBILITY,
    ),
)

internal val PRIVILEGED_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "priv.shell", "Run a shell command", "Privileged", "⌘", C_POWER,
        summaryKey = "command",
        description = "Tries Shizuku, then root, then an unprivileged shell — and tells you which one ran it.",
        params = listOf(
            ParamSpec("command", "Command", ParamType.MULTILINE, "", hint = "settings put global airplane_mode_on 1"),
            ParamSpec("via", "Run through", ParamType.SELECT, "Best available", listOf("Best available", "Shizuku", "Root", "Unprivileged"), mappable = false),
            ParamSpec("timeout", "Timeout (seconds)", ParamType.NUMBER, "20", mappable = false),
        ),
        outputs = listOf("exitCode", "stdout", "stderr", "via", "ok"),
        specialAccess = SpecialAccess.PRIVILEGED,
    ),
    ModuleSpec(
        "priv.radio", "Toggle a radio", "Privileged", "📻", C_POWER,
        summaryKey = "radio",
        description = "Wi-Fi, mobile data and airplane mode — needs Shizuku or root, since normal apps cannot do this.",
        params = listOf(
            ParamSpec("radio", "Radio", ParamType.SELECT, "Wi-Fi", listOf("Wi-Fi", "Mobile data", "Airplane mode", "Bluetooth"), mappable = false),
            ParamSpec("state", "State", ParamType.SELECT, "On", listOf("On", "Off", "Toggle"), mappable = false),
        ),
        outputs = listOf("done", "radio", "state", "via"),
        specialAccess = SpecialAccess.PRIVILEGED,
    ),
    ModuleSpec(
        "priv.appControl", "Control another app", "Privileged", "🛑", C_POWER,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Force stop", listOf("Force stop", "Grant permission", "Revoke permission", "Clear data", "Disable", "Enable"), mappable = false),
            ParamSpec("package", "App", ParamType.APP, ""),
            ParamSpec("permission", "Permission", ParamType.TEXT, "", hint = "android.permission.CAMERA"),
        ),
        outputs = listOf("done", "output", "via"),
        specialAccess = SpecialAccess.PRIVILEGED,
    ),
    ModuleSpec(
        "priv.key", "Send a hardware key", "Privileged", "🔘", C_POWER,
        summaryKey = "key",
        params = listOf(
            ParamSpec(
                "key", "Key", ParamType.SELECT, "Home",
                listOf("Home", "Back", "Recents", "Power", "Volume up", "Volume down", "Camera", "Enter", "Custom keycode"),
                mappable = false,
            ),
            ParamSpec("keycode", "Custom keycode", ParamType.NUMBER, ""),
        ),
        outputs = listOf("sent", "key", "via"),
        specialAccess = SpecialAccess.PRIVILEGED,
    ),
    ModuleSpec(
        "priv.setting", "Write a system setting", "Privileged", "🔧", C_POWER,
        summaryKey = "key",
        description = "Reaches settings that Modify system settings alone cannot reach.",
        params = listOf(
            ParamSpec("namespace", "Namespace", ParamType.SELECT, "global", listOf("global", "system", "secure"), mappable = false),
            ParamSpec("key", "Key", ParamType.TEXT, ""),
            ParamSpec("value", "Value", ParamType.TEXT, ""),
            ParamSpec("action", "Action", ParamType.SELECT, "Write", listOf("Write", "Read"), mappable = false),
        ),
        outputs = listOf("done", "key", "value", "via"),
        specialAccess = SpecialAccess.PRIVILEGED,
    ),
)
