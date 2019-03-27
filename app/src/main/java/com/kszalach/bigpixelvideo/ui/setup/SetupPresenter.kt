package com.kszalach.bigpixelvideo.ui.setup

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.instacart.library.truetime.TrueTimeRx
import com.kszalach.bigpixelvideo.domain.isConnectedToNetwork
import com.kszalach.bigpixelvideo.domain.lastTrueTimeSyncStatusPassed
import com.kszalach.bigpixelvideo.framework.BasePresenter
import com.kszalach.bigpixelvideo.model.RemoteConfig
import com.kszalach.bigpixelvideo.model.Schedule
import com.kszalach.bigpixelvideo.model.ScheduleItem
import com.kszalach.bigpixelvideo.model.VideoItem
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.net.InetAddress
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

internal const val FTP_SERVER = "serwer1889938.home.pl"
internal const val USER = "telefony@serwer1889938.home.pl"
internal const val PASS = "Tele33Tele!"
internal const val MAX_TRUE_TIME_SYNC_TRIES = 5
const val DEVICE_ID_PREF_KEY = "device_id_key"
const val CONFIG_PREF_KEY = "config_key"

class SetupPresenter(private val context: Context, private val ui: SetupUi) : BasePresenter<SetupUi>() {

    private var trueTimeSyncErrorCount = 0
    private var schedule: Schedule? = null
    private var remoteConfig: RemoteConfig? = null
    private var videos: ArrayList<VideoItem>? = null
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private var updateTask: TimerTask? = null

    override fun onResume() {
        super.onResume()
        val storedDeviceId = prefs.getString(DEVICE_ID_PREF_KEY, null)
        if (!storedDeviceId.isNullOrEmpty() && ui.getDeviceId().isNullOrEmpty()) {
            ui.setDeviceId(storedDeviceId)
        }
        remoteConfig = Gson().fromJson(prefs.getString(CONFIG_PREF_KEY, null), RemoteConfig::class.java)
        updateTask = Timer().schedule(0, 100) {
            if (TrueTimeRx.isInitialized()) {
                ui.setTrueTime(TrueTimeRx.now().time)
            }
            ui.setTrueTimeSync(lastTrueTimeSyncStatusPassed)
            ui.setNetworkAvailable(isConnectedToNetwork(context))
        }
    }

    override fun onPause() {
        super.onPause()
        updateTask?.cancel()
    }

    private fun invalidateInputs(): Boolean {
        if (ui.getDeviceId().isNullOrEmpty()) {
            return false
        }
        return true
    }

    @SuppressLint("CheckResult")
    private fun syncTime() {
        if (lastTrueTimeSyncStatusPassed) {
            startParsingSchedule()
        } else {
            ui.showSyncTimeProgress()
            ui.showLoading(true)
            ui.enableInputs(false)
            TrueTimeRx.build().initializeNtp("time.google.com")
                    .subscribeOn(Schedulers.io())
                    .subscribe({
                                   lastTrueTimeSyncStatusPassed = true
                                   ui.setTrueTime(TrueTimeRx.now().time)
                                   ui.setTrueTimeSync(lastTrueTimeSyncStatusPassed)
                                   startParsingSchedule()
                               },
                               {
                                   startParsingSchedule()
                                   lastTrueTimeSyncStatusPassed = false
                                   ui.setTrueTimeSync(lastTrueTimeSyncStatusPassed)
                               })
        }
    }

    private fun startParsingSchedule() {
        val deviceId = ui.getDeviceId()!!
        prefs.edit().putString(DEVICE_ID_PREF_KEY, deviceId).apply()
        ui.showLoading(true)
        ui.enableInputs(false)
        val parseScheduleJob = parseSchedule()
        parseScheduleJob.invokeOnCompletion {
            onParseFinished(deviceId)
        }
    }

    fun onStartClicked() {
        ui.hideProgress()
        ui.askForPermissions()
    }

    private fun onParseFinished(deviceId: String) {
        if (schedule?.items?.isNullOrEmpty() == true) {
            ui.showLoading(false)
            ui.enableInputs(true)
            ui.showWrongScheduleError()
        } else {
            /*if (ui.isQuickDemoChecked()) {
                val now = Calendar.getInstance()
                now.add(Calendar.MINUTE, 1)
                schedule?.items?.forEach { scheduleItem: ScheduleItem ->
                    scheduleItem.startHour = now.get(Calendar.HOUR_OF_DAY)
                    scheduleItem.startMinute = now.get(Calendar.MINUTE)
                    now.add(Calendar.SECOND, scheduleItem.length)
                }
            }*/
            if (ui.isRewriteFilesChecked() || remoteConfig == null) {
                val downloadConfigJob = downloadConfig()
                downloadConfigJob.invokeOnCompletion {
                    onDownloadConfigCompleted(deviceId)
                }
            } else {
                startDownload(deviceId)
            }
        }
    }

    private fun onDownloadConfigCompleted(deviceId: String) {
        if (remoteConfig != null) {
            if (remoteConfig!!.url.isNullOrEmpty() || remoteConfig!!.user.isNullOrEmpty() || remoteConfig!!.pass.isNullOrEmpty()) {
                ui.showShowWrongConfigError()
                ui.showLoading(false)
                ui.enableInputs(true)
            } else {
                prefs.edit().putString(CONFIG_PREF_KEY, Gson().toJson(remoteConfig)).apply()
                startDownload(deviceId)
            }
        } else {
            ui.showShowNoConfigError()
            ui.showLoading(false)
            ui.enableInputs(true)
        }
    }

