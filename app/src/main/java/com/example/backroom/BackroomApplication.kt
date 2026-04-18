package com.example.backroom

import android.app.Application
import android.util.Log

class BackroomApplication : Application() {

    companion object {
        private const val TAG = "BackroomApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Application onCreate")

        // Set up global exception handler to log crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "💥 UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            // Call the default handler to let the app crash normally (so we can see it)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

