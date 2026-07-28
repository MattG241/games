package com.flowforge.android.engine.runners

import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import com.flowforge.android.triggers.FlowAccessibilityService
import kotlinx.coroutines.delay

suspend fun runUiModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "ui.tap" -> tap(node, env)
    "ui.swipe" -> swipe(node, env)
    "ui.type" -> typeText(node, env)
    "ui.read" -> readScreen(node, env)
    "ui.global" -> globalAction(node, env)
    else -> null
}

private fun service(): FlowAccessibilityService =
    FlowAccessibilityService.instance
        ?: error("UI automation needs the FlowForge accessibility service — turn it on in Settings inside FlowForge")

private suspend fun tap(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val service = service()
    val by = env.choice(node, "by", "Text")
    val longPress = env.bool(node, "longPress", false)

    if (by == "Coordinates") {
        val x = env.number(node, "x", -1.0).toFloat()
        val y = env.number(node, "y", -1.0).toFloat()
        require(x >= 0 && y >= 0) { "Enter both X and Y coordinates" }
        val dispatched = service.tapAt(x, y, longPress)
        delay(200)
        return mapOf("tapped" to dispatched, "target" to "$x,$y")
    }

    val target = env.text(node, "target").trim()
    require(target.isNotBlank()) { "Enter the text, id or description to tap" }
    val found = service.findNode(by, target)
        ?: error("Nothing on screen matches \"$target\"")
    val tapped = service.tapNode(found, longPress)
    delay(200)
    return mapOf("tapped" to tapped, "target" to target)
}

private suspend fun swipe(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val service = service()
    val direction = env.choice(node, "direction", "Up")
    val metrics = env.app.resources.displayMetrics
    val width = metrics.widthPixels.toFloat()
    val height = metrics.heightPixels.toFloat()

    val stroke = when (direction) {
        "Up" -> Quad(width / 2, height * 0.75f, width / 2, height * 0.25f)
        "Down" -> Quad(width / 2, height * 0.25f, width / 2, height * 0.75f)
        "Left" -> Quad(width * 0.8f, height / 2, width * 0.2f, height / 2)
        "Right" -> Quad(width * 0.2f, height / 2, width * 0.8f, height / 2)
        else -> Quad(
            env.number(node, "x1", 0.0).toFloat(),
            env.number(node, "y1", 0.0).toFloat(),
            env.number(node, "x2", 0.0).toFloat(),
            env.number(node, "y2", 0.0).toFloat(),
        )
    }

    val swiped = service.swipe(
        stroke.x1, stroke.y1, stroke.x2, stroke.y2,
        env.number(node, "durationMs", 300.0).toLong(),
    )
    delay(300)
    return mapOf("swiped" to swiped, "direction" to direction)
}

private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

private suspend fun typeText(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val service = service()
    val text = env.text(node, "text")
    val typed = service.typeText(text, env.text(node, "intoField").trim())
    if (!typed) error("Could not find a text field to type into")
    if (env.bool(node, "submit", false)) {
        delay(150)
        service.performGlobal("Back") // dismisses the keyboard before whatever runs next
    }
    return mapOf("typed" to true, "text" to text)
}

private fun readScreen(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val service = service()
    val (text, nodes, pkg) = service.readScreen(env.text(node, "contains").trim())
    return mapOf(
        "text" to text,
        "nodes" to nodes,
        "count" to nodes.size.toDouble(),
        "package" to pkg,
    )
}

private suspend fun globalAction(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val service = service()
    val action = env.choice(node, "action", "Back")
    val performed = service.performGlobal(action)
    delay(200)
    return mapOf("performed" to performed, "action" to action)
}
