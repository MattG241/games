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

enum class SpecialAccess {
    NOTIFICATION_LISTENER,
    WRITE_SETTINGS,
    DND_POLICY,
    BATTERY_OPTIMISATION,
    ACCESSIBILITY,
    USAGE_STATS,
    DEVICE_ADMIN,
    SCREEN_CAPTURE,
    PRIVILEGED,
}

internal const val C_TRIGGER = 0xFF7C4DFFL
internal const val C_HTTP = 0xFF2E7DF6L
internal const val C_DEVICE = 0xFF00A870L
internal const val C_MSG = 0xFFF2A13BL
internal const val C_FLOW = 0xFFE0457BL
internal const val C_TOOL = 0xFF6E7B8BL
internal const val C_MEDIA = 0xFFB5179EL
internal const val C_VISION = 0xFF0FA3B1L
internal const val C_UI = 0xFF8E44ADL
internal const val C_POWER = 0xFFD7263DL

object ModuleCatalog {

    val ALL: List<ModuleSpec> =
        TRIGGER_MODULES +
            NETWORK_MODULES +
            COMMUNICATION_MODULES +
            NOTIFICATION_MODULES +
            APP_INTENT_MODULES +
            DEVICE_CONTROL_MODULES +
            MEDIA_MODULES +
            FILE_MODULES +
            VISION_MODULES +
            LOCATION_MODULES +
            CONNECTIVITY_MODULES +
            PEOPLE_MODULES +
            UI_AUTOMATION_MODULES +
            PRIVILEGED_MODULES +
            FLOW_MODULES +
            DATA_MODULES

    private val byType = ALL.associateBy { it.type }

    fun spec(type: String): ModuleSpec? = byType[type]

    fun specOrUnknown(type: String): ModuleSpec =
        byType[type] ?: ModuleSpec(type, type, "Unknown", "❓", C_TOOL)

    val triggers: List<ModuleSpec> get() = ALL.filter { it.isTrigger }
    val actions: List<ModuleSpec> get() = ALL.filterNot { it.isTrigger }

    fun defaultParams(spec: ModuleSpec): Map<String, String> =
        spec.params.filter { it.default.isNotEmpty() }.associate { it.key to it.default }
}
