package com.flowforge.android.engine.runners

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.FileProvider
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

suspend fun runCommsModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "phone.call" -> placeCall(node, env)
    "phone.answer" -> answerOrEnd(node, env)
    "share.email" -> composeEmail(node, env)
    "share.sheet" -> shareSheet(node, env)
    "contacts.lookup" -> lookupContact(node, env)
    "contacts.save" -> saveContact(node, env)
    "calendar.create" -> createEvent(node, env)
    "calendar.query" -> queryEvents(node, env)
    else -> null
}

private fun placeCall(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val number = env.text(node, "number").trim()
    require(number.isNotBlank()) { "Enter a number to call" }
    val dial = env.choice(node, "mode", "Dial immediately") == "Dial immediately"
    val action = if (dial) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, Uri.parse("tel:${Uri.encode(number)}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    env.app.startActivity(intent)
    return mapOf("number" to number, "placed" to dial)
}

private fun answerOrEnd(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val telecom = env.app.getSystemService(TelecomManager::class.java)
        ?: error("Telecom service unavailable on this device")
    val action = env.choice(node, "action", "Answer")
    val done = runCatching {
        @Suppress("MissingPermission")
        if (action == "Answer") {
            telecom.acceptRingingCall()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telecom.endCall()
            else error("Ending calls needs Android 9 or newer")
        }
        true
    }.getOrElse {
        error("$action needs the Answer phone calls permission — grant it in Settings inside FlowForge")
    }
    return mapOf("action" to action, "done" to done)
}

private fun composeEmail(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        env.text(node, "to").takeIf { it.isNotBlank() }?.let {
            putExtra(Intent.EXTRA_EMAIL, it.split(',', ';').map { part -> part.trim() }.toTypedArray())
        }
        env.text(node, "cc").takeIf { it.isNotBlank() }?.let {
            putExtra(Intent.EXTRA_CC, it.split(',', ';').map { part -> part.trim() }.toTypedArray())
        }
        putExtra(Intent.EXTRA_SUBJECT, env.text(node, "subject"))
        putExtra(Intent.EXTRA_TEXT, env.text(node, "body"))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val attachment = env.text(node, "attachment")
    if (attachment.isNotBlank()) {
        // ACTION_SENDTO ignores attachments, so switch to a send intent when there is one.
        val file = resolveFile(env, attachment)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(env.app, "${env.app.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtras(intent)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            env.app.startActivity(Intent.createChooser(send, "Send email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return mapOf("opened" to true, "attached" to true)
        }
    }

    env.app.startActivity(intent)
    return mapOf("opened" to true, "attached" to false)
}

private fun shareSheet(node: ModuleNode, env: RunEnv): Map<String, Any?> {
    val text = env.text(node, "text")
    val filePath = env.text(node, "file")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = env.text(node, "mimeType").ifBlank { "text/plain" }
        if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        env.text(node, "subject").takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    if (filePath.isNotBlank()) {
        val file = resolveFile(env, filePath)
        require(file.exists()) { "No file at ${file.absolutePath}" }
        val uri = FileProvider.getUriForFile(env.app, "${env.app.packageName}.files", file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    env.app.startActivity(
        Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    return mapOf("opened" to true)
}

private suspend fun lookupContact(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val query = env.text(node, "query").trim()
    require(query.isNotBlank()) { "Enter a name or number to look up" }
    val limit = env.number(node, "limit", 5.0).toInt().coerceIn(1, 50)

    val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
    )

    val results = mutableListOf<Map<String, Any?>>()
    env.app.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext() && results.size < limit) {
            results += mapOf(
                "name" to cursor.getString(0).orEmpty(),
                "number" to cursor.getString(1).orEmpty(),
                "id" to cursor.getString(2).orEmpty(),
            )
        }
    }

    // Fall back to matching by name when the number filter finds nothing.
    if (results.isEmpty()) {
        env.app.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && results.size < limit) {
                results += mapOf(
                    "id" to cursor.getString(0).orEmpty(),
                    "name" to cursor.getString(1).orEmpty(),
                    "number" to "",
                )
            }
        }
    }

    val first = results.firstOrNull()
    val email = first?.get("id")?.toString()?.let { contactId ->
        env.app.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    mapOf(
        "found" to results.isNotEmpty(),
        "name" to (first?.get("name") ?: ""),
        "number" to (first?.get("number") ?: ""),
        "email" to (email ?: ""),
        "contacts" to results,
        "count" to results.size.toDouble(),
    )
}

