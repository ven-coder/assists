package com.ven.assists.web.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseUtils
import android.util.Base64
import com.blankj.utilcode.util.PathUtils
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * SQLite 连接缓存、路径校验与 SQL 执行
 */
object DbDatabaseManager {

    data class ExecResult(
        val rowsAffected: Int,
        val lastInsertRowId: Long,
    )

    private val connections = ConcurrentHashMap<String, SQLiteDatabase>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun resolveDbPath(dbPath: String?, dbName: String?): String {
        return when {
            !dbPath.isNullOrBlank() -> validateDbPath(dbPath)
            !dbName.isNullOrBlank() -> validateDbPath(PathUtils.getInternalAppDbPath(dbName))
            else -> throw IllegalArgumentException("dbPath或dbName必须指定其一")
        }
    }

    fun validateDbPath(path: String): String {
        val file = File(path)
        val normalized = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val allowedBases = listOf(
            PathUtils.getInternalAppDataPath(),
            PathUtils.getExternalAppDataPath(),
        ).map { base ->
            runCatching { File(base).canonicalPath }.getOrDefault(File(base).absolutePath)
        }
        val allowed = allowedBases.any { base ->
            normalized == base || normalized.startsWith("$base${File.separator}")
        }
        if (!allowed) {
            throw IllegalArgumentException("数据库路径不在允许范围内")
        }
        return normalized
    }

    fun exec(dbPath: String, sql: String, bindArgs: Array<String?>?): ExecResult {
        val lock = lockFor(dbPath)
        lock.lock()
        try {
            val db = openDatabase(dbPath)
            if (!bindArgs.isNullOrEmpty()) {
                val trimmed = sql.trimStart().uppercase()
                val stmt = db.compileStatement(sql)
                bindArgs.forEachIndexed { index, value ->
                    val position = index + 1
                    if (value == null) {
                        stmt.bindNull(position)
                    } else {
                        stmt.bindString(position, value)
                    }
                }
                return when {
                    trimmed.startsWith("INSERT") -> {
                        val rowId = stmt.executeInsert()
                        ExecResult(rowsAffected = 1, lastInsertRowId = rowId)
                    }
                    trimmed.startsWith("UPDATE") || trimmed.startsWith("DELETE") -> {
                        val rows = stmt.executeUpdateDelete()
                        ExecResult(rowsAffected = rows, lastInsertRowId = 0L)
                    }
                    else -> {
                        stmt.close()
                        db.execSQL(sql, bindArgs)
                        readExecStats(db)
                    }
                }
            } else {
                db.execSQL(sql)
                return readExecStats(db)
            }
        } finally {
            lock.unlock()
        }
    }

    fun query(dbPath: String, sql: String, bindArgs: Array<String?>?): JsonObject {
        val lock = lockFor(dbPath)
        lock.lock()
        try {
            val db = openDatabase(dbPath)
            val cursor = db.rawQuery(sql, bindArgs)
            return cursorToQueryResult(cursor)
        } finally {
            lock.unlock()
        }
    }

    fun execBatch(dbPath: String, statements: List<String>): JsonObject {
        val lock = lockFor(dbPath)
        lock.lock()
        try {
            val db = openDatabase(dbPath)
            val results = JsonArray()
            db.beginTransaction()
            try {
                statements.forEach { sql ->
                    db.execSQL(sql)
                    val stats = readExecStats(db)
                    results.add(execResultToJson(stats))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return JsonObject().apply {
                addProperty("count", results.size())
                add("results", results)
            }
        } finally {
            lock.unlock()
        }
    }

    fun close(dbPath: String) {
        val lock = lockFor(dbPath)
        lock.lock()
        try {
            connections.remove(dbPath)?.close()
            locks.remove(dbPath)
        } finally {
            lock.unlock()
        }
    }

    private fun lockFor(dbPath: String): ReentrantLock {
        return locks.computeIfAbsent(dbPath) { ReentrantLock() }
    }

    private fun openDatabase(dbPath: String): SQLiteDatabase {
        return connections.computeIfAbsent(dbPath) {
            val file = File(dbPath)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null)
                ?: throw IllegalStateException("打开数据库失败: $dbPath")
        }
    }

    private fun readExecStats(db: SQLiteDatabase): ExecResult {
        val rowsAffected = DatabaseUtils.longForQuery(db, "SELECT changes()", null).toInt()
        val lastInsertRowId = DatabaseUtils.longForQuery(db, "SELECT last_insert_rowid()", null)
        return ExecResult(rowsAffected = rowsAffected, lastInsertRowId = lastInsertRowId)
    }

    private fun execResultToJson(result: ExecResult): JsonObject {
        return JsonObject().apply {
            addProperty("rowsAffected", result.rowsAffected)
            addProperty("lastInsertRowId", result.lastInsertRowId)
        }
    }

    private fun cursorToQueryResult(cursor: Cursor): JsonObject {
        val columns = cursor.columnNames.toList()
        val rows = JsonArray()
        cursor.use {
            while (it.moveToNext()) {
                val row = JsonObject()
                columns.forEachIndexed { index, column ->
                    when (it.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> row.add(column, JsonNull.INSTANCE)
                        Cursor.FIELD_TYPE_INTEGER -> row.addProperty(column, it.getLong(index))
                        Cursor.FIELD_TYPE_FLOAT -> row.addProperty(column, it.getDouble(index))
                        Cursor.FIELD_TYPE_STRING -> row.addProperty(column, it.getString(index))
                        Cursor.FIELD_TYPE_BLOB -> {
                            val blob = it.getBlob(index)
                            val base64 = Base64.encodeToString(blob, Base64.NO_WRAP)
                            row.addProperty(column, base64)
                        }
                    }
                }
                rows.add(row)
            }
        }
        return JsonObject().apply {
            add("columns", JsonArray().apply { columns.forEach { add(it) } })
            add("rows", rows)
            addProperty("rowCount", rows.size())
        }
    }
}
