package com.kszalach.bigpixelvideo.ui.video

import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.kszalach.bigpixelvideo.framework.BaseUi

interface VideoUi : BaseUi {
    fun setTrueTime(time: Long?)
    fun setCountdown(time: Long?)
    fun stop()
    fun prepare(videoSource: ExtractorMediaSource?)
    fun play()
    fun getCurrentPosition(): Long
    fun seekTo(seekTo: Long)
    fun setDeviceId(deviceId: String?)
    fun setNetworkAvailable(networkConnected: Boolean)
    fun setTrueTimeSync(synced: Boolean)
    fun hideControls()
    fun notifyManualSyncTime()
    fun getCurrentLength(): Long
    fun showCurrentVideo(currentVideo: Int)
}