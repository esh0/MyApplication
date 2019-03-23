package com.kszalach.bigpixelvideo.ui.video

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoListener
import com.instacart.library.truetime.TrueTime
import com.kszalach.bigpixelvideo.R
import com.kszalach.bigpixelvideo.framework.BaseActivity
import com.kszalach.bigpixelvideo.model.Schedule
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

const val SCHEDULE_KEY = "schedule"
const val DEVICE_KEY = "device"
const val MAX_SYNC_TIMEWINDOW = 2000
const val DEVICES_COUNT = 264

class VideoActivity : BaseActivity<VideoPresenter>(), VideoUi, Player.EventListener, VideoListener {

    private lateinit var player: SimpleExoPlayer
    private lateinit var videoView: PlayerView
    private lateinit var trueTimeView: TextView
    private lateinit var countdownView: TextView
    private lateinit var deviceIdView: TextView
    private lateinit var internetIndicatorView: TextView
    private lateinit var trueTimeIndicatorView: TextView
    private lateinit var demoIndictorView: TextView
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun hideControls() {
        runOnUiThread {
            trueTimeView.visibility = View.GONE
            countdownView.visibility = View.GONE
            deviceIdView.visibility = View.GONE
            internetIndicatorView.visibility = View.GONE
            trueTimeIndicatorView.visibility = View.GONE
            demoIndictorView.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayerFactory.newSimpleInstance(this)
        player.addListener(this)
        player.addVideoListener(this)
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.seekParameters = SeekParameters.EXACT

        videoView = findViewById(R.id.video_view)
        trueTimeView = findViewById(R.id.true_time)
        countdownView = findViewById(R.id.countdown)
        deviceIdView = findViewById(R.id.device_id)
        internetIndicatorView = findViewById(R.id.internet_indicator)
        trueTimeIndicatorView = findViewById(R.id.truetime_indicator)
        demoIndictorView = findViewById(R.id.demo_indicator)

        videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        videoView.hideController()
        videoView.controllerAutoShow = false
        videoView.useController = false
        videoView.player = player

        presenter.init(intent.getSerializableExtra(SCHEDULE_KEY) as Schedule, intent.getStringExtra(DEVICE_KEY))
    }

    override fun setNetworkAvailable(networkConnected: Boolean) {
        runOnUiThread { internetIndicatorView.setBackgroundResource(if (networkConnected) R.color.green_light else R.color.red_light) }
    }

    override fun setTrueTimeSync(synced: Boolean) {
        runOnUiThread { trueTimeIndicatorView.setBackgroundResource(if (synced) R.color.green_light else R.color.red_light) }
    }

    override fun onResume() {
        super.onResume()
        window.attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    }

    override fun setDeviceId(deviceId: String?) {
        runOnUiThread {
            deviceIdView.text = deviceId
        }
    }

    override fun stop() {
        runOnUiThread {
            player.stop()
        }
    }

    override fun prepare(videoSource: ExtractorMediaSource?) {
        runOnUiThread { player.prepare(videoSource) }
    }

    override fun showLoading(isLoading: Boolean) {
    }

    override fun setupPresenter(): VideoPresenter {
        return VideoPresenter(this, this)
    }

    override fun play() {
        runOnUiThread {
            player.playWhenReady = true
        }
    }

    override fun getCurrentPosition(): Long {
        return player.currentPosition
    }

    override fun seekTo(seekTo: Long) {
        runOnUiThread { player.seekTo(seekTo) }
    }

    override fun getLayoutResId(): Int {
        return R.layout.activity_video
    }

    override fun setTrueTime(time: Long?) {
        runOnUiThread { trueTimeView.text = if (time != null) timeFormat.format(time) else null }
    }

    override fun setCountdown(time: Long?) {
        val timeString = if (time != null) String.format("T-%02d:%02d:%02d:%03d",
                                                         TimeUnit.MILLISECONDS.toHours(time),
                                                         TimeUnit.MILLISECONDS.toMinutes(time) -
                                                                 TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                                                         TimeUnit.MILLISECONDS.toSeconds(time) -
                                                                 TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)),
                                                         TimeUnit.MILLISECONDS.toMillis(time) -
                                                                 TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(time)))
        else null

        runOnUiThread {
            countdownView.text = timeString
        }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        presenter.onPlayerStateChanged(playWhenReady, playbackState)
    }

    override fun onRenderedFirstFrame() {
        presenter.onRenderedFirstFrame()
    }

    override fun onSeekProcessed() {
        presenter.onSeekProcessed()
    }
}
