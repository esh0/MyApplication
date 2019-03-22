package com.kszalach.bigpixelvideo.ui.setup

import android.content.Context
import android.net.ConnectivityManager
import android.os.Environment
import com.google.gson.Gson
import com.instacart.library.truetime.InvalidNtpServerResponseException
import com.instacart.library.truetime.TrueTime
import com.kszalach.bigpixelvideo.framework.BasePresenter
import com.kszalach.bigpixelvideo.model.Schedule
import com.kszalach.bigpixelvideo.model.ScheduleItem
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.*

internal const val FTP_SERVER = "serwer1889938.home.pl"
internal const val USER = "telefony@serwer1889938.home.pl"
internal const val PASS = "Tele33Tele!"

class SetupPresenter(private val context: Context, private val ui: SetupUi) : BasePresenter<SetupUi>() {

    private fun invalidateInputs(): Boolean {
        ui.showDeviceIdError(false)
        if (ui.getDeviceId().isNullOrEmpty()) {
            ui.showDeviceIdError(true)
            return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        syncTime()
    }

    private fun syncTime() {
        ui.showLoading(true)
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
            ui.showLoading(false)
        }
    }

    private var schedule: Schedule? = null
    fun onStartClicked() {
        if (invalidateInputs() && isConnectedToNetwork(context)) {
            ui.showLoading(true)
            val parseScheduleJob = parseSchedule()
            parseScheduleJob.invokeOnCompletion {
                if (schedule?.items?.isNullOrEmpty() == true) {
                    ui.showLoading(false)
                    ui.showWrongScheduleError()
                } else {
                    val videos = mutableListOf<String>()

                    //FAKE TIME
                    val now = Calendar.getInstance()
                    now.set(Calendar.SECOND, 0)
                    now.set(Calendar.MILLISECOND, 0)
                    now.add(Calendar.MINUTE, 5)
                    schedule?.items?.forEach { scheduleItem: ScheduleItem ->
                        if (!videos.contains(scheduleItem.video)) {
                            videos.add(scheduleItem.video)
                        }
                        //FAKE TIME
                        scheduleItem.startHour = now.get(Calendar.HOUR_OF_DAY)
                        scheduleItem.startMinute = now.get(Calendar.MINUTE)
                        now.add(Calendar.MINUTE, scheduleItem.length / 60)
                    }
                    if (videos.isNotEmpty()) {
                        val downloadJob = download(ui.getDeviceId()!!, videos)
                        downloadJob.invokeOnCompletion {
                            ui.showLoading(false)
                            if (allDownloaded) {
                                ui.runVideoActivity(schedule!!)
                            } else {
                                ui.showNotAllDownloadedError()
                            }
                        }
                    } else {
                        ui.showShowNoVideosError()
                    }
                }
            }
        }
    }

    private var allDownloaded = false
    private fun download(deviceId: String, videos: List<String>): Job {
        return GlobalScope.launch {
            var ftpClient: FTPClient? = null
            try {
                ftpClient = FTPClient()
                ftpClient.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))
                ftpClient.connect(InetAddress.getByName(FTP_SERVER))
                ftpClient.login(USER, PASS)
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                ftpClient.bufferSize = 4096
                allDownloaded = true
                videos.forEach {
                    var outputStream: BufferedOutputStream? = null
                    var inputStream: InputStream? = null
                    try {
                        val remoteFile = "${deviceId.toUpperCase()}/$it"
                        if (ftpClient.listFiles(remoteFile).isNotEmpty()) {
                            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), it)
                            //if (!file.exists()) {
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
                            if (!ftpClient.completePendingCommand() || totalBytes == 0) {
                                allDownloaded = false
                            }
                            inputStream?.close()
                            //}
                        } else {
                            allDownloaded = false
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
                val inputStream = context.assets.open("schedule_org.json")
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

    private fun isConnectedToNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
}