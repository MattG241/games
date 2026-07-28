package com.flowforge.android.model

internal val FLOW_MODULES: List<ModuleSpec> = listOf(
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
    ),
    ModuleSpec(
        "flow.router", "Router", "Flow control", "⑂", C_FLOW,
        description = "Splits the flow into branches. Each branch runs when its own filter passes.",
        outputs = listOf("routes"),
    ),
    ModuleSpec(
        "flow.iterator", "Iterator", "Flow control", "🔁", C_FLOW,
        description = "Splits an array into bundles — everything after it runs once per item.",
        summaryKey = "array",
        params = listOf(ParamSpec("array", "Array", ParamType.TEXT, "", hint = "{{2.json.items}}")),
        outputs = listOf("value", "index", "total"),
    ),
    ModuleSpec(
        "flow.repeater", "Repeater", "Flow control", "🔂", C_FLOW,
        summaryKey = "count",
        params = listOf(
            ParamSpec("count", "Repeats", ParamType.NUMBER, "3"),
            ParamSpec("gapMs", "Gap between repeats (ms)", ParamType.NUMBER, "0", mappable = false),
        ),
        outputs = listOf("index", "total"),
    ),
    ModuleSpec(
        "flow.sleep", "Sleep", "Flow control", "⏸", C_FLOW,
        summaryKey = "ms",
        params = listOf(ParamSpec("ms", "Milliseconds", ParamType.NUMBER, "1000")),
        outputs = listOf("slept"),
    ),
    ModuleSpec(
        "flow.stop", "Stop", "Flow control", "⏹", C_FLOW,
        params = listOf(
            ParamSpec("status", "Finish as", ParamType.SELECT, "Success", listOf("Success", "Error"), mappable = false),
            ParamSpec("message", "Message", ParamType.TEXT, ""),
        ),
        outputs = listOf("message"),
    ),
    ModuleSpec(
        "flow.aggregate", "Aggregate", "Flow control", "📦", C_FLOW,
        description = "Collects a value from every iteration into one joined string and array.",
        params = listOf(
            ParamSpec("value", "Value per bundle", ParamType.TEXT, ""),
            ParamSpec("separator", "Separator", ParamType.TEXT, ", ", mappable = false),
        ),
        outputs = listOf("text", "items", "count"),
    ),
)

