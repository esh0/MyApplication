package com.kszalach.bigpixelvideo.ui.video

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.SwitchCompat
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.STATE_ENDED
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoListener
import com.google.firebase.analytics.FirebaseAnalytics
import com.instacart.library.truetime.InvalidNtpServerResponseException
import com.instacart.library.truetime.TrueTime
import com.kszalach.bigpixelvideo.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

internal data class ScheduledVideo(val time: Long, val video: File)
class MainActivity : AppCompatActivity(), Player.EventListener, VideoListener {

    private val schedule = ArrayList<ScheduledVideo>(0)

    private val flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
        (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    } else {
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private lateinit var videoView: PlayerView
    private lateinit var submitButton: View
    private val handler = Handler()
    private var currentVideo = 0
    private val calendar = Calendar.getInstance()
    private var updateTask: TimerTask? = null
    private lateinit var syncSwitch: SwitchCompat
    private lateinit var systemTimeView: TextView
    private lateinit var elapsedTimeView: TextView
    private lateinit var trueTimeView: TextView
    private lateinit var diffView: TextView
    private lateinit var timePicker: TimePicker
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var player: SimpleExoPlayer
    private var syncTask: TimerTask? = null
    private var timerTask: TimerTask? = null
    private var frameRendered = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = flags
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        setContentView(R.layout.activity_main)
        player = ExoPlayerFactory.newSimpleInstance(this)
        player.addListener(this)
        player.addVideoListener(this)
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.seekParameters = SeekParameters.EXACT

        systemTimeView = findViewById(R.id.systemtime)
        trueTimeView = findViewById(R.id.truetime)
        elapsedTimeView = findViewById(R.id.elapsedtime)
        diffView = findViewById(R.id.diff)
        syncSwitch = findViewById(R.id.syncSwitch)

        videoView = findViewById(R.id.video_view)
        videoView.player = player
        videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        videoView.hideController()
        videoView.controllerAutoShow = false
        videoView.useController = false

        timePicker = findViewById(R.id.time_picker)
        timePicker.setIs24HourView(true)
        timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            run {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
            }
        }
        submitButton = findViewById<View>(R.id.submit)
        submitButton.setOnClickListener {
            if (initSchedule()) {
                timePicker.visibility = View.GONE
                submitButton.visibility = View.GONE
                syncSwitch.visibility = View.GONE
                startVideo()
            }
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        timerTask = Timer().schedule(0, 50) {
            if (TrueTime.isInitialized()) {
                val trueTimeValue = "True: " + SimpleDateFormat("HH:mm:ss.SSS").format(TrueTime.now())
                val systemTimeValue = "Sys : " + SimpleDateFormat("HH:mm:ss.SSS").format(System.currentTimeMillis())
                runOnUiThread {
                    trueTimeView.text = trueTimeValue
                    systemTimeView.text = systemTimeValue
                }
            }
        }
        syncTime()
    }

    override fun onPause() {
        super.onPause()
        updateTask?.cancel()
        syncTask?.cancel()
        timerTask?.cancel()
    }

