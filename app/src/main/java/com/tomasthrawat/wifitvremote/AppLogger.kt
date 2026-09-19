package com.tomasthrawat.wifitvremote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "WifiTVRemote"
    private const val FILE_NAME = "WifiTVRemote-Diagnostics.log"
    private const val MIME = "text/plain"
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile private var downloadUri: Uri? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (initialized) return
            appContext = context.applicationContext
            initialized = true
            write("INFO", "LOGGER", "LOGGER_INITIALIZED destination=Download/" + FILE_NAME, null)
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                e("UNCAUGHT", "thread=" + thread.name, error)
            }
        }
        i("APP", "started sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL + " manufacturer=" + Build.MANUFACTURER)
    }

    fun i(tag: String, message: String) = write("INFO", tag, message, null)
    fun d(tag: String, message: String) = write("DEBUG", tag, message, null)
    fun w(tag: String, message: String) = write("WARN", tag, message, null)
    fun e(tag: String, message: String, error: Throwable? = null) = write("ERROR", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val separator = System.lineSeparator()
        val safe = message.replace(separator, " ")
        val line = buildString {
            append(formatter.format(Date()))
            append(" | ").append(level)
            append(" | ").append(tag)
            append(" | ").append(safe)
            if (error != null) {
                append(" | exception=").append(error.javaClass.name)
                append(" | message=").append(error.message?.replace(separator, " "))
                append(" | stack=").append(error.stackTraceToString().replace(separator, "↵"))
            }
            append(separator)
        }
        try {
            Log.println(
                when (level) {
                    "ERROR" -> Log.ERROR
                    "WARN" -> Log.WARN
                    "DEBUG" -> Log.DEBUG
                    else -> Log.INFO
                },
                TAG,
                tag + " | " + safe
            )
            synchronized(lock) { appendRaw(line) }
        } catch (t: Throwable) {
            Log.e(TAG, "persistent log write failed", t)
        }
    }

    private fun appendRaw(text: String) {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            var uri = downloadUri
            if (uri == null) {
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID),
                    MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                    arrayOf(FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        uri = Uri.withAppendedPath(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            cursor.getLong(0).toString()
                        )
                    }
                }
                if (uri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, MIME)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
                    }
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }
                downloadUri = uri
            }
            val target = uri ?: return
            resolver.openOutputStream(target, "wa")?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: throw IllegalStateException("MediaStore returned null output stream")
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot create Download directory")
            FileOutputStream(File(dir, FILE_NAME), true).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }
}
