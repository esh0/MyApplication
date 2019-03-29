package com.kszalach.bigpixelvideo.domain

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import com.crashlytics.android.Crashlytics
import com.instacart.library.truetime.TrueTimeRx
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

const val TURN_OFF_WIFI_KEY = "turn_off_wifi"
const val GATE_TIME_SECONDS = 19L
const val SYNC_INTERVAL_TIME_MINUTES = 30L
const val SYNC_DEVICES_COUNT = 2
var lastTrueTimeSyncStatusPassed = false
var trueTimeSyncCurrentlyRunning = false
class ISTrueTimeSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private lateinit var wifiManager: WifiManager
    private var networkStateReceiver: BroadcastReceiver? = null
    private var syncTimeSubscription: Disposable? = null
    private var turnOffWiFi = false
    private var cancelTimer: TimerTask? = null

    fun log(text: String) {
        if (TrueTimeRx.isInitialized()) {
            println("TT: ${SimpleDateFormat("HH:HH:ss.SSS").format(TrueTimeRx.now())}: $text")
        } else {
            if (TrueTimeRx.isInitialized()) {
                println("LT: ${SimpleDateFormat("HH:HH:ss.SSS").format(Calendar.getInstance().time)}: $text")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("ISTrueTimeSyncService started $trueTimeSyncCurrentlyRunning")
        if (trueTimeSyncCurrentlyRunning) return Service.START_STICKY
        trueTimeSyncCurrentlyRunning = true
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        turnOffWiFi = intent?.getBooleanExtra(TURN_OFF_WIFI_KEY, false) == true
        cancelTimer = Timer().schedule(GATE_TIME_SECONDS * 1000) {
            log("ISTrueTimeSyncService timer elapsed")
            if (networkStateReceiver != null) {
                unregisterReceiver(networkStateReceiver)
                networkStateReceiver = null
            }
            if (turnOffWiFi) {
                wifiManager.isWifiEnabled = false
            }
            syncTimeSubscription?.dispose()
        }
        if (isConnectedToNetwork(applicationContext)) {
            syncTime()
        } else {
            log("ISTrueTimeSyncService connecting to network...")
            networkStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val info = intent?.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    if (info?.isConnected == true) {
                        syncTime()
                    }
                }
            }
            registerReceiver(networkStateReceiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
            wifiManager.isWifiEnabled = true
        }
        return Service.START_STICKY
    }

    @SuppressLint("CheckResult")
    private fun syncTime() {
        log("ISTrueTimeSyncService sync time started")
        syncTimeSubscription = Single
                .fromCallable {
                    trueTimeSyncCurrentlyRunning = true
                }
                .delay(3, TimeUnit.SECONDS)
                .flatMap<LongArray> {
                    TrueTimeRx.build().initializeNtp("time.google.com")
                }
                .doFinally {
                    cancelTimer?.cancel()
                    if (networkStateReceiver != null) {
                        unregisterReceiver(networkStateReceiver)
                        networkStateReceiver = null
                    }
                    if (turnOffWiFi) {
                        wifiManager.isWifiEnabled = false
                    }
                    trueTimeSyncCurrentlyRunning = false
                }
                .subscribeOn(Schedulers.io())
                .subscribe({
                               lastTrueTimeSyncStatusPassed = true
                               log("ISTrueTimeSyncService sync time finished")
                           },
                           {
                               lastTrueTimeSyncStatusPassed = false
                               log("ISTrueTimeSyncService sync time failed")
                           })
    }

    override fun unregisterReceiver(receiver: BroadcastReceiver?) {
        try {
            super.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Crashlytics.logException(e)
        }
    }
}