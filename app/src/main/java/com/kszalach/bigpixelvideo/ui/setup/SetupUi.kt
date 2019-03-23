package com.kszalach.bigpixelvideo.ui.setup

import com.kszalach.bigpixelvideo.framework.BaseUi
import com.kszalach.bigpixelvideo.model.Schedule

interface SetupUi : BaseUi {
    fun getDeviceId(): String?
    fun showDeviceIdError(isError: Boolean)
    fun runVideoActivity(schedule: Schedule)
    fun showWrongScheduleError()
    fun showNotAllDownloadedError()
    fun showShowNoVideosError()
    fun enableInputs(enabled: Boolean)
    fun showNotSyncedError()
    fun showNoPermissionsError()
    fun showNoNetworkError()
}