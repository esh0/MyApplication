package com.kszalach.myapplication

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TimePicker
import android.widget.VideoView
import com.instacart.library.truetime.InvalidNtpServerResponseException
import com.instacart.library.truetime.TrueTime
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

internal data class ScheduledVideo(val time: Long, val video: Int)
class MainActivity : AppCompatActivity(), MediaPlayer.OnErrorListener {

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return true
    }

    private val schedule = ArrayList<ScheduledVideo>(0)

    private val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

    private lateinit var videoView: VideoView
    private lateinit var submitButton: View
    private val handler = Handler()
    private var currentVideo = 0
    private val calendar = Calendar.getInstance()

    private lateinit var timePicker: TimePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = flags
        setContentView(R.layout.activity_main)
        videoView = findViewById(R.id.video_view)
        videoView.setOnInfoListener { mp, what, extra ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                val startOffset = TrueTime.now().time - schedule[currentVideo].time
                videoView.seekTo(startOffset.toInt())
                Log.i("BigPlayer", String.format(Locale.ENGLISH, "Video %d on screen with %dms delay", currentVideo, startOffset))
                return@setOnInfoListener true
            }
            false
        }
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
            timePicker.visibility = View.GONE
            submitButton.visibility = View.GONE
            initSchedule()
            startVideoOrSync()
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        syncTime()
    }

    private fun initSchedule() {
        calendar.timeInMillis = calendar.timeInMillis + (System.currentTimeMillis() - TrueTime.now().time)
        schedule.add(ScheduledVideo(calendar.timeInMillis, R.raw.video0))
        calendar.add(Calendar.MINUTE, 1)
        schedule.add(ScheduledVideo(calendar.timeInMillis, R.raw.video2))
        calendar.add(Calendar.MINUTE, 1)
        schedule.add(ScheduledVideo(calendar.timeInMillis, R.raw.video3))
    }

    private fun syncTime() {
        val job = GlobalScope.launch {
            do {
                try {
                    TrueTime.build().initialize()
                } catch (ignored: InvalidNtpServerResponseException) {
                }
            } while (!TrueTime.isInitialized())
        }
        job.invokeOnCompletion {
            runOnUiThread {
                timePicker.visibility = View.VISIBLE
                submitButton.visibility = View.VISIBLE
            }
        }
    }

    private fun startVideoOrSync() {
        if (videoView.isPlaying) {
            syncVideo()
        } else {
            triggerVideoPlay()
        }
    }

    private fun triggerVideoPlay() {
        videoView.setVideoPath("android.resource://$packageName/${schedule[currentVideo].video}")
        val realTime = TrueTime.now().time
        val triggerTime = schedule[currentVideo].time
        val delay = triggerTime - realTime
        Log.i("BigPlayer", String.format(Locale.ENGLISH, "Video %d delayed in %dms", currentVideo, delay))
        if (delay > 0) {
            handler.postDelayed({ startVideoAndCheckOffset() }, delay)
        } else {
            startVideoAndCheckOffset()
        }
    }

    private fun startVideoAndCheckOffset() {
        val startOffset = TrueTime.now().time - schedule[currentVideo].time
        Log.i("BigPlayer", String.format(Locale.ENGLISH, "Started with %dms offset", startOffset))
        if (startOffset > 0) {
            videoView.start()
        } else {
            handler.postDelayed({ videoView.start() }, -1 * startOffset)
        }

        if (schedule.size - 1 > currentVideo) {
            videoView.setOnCompletionListener {
                currentVideo++
                triggerVideoPlay()
            }
        }
    }

    private fun syncVideo() {
        videoView.currentPosition
    }
}
