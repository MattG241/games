package com.flowforge.android.model

internal val NETWORK_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "http.request", "HTTP request", "Network", "🌐", C_HTTP,
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
    ),
    ModuleSpec(
        "http.download", "Download file", "Network", "⬇", C_HTTP,
        summaryKey = "url",
        params = listOf(
            ParamSpec("url", "URL", ParamType.TEXT, ""),
            ParamSpec("filename", "Save as", ParamType.TEXT, "download.bin"),
            ParamSpec("headers", "Headers (one per line)", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("path", "bytes", "uri"),
    ),
    ModuleSpec(
        "http.upload", "Upload file", "Network", "⬆", C_HTTP,
        summaryKey = "url",
        description = "Multipart POST of a file on disk, with optional extra form fields.",
        params = listOf(
            ParamSpec("url", "URL", ParamType.TEXT, ""),
            ParamSpec("path", "File to upload", ParamType.TEXT, ""),
            ParamSpec("field", "Form field name", ParamType.TEXT, "file"),
            ParamSpec("mimeType", "MIME type", ParamType.TEXT, "application/octet-stream"),
            ParamSpec("fields", "Extra fields (one per line, key=value)", ParamType.MULTILINE, ""),
            ParamSpec("headers", "Headers (one per line)", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("status", "body", "json", "ok"),
    ),
    ModuleSpec(
        "net.ping", "Ping / port check", "Network", "📡", C_HTTP,
        summaryKey = "host",
        description = "ICMP-style reachability, or a TCP connect when you give it a port.",
        params = listOf(
            ParamSpec("host", "Host or IP", ParamType.TEXT, ""),
            ParamSpec("port", "Port (blank = reachability only)", ParamType.NUMBER, ""),
            ParamSpec("timeout", "Timeout (ms)", ParamType.NUMBER, "3000", mappable = false),
        ),
        outputs = listOf("reachable", "latencyMs", "host", "address", "port"),
    ),
    ModuleSpec(
        "net.websocket", "WebSocket send", "Network", "🔌", C_HTTP,
        summaryKey = "url",
        description = "Connects, sends one message, optionally waits for a reply, then closes.",
        params = listOf(
            ParamSpec("url", "URL", ParamType.TEXT, "", hint = "wss://example.com/socket"),
            ParamSpec("message", "Message", ParamType.MULTILINE, ""),
            ParamSpec("headers", "Headers (one per line)", ParamType.MULTILINE, ""),
            ParamSpec("waitForReply", "Wait for a reply", ParamType.BOOL, "true", mappable = false),
            ParamSpec("timeout", "Reply timeout (seconds)", ParamType.NUMBER, "10", mappable = false),
        ),
        outputs = listOf("sent", "reply", "json", "closed"),
    ),
    ModuleSpec(
        "net.mqtt", "MQTT publish", "Network", "📮", C_HTTP,
        summaryKey = "topic",
        description = "Publishes one MQTT 3.1.1 message to a broker over plain TCP.",
        params = listOf(
            ParamSpec("host", "Broker host", ParamType.TEXT, ""),
            ParamSpec("port", "Port", ParamType.NUMBER, "1883", mappable = false),
            ParamSpec("topic", "Topic", ParamType.TEXT, ""),
            ParamSpec("message", "Payload", ParamType.MULTILINE, ""),
            ParamSpec("clientId", "Client id", ParamType.TEXT, "flowforge"),
            ParamSpec("username", "Username (optional)", ParamType.TEXT, ""),
            ParamSpec("password", "Password (optional)", ParamType.TEXT, ""),
            ParamSpec("retain", "Retain", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("published", "topic", "bytes"),
    ),
    ModuleSpec(
        "webhook.respond", "Webhook response", "Network", "↩", C_HTTP,
        description = "Sends a custom reply to the caller of a webhook trigger.",
        params = listOf(
            ParamSpec("status", "Status code", ParamType.NUMBER, "200", mappable = false),
            ParamSpec("contentType", "Content type", ParamType.TEXT, "application/json", mappable = false),
            ParamSpec("body", "Body", ParamType.MULTILINE, "{\"ok\":true}"),
        ),
        outputs = listOf("sent"),
    ),
)

internal val COMMUNICATION_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "sms.send", "Send SMS", "Communication", "✉", C_MSG,
        summaryKey = "to",
        params = listOf(
            ParamSpec("to", "To number", ParamType.TEXT, ""),
            ParamSpec("message", "Message", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("to", "parts"),
        permissions = listOf("android.permission.SEND_SMS"),
    ),
    ModuleSpec(
        "phone.call", "Place phone call", "Communication", "📞", C_MSG,
        summaryKey = "number",
        params = listOf(
            ParamSpec("number", "Number", ParamType.TEXT, ""),
            ParamSpec("mode", "Mode", ParamType.SELECT, "Dial immediately", listOf("Dial immediately", "Open dialer"), mappable = false),
        ),
        outputs = listOf("number", "placed"),
        permissions = listOf("android.permission.CALL_PHONE"),
    ),
    ModuleSpec(
        "phone.answer", "Answer or end call", "Communication", "☎", C_MSG,
        summaryKey = "action",
        description = "Answers a ringing call or hangs up the current one.",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Answer", listOf("Answer", "End call"), mappable = false)
        ),
        outputs = listOf("action", "done"),
        permissions = listOf("android.permission.ANSWER_PHONE_CALLS"),
    ),
    ModuleSpec(
        "share.email", "Compose email", "Communication", "📧", C_MSG,
        summaryKey = "subject",
        description = "Opens the mail app with everything pre-filled.",
        params = listOf(
            ParamSpec("to", "To (comma separated)", ParamType.TEXT, ""),
            ParamSpec("cc", "Cc", ParamType.TEXT, ""),
            ParamSpec("subject", "Subject", ParamType.TEXT, ""),
            ParamSpec("body", "Body", ParamType.MULTILINE, ""),
            ParamSpec("attachment", "Attachment file (optional)", ParamType.TEXT, ""),
        ),
        outputs = listOf("opened"),
    ),
    ModuleSpec(
        "share.sheet", "Open share sheet", "Communication", "📤", C_MSG,
        summaryKey = "text",
        params = listOf(
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("subject", "Subject", ParamType.TEXT, ""),
            ParamSpec("file", "File to share (optional)", ParamType.TEXT, ""),
            ParamSpec("mimeType", "MIME type", ParamType.TEXT, "text/plain"),
        ),
        outputs = listOf("opened"),
    ),
)

internal val NOTIFICATION_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "notify.send", "Post notification", "Notifications", "🔔", C_MSG,
        summaryKey = "title",
        description = "Up to three buttons, each of which can run another scenario when tapped.",
        params = listOf(
            ParamSpec("title", "Title", ParamType.TEXT, "FlowForge"),
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("channel", "Importance", ParamType.SELECT, "Default", listOf("Default", "High (heads up)", "Silent"), mappable = false),
            ParamSpec("ongoing", "Ongoing (not swipeable)", ParamType.BOOL, "false", mappable = false),
            ParamSpec("tag", "Replace notification with tag", ParamType.TEXT, ""),
            ParamSpec("actions", "Buttons (one per line, Label=Scenario name)", ParamType.MULTILINE, "", hint = "Snooze=Snooze alarm"),
        ),
        outputs = listOf("id", "title"),
        permissions = listOf("android.permission.POST_NOTIFICATIONS"),
    ),
    ModuleSpec(
        "notify.dismiss", "Dismiss notification", "Notifications", "🧹", C_MSG,
        summaryKey = "scope",
        description = "Clears your own notifications, or any app's via notification access.",
        params = listOf(
            ParamSpec("scope", "Dismiss", ParamType.SELECT, "By key", listOf("By key", "All from an app", "All", "My own by tag"), mappable = false),
            ParamSpec("key", "Notification key", ParamType.TEXT, "", hint = "{{1.key}}"),
            ParamSpec("package", "App", ParamType.APP, ""),
            ParamSpec("tag", "Tag used when posting", ParamType.TEXT, ""),
        ),
        outputs = listOf("dismissed", "count"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "notify.reply", "Reply to a notification", "Notifications", "💬", C_MSG,
        summaryKey = "text",
        description = "Uses the notification's own inline reply — how you answer a chat message without opening the app.",
        params = listOf(
            ParamSpec("key", "Notification key", ParamType.TEXT, "{{1.key}}"),
            ParamSpec("text", "Reply text", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("replied", "key"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "notify.snooze", "Snooze notification", "Notifications", "😴", C_MSG,
        summaryKey = "minutes",
        params = listOf(
            ParamSpec("key", "Notification key", ParamType.TEXT, "{{1.key}}"),
            ParamSpec("minutes", "Snooze for (minutes)", ParamType.NUMBER, "10"),
        ),
        outputs = listOf("snoozed", "untilTimestamp"),
        specialAccess = SpecialAccess.NOTIFICATION_LISTENER,
    ),
    ModuleSpec(
        "device.tts", "Speak text", "Notifications", "🗣", C_MSG,
        summaryKey = "text",
        params = listOf(
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("rate", "Speed", ParamType.NUMBER, "1.0", mappable = false),
            ParamSpec("queue", "Queue behind current speech", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("spoken"),
    ),
    ModuleSpec(
        "device.toast", "Show toast", "Notifications", "💬", C_MSG,
        summaryKey = "text",
        params = listOf(
            ParamSpec("text", "Text", ParamType.TEXT, ""),
            ParamSpec("long", "Long duration", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("shown"),
    ),
)
