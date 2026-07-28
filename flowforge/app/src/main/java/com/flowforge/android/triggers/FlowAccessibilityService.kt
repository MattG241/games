package com.flowforge.android.triggers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Drives the UI automation modules: tapping, swiping, typing, reading the screen and pressing the
 * system buttons. Android only hands these powers to an accessibility service the user turns on.
 */
class FlowAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ actions

    fun performGlobal(action: String): Boolean {
        val id = when (action) {
            "Back" -> GLOBAL_ACTION_BACK
            "Home" -> GLOBAL_ACTION_HOME
            "Recents" -> GLOBAL_ACTION_RECENTS
            "Notification shade" -> GLOBAL_ACTION_NOTIFICATIONS
            "Quick settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "Power dialog" -> GLOBAL_ACTION_POWER_DIALOG
            "Split screen" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            "Lock screen" ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN
                else return false
            else -> return false
        }
        return performGlobalAction(id)
    }

    /** Finds a node by visible text, view id or content description. */
    fun findNode(by: String, target: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = when (by) {
            "View id" -> root.findAccessibilityNodeInfosByViewId(target)
            "Content description" -> collect(root) { node ->
                node.contentDescription?.toString()?.contains(target, ignoreCase = true) == true
            }
            else -> root.findAccessibilityNodeInfosByText(target)
                .takeIf { it.isNotEmpty() }
                ?: collect(root) { node ->
                    node.text?.toString()?.contains(target, ignoreCase = true) == true
                }
        }
        return matches?.firstOrNull()
    }

    fun tapNode(node: AccessibilityNodeInfo, longPress: Boolean): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        // The node holding the text is often not the clickable one — walk up until something is.
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        val clickable = candidate ?: node
        val action =
            if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK
            else AccessibilityNodeInfo.ACTION_CLICK
        return clickable.performAction(action)
    }

    fun tapAt(x: Float, y: Float, longPress: Boolean): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val duration = if (longPress) 600L else 50L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(20, 10_000)))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String, intoLabel: String): Boolean {
        val node = if (intoLabel.isBlank()) {
            findFocusedEditable()
        } else {
            findNode("Text", intoLabel)?.let { candidate ->
                var editable: AccessibilityNodeInfo? = candidate
                while (editable != null && !editable.isEditable) editable = editable.parent
                editable ?: candidate
            }
        } ?: return false

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun readScreen(contains: String): Triple<String, List<String>, String> {
        val root = rootInActiveWindow
            ?: return Triple("", emptyList(), "")
        val texts = mutableListOf<String>()
        walk(root) { node ->
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null && (contains.isBlank() || label.contains(contains, ignoreCase = true))) {
                texts += label
            }
        }
        val unique = texts.distinct()
        return Triple(unique.joinToString("\n"), unique, root.packageName?.toString().orEmpty())
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        val root = rootInActiveWindow ?: return null
        return collect(root) { it.isEditable }?.firstOrNull()
    }

    private fun collect(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo>? {
        val found = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { if (predicate(it)) found += it }
        return found.takeIf { it.isNotEmpty() }
    }

    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, visit) }
        }
    }

    companion object {
        @Volatile
        var instance: FlowAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val component = ComponentName(context, FlowAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }
    }
}
