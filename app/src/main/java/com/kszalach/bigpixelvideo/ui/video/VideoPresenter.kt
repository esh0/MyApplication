package com.kszalach.bigpixelvideo.ui.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.instacart.library.truetime.InvalidNtpServerResponseException
import com.instacart.library.truetime.TrueTime
import com.kszalach.bigpixelvideo.domain.ISTrueTimeSyncService
import com.kszalach.bigpixelvideo.domain.isConnectedToNetwork
import com.kszalach.bigpixelvideo.domain.lastTrueTimesyncStatusPassed
import com.kszalach.bigpixelvideo.framework.BasePresenter
import com.kszalach.bigpixelvideo.model.Schedule
import java.io.File
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

class VideoPresenter(private val context: Context, private val ui: VideoUi) : BasePresenter<VideoUi>() {

    private var schedule: Schedule? = null
    private var deviceId: String? = null
    private var currentVideo = 0
    private val frameRendered = AtomicBoolean(false)
    private var syncTask: TimerTask? = null
    private var updateTask: TimerTask? = null
    private var startDelayTask: TimerTask? = null
    private var trueTimeSyncTask: TimerTask? = null
    private var networkStatusTask : TimerTask? = null
    private var seekTime = 0L
    private var seekStart = 0L

    override fun onResume() {
        super.onResume()
        initSyncTimeTask()
        var realTime = TrueTime.now().time
        var triggerTime = getVideoStart()
        var delay = triggerTime - realTime
        while (delay < 0 || currentVideo == schedule!!.items.size - 1) {
            currentVideo++
            realTime = TrueTime.now().time
            triggerTime = getVideoStart()
            delay = triggerTime - realTime
        }
        if (delay >= 0L && currentVideo > 0) {
            currentVideo--
        }
        startVideo()
    }

    override fun onStop() {
        super.onStop()
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
        val offset = 5 * (col.toInt() - 1)
        val trueTime = TrueTime.now().time
        val timeLeft = trueTime.rem(15 * 60 * 1000) + offset
        if (timeLeft == 0L) {
            context.startService(Intent(context, ISTrueTimeSyncService::class.java))
        } else {
            trueTimeSyncTask = Timer().schedule(timeLeft) { context.startService(Intent(context, ISTrueTimeSyncService::class.java)) }
        }
    }

    private fun startVideo() {
        var realTime = TrueTime.now().time
        var triggerTime = getVideoStart()
        var delay = triggerTime - realTime
        if (delay > 0) {
            startDelayTask = Timer().schedule(delay) { startVideoAndCheckOffset() }
            networkStatusTask = Timer().schedule(0, 5000) {
                ui.setNetworkAvailable(isConnectedToNetwork(context))
            }
            updateTask = Timer().schedule(0, 100) {
                val realTime = TrueTime.now().time
                val triggerTime = getVideoStart()
                val delay = triggerTime - realTime
                ui.setDeviceId(deviceId)
                ui.setTrueTime(realTime)
                ui.setCountdown(delay)
                ui.setTrueTimeSync(lastTrueTimesyncStatusPassed)
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
        val startOffset = TrueTime.now().time - getVideoStart()
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
                val realTime = TrueTime.now().time
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

    fun init(schedule: Schedule, deviceId: String) {
        this.schedule = schedule
        this.deviceId = deviceId
    }
}