package com.kszalach.bigpixelvideo.ui.video

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.instacart.library.truetime.TrueTimeRx
import com.kszalach.bigpixelvideo.domain.*
import com.kszalach.bigpixelvideo.framework.BasePresenter
import com.kszalach.bigpixelvideo.model.RemoteConfig
import com.kszalach.bigpixelvideo.model.Schedule
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

class VideoPresenter(private val context: Context, private val ui: VideoUi) : BasePresenter<VideoUi>() {

    private val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    private var schedule: Schedule? = null
    private var config: RemoteConfig? = null
    private var deviceId: String? = null
    private var currentVideo = 0
    private val frameRendered = AtomicBoolean(false)
    private var syncTask: TimerTask? = null
    private var updateTask: TimerTask? = null
    private var startDelayTask: TimerTask? = null
    private var trueTimeSyncTask: TimerTask? = null
    private var networkStatusTask: TimerTask? = null
    private var seekTime = 0L
    private var seekStart = 0L
    private var nextSyncTimeStamp = 0L

    override fun onResume() {
        super.onResume()
        wifiManager.isWifiEnabled = false

        initSyncTimeTask()
        var realTime = getTime().time
        var triggerTime = getVideoStart()
        var delay = triggerTime - realTime
        while (delay < 0 || currentVideo == schedule!!.items.size - 1) {
            currentVideo++
            realTime = getTime().time
            triggerTime = getVideoStart()
            delay = triggerTime - realTime
        }
        if (delay >= 0L && currentVideo > 0) {
            currentVideo--
        }
        startVideo()
    }

    override fun onPause() {
        super.onPause()
        context.stopService(Intent(context, ISTrueTimeSyncService::class.java))
        wifiManager.isWifiEnabled = true
        syncTask?.cancel()
        updateTask?.cancel()
        startDelayTask?.cancel()
        trueTimeSyncTask?.cancel()
        networkStatusTask?.cancel()
        ui.stop()
    }

    private fun initSyncTimeTask() {
        trueTimeSyncTask?.cancel()
        val row = deviceId!![0]
        val col = (deviceId!!.replace(row.toString(), ""))
        val now = Calendar.getInstance()
        now.time = getTime()
        val future = Calendar.getInstance()
        future.time = now.time
        future.set(Calendar.SECOND, 0)
        future.set(Calendar.MILLISECOND, 0)
        future.set(Calendar.MINUTE, (now.get(Calendar.MINUTE) - (now.get(Calendar.MINUTE).rem(config!!.syncIntervalMinutes))).toInt())
        val offset = config!!.syncIntervalMinutes * 60 + config!!.syncGateSeconds * ((col.toInt() - 1) / config!!.syncDeviceCount)
        future.add(Calendar.SECOND, offset.toInt())

        val timeLeft = future.timeInMillis - now.timeInMillis
        if (timeLeft == 0L) {
            syncTime()
        } else {
            nextSyncTimeStamp = future.timeInMillis
            trueTimeSyncTask = Timer().schedule(timeLeft) {
                syncTime()
                initSyncTimeTask()
            }
        }
    }

    private fun syncTime() {
        val intent = Intent(context, ISTrueTimeSyncService::class.java)
        intent.putExtra(TURN_OFF_WIFI_KEY, true)
        context.startService(intent)
    }

    private fun startVideo() {
        var realTime = getTime().time
        var triggerTime = getVideoStart()
        var delay = triggerTime - realTime
        if (delay > 0) {
            startDelayTask = Timer().schedule(delay) { startVideoAndCheckOffset() }
            networkStatusTask = Timer().schedule(0, 5000) {
                ui.setNetworkAvailable(isConnectedToNetwork(context))
            }
            updateTask = Timer().schedule(0, 100) {
                val realTime = getTime().time
                val triggerTime = getVideoStart()
                val delay = triggerTime - realTime
                ui.setDeviceId(deviceId)
                ui.setTrueTime(realTime)
                ui.setCountdown(delay)
                ui.setTrueTimeSync(lastTrueTimeSyncStatusPassed)
            }
        } else if (currentVideo != schedule!!.items.size - 1) {
            startVideoAndCheckOffset()
        }
    }

    private fun getVideoStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, schedule!!.items[currentVideo].startHour)
        calendar.set(Calendar.MINUTE, schedule!!.items[currentVideo].startMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            syncTask?.cancel()
            updateTask?.cancel()
            frameRendered.set(false)
            if (++currentVideo < schedule!!.items.size) {
                startVideo()
            } else {
                //finish()
            }
        }
    }

    private fun startVideoAndCheckOffset() {
        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, "BigVideoPlayer"))
        val videoSource = ExtractorMediaSource.Factory(dataSourceFactory).setExtractorsFactory(DefaultExtractorsFactory())
                .createMediaSource(Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), schedule!!.items[currentVideo].video)))
        ui.prepare(videoSource)
        val startOffset = getTime().time - getVideoStart()
        if (startOffset > 0) {
            ui.play()
        } else {
            startDelayTask = Timer().schedule(-1 * startOffset) { ui.play() }
        }
    }

    fun onRenderedFirstFrame() {
        updateTask?.cancel()
        ui.hideControls()

        if (frameRendered.compareAndSet(false, true)) {
            syncTask = Timer().schedule(0, 5000) {
                val realTime = getTime().time
                val elapsedTime = ui.getCurrentPosition()
                val diff = realTime - getVideoStart() - elapsedTime
                if (Math.abs(diff) > 100L) {
                    val seekTo = if (diff > 0) elapsedTime + diff + seekTime else elapsedTime - diff + seekTime
                    seekStart = System.currentTimeMillis()
                    ui.seekTo(seekTo)
                }
            }
        }
    }

    fun onSeekProcessed() {
        seekStart = System.currentTimeMillis() - seekStart
        if (seekTime == 0L) {
            seekTime = seekStart
        } else {
            seekTime = (seekTime + seekStart) / 2
        }
    }

    fun init(schedule: Schedule, config: RemoteConfig) {
        this.schedule = schedule
        this.deviceId = config.deviceId!!
        if (config.syncDeviceCount == 0) {
            config.syncDeviceCount = SYNC_DEVICES_COUNT
        }
        if (config.syncGateSeconds == 0L) {
            config.syncGateSeconds = GATE_TIME_SECONDS
        }
        if (config.syncIntervalMinutes == 0L) {
            config.syncIntervalMinutes = SYNC_INTERVAL_TIME_MINUTES
        }
        this.config = config
    }

    fun onVideoClicked() {
        if (nextSyncTimeStamp - getTime().time > 2 * GATE_TIME_SECONDS * 1000) {
            syncTime()
            ui.notifyManualSyncTime()
        }
    }

    private fun getTime(): Date {
        return if (TrueTimeRx.isInitialized()) TrueTimeRx.now() else Calendar.getInstance().time
    }
}