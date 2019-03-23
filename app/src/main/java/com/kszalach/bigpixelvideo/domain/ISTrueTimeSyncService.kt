package com.kszalach.bigpixelvideo.domain

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.instacart.library.truetime.TrueTimeRx
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

var lastTrueTimesyncStatusPassed = false
class ISTrueTimeSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private var jobCurrentlyRunning = false

    @SuppressLint("CheckResult")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (jobCurrentlyRunning) return Service.START_STICKY
        Single
                .fromCallable {
                    jobCurrentlyRunning = true
                }
                .flatMap<LongArray> {
                    TrueTimeRx.build().initializeNtp("time.google.com")
                }
                .doFinally { jobCurrentlyRunning = false }
                .subscribeOn(Schedulers.io())
                .subscribe({ lastTrueTimesyncStatusPassed = true },
                           { lastTrueTimesyncStatusPassed = false })

        return Service.START_STICKY
    }
}