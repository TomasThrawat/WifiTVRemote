package com.tomasthrawat.wifitvremote

import android.app.Application

class WifiTvRemoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(
                "Crash",
                "Uncaught exception on thread=" + thread.name,
                throwable
            )
            AppLogger.flush()
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
