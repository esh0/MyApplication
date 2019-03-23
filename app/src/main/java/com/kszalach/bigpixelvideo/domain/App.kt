package com.kszalach.bigpixelvideo.domain

import android.app.Application
import android.content.Intent

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        startService(Intent(this, ISTrueTimeSyncService::class.java))
    }
}