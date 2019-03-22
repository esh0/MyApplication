package com.kszalach.bigpixelvideo.ui.video

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.video.VideoListener
import com.kszalach.bigpixelvideo.R
import com.kszalach.bigpixelvideo.framework.BaseActivity
import com.kszalach.bigpixelvideo.model.Schedule

const val SCHEDULE_KEY = "schedule"

class VideoActivity : BaseActivity<VideoPresenter>(), VideoUi, Player.EventListener, VideoListener {

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
    private lateinit var player: SimpleExoPlayer
    private lateinit var videoView: PlayerView
    private lateinit var diffView: TextView
    private lateinit var elapsedView: TextView
    private lateinit var videoIdText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayerFactory.newSimpleInstance(this)
        player.addListener(this)
        player.addVideoListener(this)
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.seekParameters = SeekParameters.EXACT

        videoView = findViewById(R.id.video_view)
        diffView = findViewById(R.id.diff)
        elapsedView = findViewById(R.id.elapsedtime)
        videoIdText = findViewById(R.id.video_id)

        videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        videoView.hideController()
        videoView.controllerAutoShow = false
        videoView.useController = false
        videoView.player = player

        presenter.initWithSchedule(intent.getSerializableExtra(SCHEDULE_KEY) as Schedule)
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

    override fun setContentView(layoutResID: Int) {
        window.decorView.systemUiVisibility = flags
        super.setContentView(layoutResID)
    }

    override fun setDiffText(text: String) {
        runOnUiThread { diffView.text = text }
    }

    override fun setElapsedText(text: String) {
        runOnUiThread { elapsedView.text = text }
    }

    override fun setVideoId(text: String) {
        runOnUiThread { videoIdText.text = text }
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
