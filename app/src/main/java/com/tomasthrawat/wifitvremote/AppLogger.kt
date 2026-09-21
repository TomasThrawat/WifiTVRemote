package com.tomasthrawat.wifitvremote

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "WifiTVRemote"
    private const val MIME_TYPE = "text/plain"

    @Volatile
    private var initialized = false
    private var writer: BufferedWriter? = null
    private var fileDescription = "unavailable"

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        try {
            val appContext = context.applicationContext
            writer = openWriter(appContext)
            initialized = true
            i(
                "Logger",
                "Logging initialized; file=" + fileDescription +
                    "; api=" + Build.VERSION.SDK_INT +
                    "; model=" + Build.MODEL
            )
        } catch (t: Throwable) {
            initialized = true
            Log.e(TAG, "Failed to initialize persistent logging", t)
        }
    }

    @Synchronized
    private fun openWriter(context: Context): BufferedWriter {
        val fileName = "WifiTVRemote-" +
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date()) +
            ".log"

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException("MediaStore failed to create Downloads log")
            fileDescription = uri.toString()
            val output = context.contentResolver.openOutputStream(uri, "wa")
                ?: throw IllegalStateException("Unable to open Downloads log output stream")
            return BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8), 8192)
        }

        val publicDownloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val target = if (
            Build.VERSION.SDK_INT < 23 ||
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            publicDownloads
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("Unable to create log directory: " + target.absolutePath)
        }
        val file = File(target, fileName)
        fileDescription = file.absolutePath
        return BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8),
            8192
        )
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val fullMessage = buildString {
            append('[')
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
            append("] [")
            append(level)
            append("] [")
            append(tag)
            append("] ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(Log.getStackTraceString(throwable))
            }
        }

        when (level) {
            "V" -> Log.v(TAG, "[" + tag + "] " + message, throwable)
            "D" -> Log.d(TAG, "[" + tag + "] " + message, throwable)
            "I" -> Log.i(TAG, "[" + tag + "] " + message, throwable)
            "W" -> Log.w(TAG, "[" + tag + "] " + message, throwable)
            else -> Log.e(TAG, "[" + tag + "] " + message, throwable)
        }

        try {
            writer?.apply {
                write(fullMessage)
                newLine()
                flush()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Persistent log write failed: " + fileDescription, t)
        }
    }

    fun v(tag: String, message: String) = write("V", tag, message, null)
    fun d(tag: String, message: String) = write("D", tag, message, null)
    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, message, throwable)

    @Synchronized
    fun flush() {
        try {
            writer?.flush()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to flush persistent log", t)
        }
    }

    fun location(): String = fileDescription
}
