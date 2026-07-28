package com.flowforge.android.engine.runners

import android.database.sqlite.SQLiteDatabase
import com.flowforge.android.engine.RunEnv
import com.flowforge.android.model.ModuleNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

suspend fun runFileModule(type: String, node: ModuleNode, env: RunEnv): Map<String, Any?>? = when (type) {
    "file.manage" -> manageFile(node, env)
    "file.list" -> listFolder(node, env)
    "file.zip" -> zipOrUnzip(node, env)
    "tool.sqlite" -> sqlite(node, env)
    else -> null
}

private suspend fun manageFile(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val action = env.choice(node, "action", "Copy")
    val source = resolveFile(env, env.text(node, "path"))
    val overwrite = env.bool(node, "overwrite", true)

    when (action) {
        "Delete" -> {
            val existed = source.exists()
            val removed = if (source.isDirectory) source.deleteRecursively() else source.delete()
            mapOf("done" to (removed || !existed), "path" to source.absolutePath, "existed" to existed)
        }
        "Create folder" -> {
            val made = source.mkdirs() || source.isDirectory
            mapOf("done" to made, "path" to source.absolutePath)
        }
        else -> {
            require(source.exists()) { "No file at ${source.absolutePath}" }
            val targetText = env.text(node, "target")
            require(targetText.isNotBlank()) { "Enter a destination" }
            var target = resolveFile(env, targetText)
            // A destination folder means "keep the same file name inside it".
            if (target.isDirectory) target = File(target, source.name)
            target.parentFile?.mkdirs()
            if (target.exists() && !overwrite) error("${target.absolutePath} already exists")

            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = overwrite)
            } else {
                source.copyTo(target, overwrite = overwrite)
            }
            if (action == "Move") {
                if (source.isDirectory) source.deleteRecursively() else source.delete()
            }
            mapOf("done" to true, "path" to target.absolutePath, "bytes" to target.length().toDouble())
        }
    }
}

private suspend fun listFolder(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val folder = resolveFile(env, env.text(node, "path"))
    require(folder.isDirectory) { "${folder.absolutePath} is not a folder" }
    val glob = env.text(node, "filter").trim()
    val regex = glob.takeIf { it.isNotBlank() }?.let {
        Regex(
            "^" + Regex.escape(it)
                .replace("\\*", "\\E.*\\Q")
                .replace("\\?", "\\E.\\Q") + "$",
            RegexOption.IGNORE_CASE,
        )
    }

    val walker = if (env.bool(node, "recursive", false)) folder.walkTopDown() else folder.walkTopDown().maxDepth(1)
    val files = walker
        .filter { it != folder }
        .filter { regex == null || regex.matches(it.name) }
        .map {
            mapOf(
                "name" to it.name,
                "path" to it.absolutePath,
                "bytes" to it.length().toDouble(),
                "isFolder" to it.isDirectory,
                "modified" to it.lastModified().toDouble(),
            )
        }
        .toList()

    mapOf(
        "files" to files,
        "names" to files.map { it["name"] },
        "count" to files.size.toDouble(),
    )
}

private suspend fun zipOrUnzip(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val source = resolveFile(env, env.text(node, "path"))
    require(source.exists()) { "No file or folder at ${source.absolutePath}" }
    val action = env.choice(node, "action", "Zip")

    if (action == "Zip") {
        val target = resolveFile(env, env.text(node, "target").ifBlank { "${source.name}.zip" })
        target.parentFile?.mkdirs()
        var entries = 0
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val base = if (source.isDirectory) source else source.parentFile ?: source
            val files = if (source.isDirectory) source.walkTopDown().filter { it.isFile } else sequenceOf(source)
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(base).path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                entries++
            }
        }
        mapOf(
            "done" to true,
            "path" to target.absolutePath,
            "entries" to entries.toDouble(),
            "bytes" to target.length().toDouble(),
        )
    } else {
        val target = resolveFile(env, env.text(node, "target").ifBlank { source.nameWithoutExtension })
        target.mkdirs()
        var entries = 0
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val current = entry
                val out = File(target, current.name)
                // Refuse paths that escape the destination folder (zip-slip).
                require(out.canonicalPath.startsWith(target.canonicalPath)) {
                    "The archive contains an unsafe path: ${current.name}"
                }
                if (current.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    entries++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        mapOf("done" to true, "path" to target.absolutePath, "entries" to entries.toDouble())
    }
}

private suspend fun sqlite(node: ModuleNode, env: RunEnv): Map<String, Any?> = withContext(Dispatchers.IO) {
    val file = resolveFile(env, env.text(node, "database").ifBlank { "flowforge/data.db" })
    file.parentFile?.mkdirs()
    val sql = env.text(node, "sql").trim()
    require(sql.isNotBlank()) { "Enter some SQL to run" }
    val args = env.text(node, "args").lines().map { it.trim() }.filter { it.isNotEmpty() }

    val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    try {
        val isQuery = sql.trimStart().startsWith("SELECT", ignoreCase = true) ||
            sql.trimStart().startsWith("PRAGMA", ignoreCase = true) ||
            sql.trimStart().startsWith("WITH", ignoreCase = true)

        if (!isQuery) {
            db.execSQL(sql, args.toTypedArray())
            val changed = db.rawQuery("SELECT changes()", null).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            return@withContext mapOf(
                "rows" to emptyList<Any?>(),
                "count" to 0.0,
                "changed" to changed.toDouble(),
                "columns" to emptyList<String>(),
            )
        }

        db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<Map<String, Any?>>()
            while (cursor.moveToNext() && rows.size < 1000) {
                rows += columns.indices.associate { index ->
                    columns[index] to when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toDouble()
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        else -> cursor.getString(index)
                    }
                }
            }
            mapOf(
                "rows" to rows,
                "count" to rows.size.toDouble(),
                "changed" to 0.0,
                "columns" to columns,
            )
        }
    } finally {
        runCatching { db.close() }
    }
}