internal val DATA_MODULES: List<ModuleSpec> = listOf(
    ModuleSpec(
        "tool.setVariable", "Set variable", "Data & logic", "🏷", C_TOOL,
        summaryKey = "name",
        params = listOf(
            ParamSpec("name", "Name", ParamType.TEXT, "myVar", mappable = false),
            ParamSpec("value", "Value", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("name", "value"),
    ),
    ModuleSpec(
        "tool.transform", "Compose / transform", "Data & logic", "✨", C_TOOL,
        description = "Builds a value out of mappings and functions, e.g. upper(trim({{1.text}})).",
        summaryKey = "value",
        params = listOf(ParamSpec("value", "Expression", ParamType.MULTILINE, "")),
        outputs = listOf("value"),
    ),
    ModuleSpec(
        "tool.json", "Parse JSON", "Data & logic", "{ }", C_TOOL,
        summaryKey = "text",
        params = listOf(ParamSpec("text", "JSON text", ParamType.MULTILINE, "")),
        outputs = listOf("json", "valid", "error"),
    ),
    ModuleSpec(
        "tool.jsonBuild", "Build JSON", "Data & logic", "🧱", C_TOOL,
        description = "Turns key=value lines into a JSON object, keeping numbers and booleans typed.",
        params = listOf(
            ParamSpec("fields", "Fields (one per line, key=value)", ParamType.MULTILINE, "", hint = "name={{1.title}}\ncount=3"),
            ParamSpec("pretty", "Pretty print", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("json", "text"),
    ),
    ModuleSpec(
        "tool.text", "Text tools", "Data & logic", "✏", C_TOOL,
        summaryKey = "action",
        params = listOf(
            ParamSpec(
                "action", "Action", ParamType.SELECT, "Replace",
                listOf("Replace", "Split", "Join", "Trim", "Upper", "Lower", "Substring", "Template", "Pad", "Reverse"),
                mappable = false,
            ),
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("find", "Find / separator", ParamType.TEXT, ""),
            ParamSpec("replace", "Replace with", ParamType.TEXT, ""),
            ParamSpec("from", "Start index", ParamType.NUMBER, ""),
            ParamSpec("to", "End index / length", ParamType.NUMBER, ""),
            ParamSpec("regex", "Treat find as a regex", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("value", "parts", "count", "length"),
    ),
    ModuleSpec(
        "tool.regex", "Match pattern", "Data & logic", "🔍", C_TOOL,
        summaryKey = "pattern",
        params = listOf(
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("pattern", "Regex", ParamType.TEXT, "", hint = "(\\d+)"),
            ParamSpec("all", "Find all matches", ParamType.BOOL, "false", mappable = false),
        ),
        outputs = listOf("matched", "match", "groups", "matches", "count"),
    ),
    ModuleSpec(
        "tool.math", "Math", "Data & logic", "🧮", C_TOOL,
        summaryKey = "expression",
        description = "Evaluates an arithmetic expression and rounds it however you like.",
        params = listOf(
            ParamSpec("expression", "Expression", ParamType.MULTILINE, "", hint = "({{2.json.price}} * 1.1) + 5"),
            ParamSpec("round", "Round to (decimal places, blank = none)", ParamType.NUMBER, ""),
        ),
        outputs = listOf("value", "text", "integer"),
    ),
    ModuleSpec(
        "tool.datetime", "Date and time", "Data & logic", "🕒", C_TOOL,
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Format now", listOf("Format now", "Format a value", "Parse", "Add", "Difference"), mappable = false),
            ParamSpec("value", "Value", ParamType.TEXT, "", hint = "{{now}} or 2026-07-28 09:00"),
            ParamSpec("other", "Second value (for Difference)", ParamType.TEXT, ""),
            ParamSpec("pattern", "Pattern", ParamType.TEXT, "yyyy-MM-dd HH:mm"),
            ParamSpec("amount", "Amount to add", ParamType.NUMBER, "0"),
            ParamSpec("unit", "Unit", ParamType.SELECT, "Minutes", listOf("Minutes", "Hours", "Days", "Weeks"), mappable = false),
        ),
        outputs = listOf("text", "timestamp", "iso", "days", "hours", "minutes", "seconds"),
    ),
    ModuleSpec(
        "tool.hash", "Hash, HMAC & encoding", "Data & logic", "🔐", C_TOOL,
        summaryKey = "algorithm",
        params = listOf(
            ParamSpec(
                "algorithm", "Algorithm", ParamType.SELECT, "SHA-256",
                listOf("SHA-256", "SHA-1", "SHA-512", "MD5", "HMAC-SHA256", "HMAC-SHA1", "Base64 encode", "Base64 decode", "URL encode", "URL decode"),
                mappable = false,
            ),
            ParamSpec("text", "Text", ParamType.MULTILINE, ""),
            ParamSpec("key", "HMAC key", ParamType.TEXT, ""),
            ParamSpec("output", "Output as", ParamType.SELECT, "Hex", listOf("Hex", "Base64"), mappable = false),
        ),
        outputs = listOf("value", "algorithm"),
    ),
    ModuleSpec(
        "tool.random", "Random", "Data & logic", "🎲", C_TOOL,
        summaryKey = "kind",
        params = listOf(
            ParamSpec("kind", "Generate", ParamType.SELECT, "Integer", listOf("Integer", "Decimal", "UUID", "Pick from list", "Token"), mappable = false),
            ParamSpec("min", "Minimum", ParamType.NUMBER, "1"),
            ParamSpec("max", "Maximum", ParamType.NUMBER, "100"),
            ParamSpec("list", "List (one per line)", ParamType.MULTILINE, ""),
            ParamSpec("length", "Token length", ParamType.NUMBER, "16"),
        ),
        outputs = listOf("value", "text"),
    ),
    ModuleSpec(
        "tool.datastore", "Data store", "Data & logic", "🗃", C_TOOL,
        description = "Key/value storage that survives between runs and scenarios.",
        summaryKey = "action",
        params = listOf(
            ParamSpec("action", "Action", ParamType.SELECT, "Get", listOf("Get", "Set", "Add number", "Delete", "List keys"), mappable = false),
            ParamSpec("key", "Key", ParamType.TEXT, ""),
            ParamSpec("value", "Value", ParamType.MULTILINE, ""),
        ),
        outputs = listOf("key", "value", "existed", "keys"),
    ),
    ModuleSpec(
        "tool.log", "Log message", "Data & logic", "📝", C_TOOL,
        summaryKey = "message",
        params = listOf(ParamSpec("message", "Message", ParamType.MULTILINE, "")),
        outputs = listOf("message"),
    ),
    ModuleSpec(
        "scenario.run", "Run another scenario", "Data & logic", "▶", C_TOOL,
        summaryKey = "scenario",
        params = listOf(
            ParamSpec("scenario", "Scenario name or id", ParamType.TEXT, "", mappable = false),
            ParamSpec("payload", "Payload (JSON, arrives as trigger bundle)", ParamType.MULTILINE, ""),
            ParamSpec("wait", "Wait for it to finish", ParamType.BOOL, "true", mappable = false),
        ),
        outputs = listOf("status", "output", "error"),
    ),
)