    private fun downloadConfig(): Job {
        ui.showDownloadConfig()
        return GlobalScope.launch {
            var ftpClient: FTPClient? = null
            try {
                ftpClient = FTPClient()
                ftpClient.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))
                ftpClient.connect(InetAddress.getByName(FTP_SERVER))
                ftpClient.login(USER, PASS)
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.ASCII_FILE_TYPE)
                ftpClient.bufferSize = 4096
                var outputStream: BufferedInputStream? = null
                var inputStream: InputStream? = null
                try {
                    val remoteFile = "config.json"
                    if (ftpClient.listFiles(remoteFile).isNotEmpty()) {
                        inputStream = ftpClient.retrieveFileStream(remoteFile)
                        outputStream = BufferedInputStream(inputStream)
                        val buffer = ByteArray(1024)
                        var fileContent: String? = null
                        while (true) {
                            val readCount = outputStream.read(buffer)
                            if (readCount == -1) {
                                break
                            }
                            fileContent = String(buffer, 0, readCount)
                        }
                        if (!fileContent.isNullOrEmpty()) {
                            remoteConfig = Gson().fromJson(fileContent, RemoteConfig::class.java)
                        }
                        outputStream.close()
                        inputStream?.close()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                } finally {
                    outputStream?.close()
                    inputStream?.close()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                try {
                    ftpClient?.logout()
                } catch (e: IOException) {

                }
                try {
                    ftpClient?.disconnect()
                } catch (e: IOException) {

                }
            }
        }
    }

    private fun startDownload(deviceId: String) {
        remoteConfig!!.deviceId = deviceId
        videos = ArrayList(0)
        schedule?.items?.forEach { scheduleItem: ScheduleItem ->
            val videoItem = VideoItem(scheduleItem.video)
            if (!videos!!.contains(videoItem)) {
                if (ui.isRewriteFilesChecked() || !File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), scheduleItem.video).exists()) {
                    videos!!.add(videoItem)
                }
            }
        }
        if (videos!!.isNotEmpty()) {
            val downloadJob = downloadVideos(deviceId, videos!!)
            downloadJob.invokeOnCompletion {
                onDownloadVideosFinished()
            }
        } else {
            ui.showStartingVideo()
            ui.runVideoActivity(schedule!!, remoteConfig!!)
        }
    }

    private fun onDownloadVideosFinished() {
        var allDownloaded = true
        videos!!.forEach {
            if (!it.downloaded) {
                allDownloaded = false
                return@forEach
            }
        }
        if (allDownloaded) {
            ui.showStartingVideo()
            ui.runVideoActivity(schedule!!, remoteConfig!!)
        } else {
            ui.showLoading(false)
            ui.enableInputs(true)
            ui.showNotAllDownloadedError(videos!!)
        }
    }

    private fun downloadVideos(deviceId: String, videos: List<VideoItem>): Job {
        val downloadProgressTask = Timer().schedule(0, 500) {
            ui.showDownloadProgress(videos)
        }
        return GlobalScope.launch {
            var ftpClient: FTPClient? = null
            try {
                ftpClient = FTPClient()
                ftpClient.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))
                ftpClient.connect(InetAddress.getByName(remoteConfig!!.url))
                ftpClient.login(remoteConfig!!.user, remoteConfig!!.pass)
                if (!remoteConfig!!.directory.isNullOrEmpty()) {
                    ftpClient.changeWorkingDirectory(remoteConfig!!.directory)
                }
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.bufferSize = 4096
                videos.forEach {
                    var outputStream: BufferedOutputStream? = null
                    var inputStream: InputStream? = null
                    try {
                        val remoteFile = "$deviceId/${it.video}"
                        if (ftpClient.listFiles(remoteFile).isNotEmpty()) {
                            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), it.video)
                            outputStream = BufferedOutputStream(FileOutputStream(file))
                            inputStream = ftpClient.retrieveFileStream(remoteFile)
                            val bytesArray = ByteArray(4096)
                            var totalBytes = 0
                            while (true) {
                                val bytesRead = inputStream?.read(bytesArray) ?: -1
                                if (bytesRead == -1) {
                                    break
                                }
                                totalBytes += bytesRead
                                outputStream.write(bytesArray, 0, bytesRead)
                            }
                            outputStream.flush()
                            outputStream.close()
                            it.downloaded = ftpClient.completePendingCommand() && totalBytes != 0
                            inputStream?.close()
                        } else {
                            it.downloaded = false
                        }
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    } finally {
                        outputStream?.close()
                        inputStream?.close()
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            } finally {
                downloadProgressTask.cancel()
                try {
                    ftpClient?.logout()
                } catch (e: IOException) {

                }
                try {
                    ftpClient?.disconnect()
                } catch (e: IOException) {

                }
            }
        }
    }

    private fun parseSchedule(): Job {
        return GlobalScope.launch {
            var json: String? = null
            try {
                val inputStream = context.assets.open(if (ui.isQuickDemoChecked()) "schedule_demo.json" else "schedule.json")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                json = String(buffer, Charset.defaultCharset())
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
            if (!json.isNullOrEmpty()) {
                schedule = Gson().fromJson(json, Schedule::class.java)
            }
        }
    }

    fun onPermissionsDenied() {
        ui.showNoPermissionsError()
    }

    fun onPermissionsAccepted() {
        ui.silent()
        ui.showLoading(true)
        ui.enableInputs(false)
        if (!isConnectedToNetwork(context)) {
            ui.showNoNetworkError()
        }

        ui.showDeviceIdError(false)
        if (invalidateInputs()) {
            syncTime()
        } else {
            ui.showLoading(false)
            ui.enableInputs(true)
            ui.showDeviceIdError(true)
        }
    }
}