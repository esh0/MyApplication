package com.kszalach.bigpixelvideo.ui.setup

import com.kszalach.bigpixelvideo.framework.BaseUi
import com.kszalach.bigpixelvideo.model.RemoteConfig
import com.kszalach.bigpixelvideo.model.Schedule
import com.kszalach.bigpixelvideo.model.VideoItem

interface SetupUi : BaseUi {
    fun getDeviceId(): String?
    fun showDeviceIdError(isError: Boolean)
    fun runVideoActivity(schedule: Schedule, deviceId: RemoteConfig)
    fun showWrongScheduleError()
    fun showNotAllDownloadedError(videos: ArrayList<VideoItem>)
    fun showShowNoVideosError()
    fun enableInputs(enabled: Boolean)
    fun showNotSyncedError()
    fun showNoPermissionsError()
    fun showNoNetworkError()
    fun showDownloadProgress(videos: List<VideoItem>)
    fun showSyncTimeProgress()
    fun hideProgress()
    fun setDeviceId(storedDeviceId: String)
    fun isRewriteFilesChecked(): Boolean
    fun isQuickDemoChecked(): Boolean
    fun showDownloadConfig()
    fun showShowNoConfigError()
    fun showShowWrongConfigError()
    fun setNetworkAvailable(networkConnected: Boolean)
    fun setTrueTimeSync(synced: Boolean)
    fun silent()
    fun setTrueTime(time: Long?)
    fun showStartingVideo()
    fun askForPermissions()
}