private suspend fun saveContact(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val name = env.text(node, "name").trim()
    require(name.isNotBlank()) { "A contact needs a name" }
    val number = env.text(node, "number").trim()
    val email = env.text(node, "email").trim()
    val note = env.text(node, "note").trim()

    val ops = ArrayList<android.content.ContentProviderOperation>()
    ops += android.content.ContentProviderOperation
        .newInsert(ContactsContract.RawContacts.CONTENT_URI)
        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
        .build()

    fun dataRow(mimeType: String, block: android.content.ContentProviderOperation.Builder.() -> Unit) {
        ops += android.content.ContentProviderOperation
            .newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)
            .apply(block)
            .build()
    }

    dataRow(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE) {
        withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
    }
    if (number.isNotBlank()) {
        dataRow(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
            withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            withValue(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            )
        }
    }
    if (email.isNotBlank()) {
        dataRow(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
            withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
            withValue(
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.TYPE_HOME,
            )
        }
    }
    if (note.isNotBlank()) {
        dataRow(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE) {
            withValue(ContactsContract.CommonDataKinds.Note.NOTE, note)
        }
    }

    env.app.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    mapOf("saved" to true, "name" to name)
}

private val EVENT_FORMATS = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd", "HH:mm")

internal fun parseMoment(raw: String, fallback: Long = System.currentTimeMillis()): Long {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return fallback
    trimmed.toDoubleOrNull()?.let { return it.toLong() }
    for (pattern in EVENT_FORMATS) {
        runCatching {
            val parsed = SimpleDateFormat(pattern, Locale.getDefault()).parse(trimmed) ?: return@runCatching
            if (pattern == "HH:mm") {
                // A bare time means the next occurrence of that time today or tomorrow.
                val today = java.util.Calendar.getInstance()
                val wanted = java.util.Calendar.getInstance().apply { time = parsed }
                today.set(java.util.Calendar.HOUR_OF_DAY, wanted.get(java.util.Calendar.HOUR_OF_DAY))
                today.set(java.util.Calendar.MINUTE, wanted.get(java.util.Calendar.MINUTE))
                today.set(java.util.Calendar.SECOND, 0)
                if (today.timeInMillis < System.currentTimeMillis()) today.add(java.util.Calendar.DAY_OF_YEAR, 1)
                return today.timeInMillis
            }
            return parsed.time
        }
    }
    return fallback
}

private suspend fun createEvent(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val title = env.text(node, "title").trim()
    require(title.isNotBlank()) { "An event needs a title" }
    val start = parseMoment(env.text(node, "start"))
    val minutes = env.number(node, "minutes", 60.0).toLong().coerceAtLeast(1)
    val end = start + minutes * 60_000

    val calendarId = env.text(node, "calendarId").trim().ifBlank { defaultCalendarId(env)?.toString() }
        ?: error("No writable calendar found on this device")

    val values = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId.toLong())
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, start)
        put(CalendarContract.Events.DTEND, end)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        env.text(node, "location").takeIf { it.isNotBlank() }
            ?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        env.text(node, "description").takeIf { it.isNotBlank() }
            ?.let { put(CalendarContract.Events.DESCRIPTION, it) }
    }

    val uri = env.app.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        ?: error("The calendar provider refused the event")
    val eventId = ContentUris.parseId(uri)

    env.text(node, "reminderMinutes").trim().toIntOrNull()?.let { reminder ->
        runCatching {
            env.app.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminder)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
        }
    }

    mapOf("created" to true, "eventId" to eventId.toDouble(), "start" to start.toDouble(), "end" to end.toDouble())
}

private fun defaultCalendarId(env: RunEnv): Long? =
    env.app.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
        arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
        null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

private suspend fun queryEvents(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val hours = env.number(node, "hours", 24.0).toLong().coerceIn(1, 24 * 365)
    val from = System.currentTimeMillis()
    val to = from + hours * 3_600_000
    val contains = env.text(node, "contains").trim()
    val calendarId = env.text(node, "calendarId").trim()
    val limit = env.number(node, "limit", 20.0).toInt().coerceIn(1, 200)

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, from)
    ContentUris.appendId(builder, to)

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.ALL_DAY,
    )

    val events = mutableListOf<Map<String, Any?>>()
    env.app.contentResolver.query(
        builder.build(),
        projection,
        calendarId.takeIf { it.isNotBlank() }?.let { "${CalendarContract.Instances.CALENDAR_ID} = ?" },
        calendarId.takeIf { it.isNotBlank() }?.let { arrayOf(it) },
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        while (cursor.moveToNext() && events.size < limit) {
            val title = cursor.getString(0).orEmpty()
            if (contains.isNotBlank() && !title.contains(contains, ignoreCase = true)) continue
            val begin = cursor.getLong(1)
            events += mapOf(
                "title" to title,
                "start" to begin.toDouble(),
                "end" to cursor.getLong(2).toDouble(),
                "startText" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(begin),
                "location" to cursor.getString(3).orEmpty(),
                "calendarId" to cursor.getLong(4).toDouble(),
                "allDay" to (cursor.getInt(5) == 1),
            )
        }
    }

    mapOf(
        "events" to events,
        "count" to events.size.toDouble(),
        "next" to (events.firstOrNull()?.get("title") ?: ""),
        "nextStart" to (events.firstOrNull()?.get("startText") ?: ""),
    )
}
