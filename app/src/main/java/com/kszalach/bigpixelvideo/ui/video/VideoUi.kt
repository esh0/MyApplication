package com.kszalach.bigpixelvideo.ui.video

import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.kszalach.bigpixelvideo.framework.BaseUi

interface VideoUi : BaseUi {
    fun setDiffText(text: String)
    fun setElapsedText(text: String)
    fun setVideoId(text: String)
    fun stop()
    fun prepare(videoSource: ExtractorMediaSource?)
    fun play()
    fun getCurrentPosition(): Long
    fun seekTo(seekTo: Long)
}