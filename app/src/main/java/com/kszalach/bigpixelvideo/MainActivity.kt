package com.kszalach.bigpixelvideo

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TimePicker
import android.widget.VideoView
import com.instacart.library.truetime.InvalidNtpServerResponseException
import com.instacart.library.truetime.TrueTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.net.SocketTimeoutException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule



internal data class ScheduledVideo(val time: Long, val video: String)
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
    private var updateTask: TimerTask? = null
    private lateinit var timePicker: TimePicker
    private var meanDiff = 0L
    private var samplesCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = flags
        setContentView(R.layout.activity_main)
        videoView = findViewById(R.id.video_view)
        videoView.setOnInfoListener { mp, what, extra ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                meanDiff = 0L
                samplesCount = 0
                updateTask?.cancel()
                val startOffset = TrueTime.now().time - schedule[currentVideo].time
                updateTask = Timer("SettingUp", false).schedule(5000, 5000) {
                    if (videoView.isPlaying) {
                        val expectedPosition = TrueTime.now().time - schedule[currentVideo].time
                        val currentPosition = videoView.currentPosition
                        val diff = expectedPosition - currentPosition
                        samplesCount++
                        if (meanDiff == 0L) {
                            meanDiff = diff
                        } else {
                            meanDiff += diff
                        }
                        val realDiff = meanDiff / samplesCount
                        videoView.seekTo(expectedPosition.toInt() + realDiff.toInt())
                        Log.i("BigPlayer", String.format(Locale.ENGLISH, "Video on position %d, should be %d, diff is %d", currentPosition, expectedPosition, diff))
                    }
                }

                Log.i("BigPlayer", String.format(Locale.ENGLISH, "Video %d on screen with %dms delay at real time %d", currentVideo, startOffset, TrueTime.now().time))
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onPause() {
        super.onPause()
        updateTask?.cancel()
    }

    private fun initSchedule() {
        calendar.timeInMillis = calendar.timeInMillis
        schedule.add(ScheduledVideo(calendar.timeInMillis, "1.mp4"))
        calendar.add(Calendar.MINUTE, 1)
        schedule.add(ScheduledVideo(calendar.timeInMillis, "2.mp4"))
        calendar.add(Calendar.MINUTE, 1)
        schedule.add(ScheduledVideo(calendar.timeInMillis, "3.mp4"))
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
        //videoView.setVideoPath("android.resource://$packageName/${schedule[currentVideo].video}")
        videoView.setVideoURI(Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)!!.path, schedule[currentVideo].video)))
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
        if (startOffset > 0) {
            videoView.start()
            Log.i("BigPlayer", String.format(Locale.ENGLISH, "Started with %dms offset at real time %d", startOffset, TrueTime.now().time))
        } else {
            handler.postDelayed({ videoView.start() }, -1 * startOffset)
            Log.i("BigPlayer", String.format(Locale.ENGLISH, "Delayed for %dms offset at real time %d", -1 * startOffset, TrueTime.now().time))
        }

        videoView.setOnCompletionListener {
            updateTask?.cancel()
            currentVideo++
            if (currentVideo < schedule.size) {
                triggerVideoPlay()
            } else {
                finish()
            }
        }
    }

    private fun syncVideo() {
        videoView.currentPosition
    }
}