    private fun initSchedule(): Boolean {
        val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)!!.path)
        val files = moviesDir.listFiles()
        if (files.isNotEmpty()) {
            files.sort()
            files.forEachIndexed { index, file ->
                schedule.add(ScheduledVideo(calendar.timeInMillis, file))
                val name = file.nameWithoutExtension
                val time = name.split("_")[1]
                calendar.add(Calendar.SECOND, time.toInt())
            }
            return true
        }
        return false
    }

    private fun syncTime() {
        val job = GlobalScope.launch {
            do {
                try {
                    TrueTime.build().initialize()
                } catch (ignored: InvalidNtpServerResponseException) {
                } catch (ignored: SocketTimeoutException) {
                }
            } while (!TrueTime.isInitialized())
        }
        job.invokeOnCompletion {
            runOnUiThread {
                timePicker.visibility = View.VISIBLE
                submitButton.visibility = View.VISIBLE
                syncSwitch.visibility = View.VISIBLE
            }
        }
    }

    private fun startVideo() {
        val realTime = TrueTime.now().time
        val triggerTime = schedule[currentVideo].time
        val delay = triggerTime - realTime
        if (delay > 0) {
            handler.postDelayed({ startVideoAndCheckOffset() }, delay)
        } else {
            startVideoAndCheckOffset()
        }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        /*val state = when (playbackState) {
            STATE_IDLE -> "STATE_IDLE"
            STATE_BUFFERING -> "STATE_BUFFERING"
            STATE_READY -> "STATE_READY"
            STATE_ENDED -> "STATE_ENDED"
            else       -> "unknown"
        }*/
        when (playbackState) {
            //STATE_READY ->
            //Log.i("BigPlayer", String.format(Locale.ENGLISH, "onPlayerStateChanged(%s) at %d for %d video  should be at %d", state, TrueTime.now().time, currentVideo, schedule[currentVideo].time))
            STATE_ENDED -> {
                syncTask?.cancel()
                updateTask?.cancel()
                frameRendered.set(false)
                //Log.i("BigPlayer", String.format(Locale.ENGLISH, "onPlayerStateChanged(%s) at %d for %d video  should be at %d", state, TrueTime.now().time, currentVideo, schedule[currentVideo].time + player.duration))
                if (++currentVideo < schedule.size) {
                    startVideo()
                } else {
                    finish()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        syncTask?.cancel()
        updateTask?.cancel()
        player.stop()
    }

    private fun startVideoAndCheckOffset() {
        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "BigVideoPlayer"))
        val videoSource = ExtractorMediaSource.Factory(dataSourceFactory).setExtractorsFactory(DefaultExtractorsFactory())
                .createMediaSource(Uri.fromFile(schedule[currentVideo].video))
        player.prepare(videoSource)
        val startOffset = TrueTime.now().time - schedule[currentVideo].time
        if (startOffset > 0) {
            player.playWhenReady = true
        } else {
            handler.postDelayed({ player.playWhenReady = true }, -1 * startOffset)
        }
    }

    var seekTime = 0L
    var seekStart = 0L
    override fun onRenderedFirstFrame() {
        if (frameRendered.compareAndSet(false, true)) {
            //Log.i("BigPlayer", String.format(Locale.ENGLISH, "onRenderedFirstFrame at %d for %d video should be at %d", TrueTime.now().time, currentVideo, schedule[currentVideo].time))
            if (syncSwitch.isChecked) {
                syncTask = Timer().schedule(0, 5000) {
                    runOnUiThread {
                        val realTime = TrueTime.now().time
                        val elapsedTime = player.currentPosition
                        val diff = realTime - schedule[currentVideo].time - elapsedTime
                        if (Math.abs(diff) > 100L) {
                            val seekTo = if (diff > 0) elapsedTime + diff + seekTime else elapsedTime - diff + seekTime
                            //player.playWhenReady = false
                            //Log.i("BigPlayer", String.format(Locale.ENGLISH, "position before seek: %d, will seek to %s", elapsedTime, seekTo))
                            seekStart = System.currentTimeMillis()
                            player.seekTo(seekTo)
                        }
                    }
                }
            } else {
                val realTime = TrueTime.now().time
                val elapsedTime = player.currentPosition
                val diff = realTime - schedule[currentVideo].time - elapsedTime
                if (diff != 0L) {
                    player.seekTo(if (diff > 0) elapsedTime + diff else elapsedTime - diff)
                }
            }
            updateTask = Timer().schedule(0, 100) {
                runOnUiThread {
                    val realTime = TrueTime.now().time
                    val elapsedTime = player.currentPosition
                    val diff = realTime - schedule[currentVideo].time - elapsedTime
                    diffView.text = "Diff: ${diff}ms"
                    elapsedTimeView.text = "Pos : ${elapsedTime}ms"
                }
            }
        }
    }

    override fun onSeekProcessed() {
        //player.playWhenReady = true
        seekStart = System.currentTimeMillis() - seekStart
        if (seekTime == 0L) {
            seekTime = seekStart
        } else {
            seekTime = (seekTime + seekStart) / 2
        }
        //Log.i("BigPlayer", String.format(Locale.ENGLISH, "position after seek: %d, seek time: %d", player.currentPosition, seekTime))
    }
}