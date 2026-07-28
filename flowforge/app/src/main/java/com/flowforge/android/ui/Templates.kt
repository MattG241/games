package com.flowforge.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowforge.android.model.Blueprint
import com.flowforge.android.model.ModuleCatalog
import com.flowforge.android.model.ModuleNode
import java.util.UUID

class ScenarioTemplate(
    val name: String,
    val icon: String,
    val summary: String,
    private val modules: List<ModuleNode>,
    private val description: String = "",
) {
    fun build(): Blueprint = Blueprint(
        id = UUID.randomUUID().toString(),
        name = name,
        description = description,
        modules = modules,
    )
}

private fun node(id: Int, type: String, vararg params: Pair<String, String>): ModuleNode {
    val spec = ModuleCatalog.specOrUnknown(type)
    return ModuleNode(
        id = id,
        type = type,
        params = ModuleCatalog.defaultParams(spec) + params.toMap(),
    )
}

val SCENARIO_TEMPLATES: List<ScenarioTemplate> = listOf(
    ScenarioTemplate(
        "Blank scenario", "◻", "Start from a manual trigger and build it yourself",
        listOf(node(1, "trigger.manual")),
    ),
    ScenarioTemplate(
        "Forward SMS to a webhook", "✉", "Every SMS becomes a JSON POST to your endpoint",
        listOf(
            node(1, "trigger.sms"),
            node(
                2, "http.request",
                "method" to "POST",
                "url" to "https://hook.eu2.make.com/your-webhook-id",
                "body" to "{\n  \"from\": \"{{1.from}}\",\n  \"text\": \"{{1.text}}\",\n  \"at\": {{1.timestamp}}\n}",
            ),
        ),
        description = "Point the URL at a Make.com webhook, Zapier catch hook, or your own server.",
    ),
    ScenarioTemplate(
        "Low battery alert", "🔋", "Speak and notify when the battery drops below 20%",
        listOf(
            node(1, "trigger.battery", "compare" to "Below", "level" to "20"),
            node(2, "notify.send", "title" to "Battery low", "text" to "Down to {{1.level}}%", "channel" to "High (heads up)"),
            node(3, "device.tts", "text" to "Battery is at {{1.level}} percent"),
        ),
    ),
    ScenarioTemplate(
        "Webhook remote control", "☁", "Run anything on your phone from an HTTP call",
        listOf(
            node(1, "trigger.webhook", "path" to "phone", "method" to "ANY"),
            node(2, "notify.send", "title" to "Remote call", "text" to "{{1.body}}"),
            node(3, "webhook.respond", "status" to "200", "body" to "{\"ok\":true,\"handled\":\"{{1.method}}\"}"),
        ),
        description = "POST to http://<phone-ip>:8420/phone from your laptop, Make.com, or Home Assistant.",
    ),
    ScenarioTemplate(
        "Shake to share location", "🤚", "Shake the phone to text someone where you are",
        listOf(
            node(1, "trigger.shake", "sensitivity" to "16"),
            node(2, "device.location", "accuracy" to "Fine"),
            node(
                3, "sms.send",
                "to" to "+61400000000",
                "message" to "I'm here: https://maps.google.com/?q={{2.latitude}},{{2.longitude}}",
            ),
        ),
    ),
    ScenarioTemplate(
        "Morning briefing", "⏰", "A daily HTTP call read aloud at 7am",
        listOf(
            node(1, "trigger.schedule", "mode" to "Daily at time", "time" to "07:00"),
            node(
                2, "http.request",
                "method" to "GET",
                "url" to "https://wttr.in/Sydney?format=j1",
            ),
            node(
                3, "device.tts",
                "text" to "Good morning. It is {{2.json.current_condition[0].temp_C}} degrees and {{2.json.current_condition[0].weatherDesc[0].value}}.",
            ),
        ),
    ),
    ScenarioTemplate(
        "Forward one app's notifications", "🔔", "Mirror a chosen app's notifications to an endpoint",
        listOf(
            node(1, "trigger.notification", "contains" to ""),
            node(
                2, "http.request",
                "method" to "POST",
                "url" to "https://example.com/notifications",
                "body" to "{\n  \"app\": \"{{1.appName}}\",\n  \"title\": \"{{1.title}}\",\n  \"text\": \"{{1.text}}\"\n}",
            ),
        ),
        description = "Needs notification access — grant it from Settings inside the app.",
    ),
    ScenarioTemplate(
        "Arrive home on Wi-Fi", "📶", "Wi-Fi connects, then the phone sets itself up",
        listOf(
            node(1, "trigger.wifi", "state" to "Connected", "ssid" to "YourNetwork"),
            node(2, "device.volume", "stream" to "Media", "percent" to "70"),
            node(3, "device.ringer", "mode" to "Normal"),
            node(4, "notify.send", "title" to "Welcome home", "text" to "Connected to {{1.ssid}}"),
        ),
    ),
    ScenarioTemplate(
        "Auto-reply to a chat message", "💬", "Answer a messaging notification without opening the app",
        listOf(
            node(1, "trigger.notification", "contains" to "on my way?"),
            node(2, "notify.reply", "key" to "{{1.key}}", "text" to "On my way, about 10 minutes."),
        ),
        description = "Needs notification access. Works with any app that offers inline reply.",
    ),
    ScenarioTemplate(
        "Read a receipt with OCR", "🔎", "Photograph something and pull the text out of it",
        listOf(
            node(1, "trigger.manual"),
            node(2, "camera.photo", "lens" to "Back", "filename" to "photos/receipt.jpg"),
            node(3, "vision.ocr", "path" to "{{2.path}}"),
            node(4, "tool.datastore", "action" to "Set", "key" to "lastReceipt", "value" to "{{3.text}}"),
            node(5, "notify.send", "title" to "Scanned", "text" to "{{3.text}}"),
        ),
    ),
    ScenarioTemplate(
        "Log every unlock to a database", "🗄", "A tiny local dataset you can query later",
        listOf(
            node(1, "trigger.screen", "state" to "Unlocked"),
            node(
                2, "tool.sqlite",
                "database" to "flowforge/usage.db",
                "sql" to "CREATE TABLE IF NOT EXISTS unlocks (at INTEGER, battery INTEGER)",
            ),
            node(3, "device.info"),
            node(
                4, "tool.sqlite",
                "database" to "flowforge/usage.db",
                "sql" to "INSERT INTO unlocks (at, battery) VALUES (?, ?)",
                "args" to "{{1.timestamp}}\n{{3.battery}}",
            ),
        ),
    ),
    ScenarioTemplate(
        "Signed webhook call", "🔐", "HMAC the payload before sending it, the way real APIs want",
        listOf(
            node(1, "trigger.manual"),
            node(2, "tool.jsonBuild", "fields" to "event=ping\nat={{now}}"),
            node(3, "tool.hash", "algorithm" to "HMAC-SHA256", "text" to "{{2.text}}", "key" to "your-secret"),
            node(
                4, "http.request",
                "method" to "POST",
                "url" to "https://example.com/hook",
                "headers" to "X-Signature: {{3.value}}",
                "body" to "{{2.text}}",
            ),
        ),
    ),
    ScenarioTemplate(
        "Silence the phone at work", "🤫", "Geofence-ish: Wi-Fi arrives, the phone goes quiet",
        listOf(
            node(1, "trigger.wifi", "state" to "Connected", "ssid" to "OfficeWiFi"),
            node(2, "device.dnd", "mode" to "Priority only"),
            node(3, "device.volume", "stream" to "Ring", "percent" to "0"),
            node(4, "calendar.query", "hours" to "9"),
            node(5, "device.tts", "text" to "You have {{4.count}} meetings today. Next up: {{4.next}}."),
        ),
    ),
    ScenarioTemplate(
        "Privileged: airplane mode toggle", "📻", "A silent radio switch, via Shizuku or root",
        listOf(
            node(1, "trigger.manual"),
            node(2, "priv.radio", "radio" to "Airplane mode", "state" to "Toggle"),
            node(3, "notify.send", "title" to "Airplane mode", "text" to "Now {{2.state}} (via {{2.via}})"),
        ),
        description = "Needs Shizuku running or a rooted device — set that up in Settings first.",
    ),
    ScenarioTemplate(
        "Poll an API and branch", "⑂", "A scheduled call with a router and filters",
        listOf(
            node(1, "trigger.schedule", "mode" to "Every N minutes", "minutes" to "30"),
            node(2, "http.request", "method" to "GET", "url" to "https://api.example.com/status"),
            node(3, "flow.filter", "left" to "{{2.status}}", "op" to "equals", "right" to "200"),
            node(4, "notify.send", "title" to "API healthy", "text" to "{{2.body}}", "channel" to "Silent"),
        ),
    ),
)

@Composable
fun TemplatePicker(onPick: (ScenarioTemplate) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Start from", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Templates are fully editable — they just save you the first few taps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(SCENARIO_TEMPLATES) { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(template) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(template.icon, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(template.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            template.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
