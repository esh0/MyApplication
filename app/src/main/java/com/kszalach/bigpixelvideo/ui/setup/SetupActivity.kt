package com.kszalach.bigpixelvideo.ui.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.widget.ContentLoadingProgressBar
import android.support.v7.widget.SwitchCompat
import android.view.View
import android.widget.TextView
import com.crashlytics.android.Crashlytics
import com.kszalach.bigpixelvideo.BuildConfig
import com.kszalach.bigpixelvideo.R
import com.kszalach.bigpixelvideo.framework.BaseActivity
import com.kszalach.bigpixelvideo.model.RemoteConfig
import com.kszalach.bigpixelvideo.model.Schedule
import com.kszalach.bigpixelvideo.model.VideoItem
import com.kszalach.bigpixelvideo.ui.video.CONFIG_KEY
import com.kszalach.bigpixelvideo.ui.video.SCHEDULE_KEY
import com.kszalach.bigpixelvideo.ui.video.VideoActivity
import java.text.SimpleDateFormat

class SetupActivity : BaseActivity<SetupPresenter>(), SetupUi {

    private lateinit var deviceIdView: TextView
    private lateinit var progressLabelView: TextView
    private lateinit var internetIndicatorView: TextView
    private lateinit var startView: View
    private lateinit var progressView: ContentLoadingProgressBar
    private lateinit var rewriteFilesView: SwitchCompat
    private lateinit var quickDemoView: SwitchCompat
    private lateinit var trueTimeView: TextView
    private lateinit var trueTimeIndicatorView: TextView
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    override fun setSyncError(it: Throwable?, remoteConfig: RemoteConfig) {
        it?.let { runOnUiThread { progressLabelView.text = remoteConfig.ntpServer + "\n" + it.localizedMessage } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceIdView = findViewById(R.id.device_id)
        startView = findViewById(R.id.start)
        progressView = findViewById(R.id.progress)
        progressLabelView = findViewById(R.id.progress_label)
        rewriteFilesView = findViewById(R.id.rewrite_files)
        quickDemoView = findViewById(R.id.quick_demo)
        internetIndicatorView = findViewById(R.id.internet_indicator)
        trueTimeView = findViewById(R.id.true_time)
        trueTimeIndicatorView = findViewById(R.id.truetime_indicator)

        startView.setOnClickListener { presenter.onStartClicked() }

        findViewById<TextView>(R.id.version).text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    override fun askForPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CHANGE_WIFI_STATE,
                                                                Manifest.permission.MODIFY_AUDIO_SETTINGS), 1)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CHANGE_WIFI_STATE,
                                                                Manifest.permission.MODIFY_AUDIO_SETTINGS), 1)
            }
        } else {
            presenter.onPermissionsAccepted()
        }
    }

    override fun showSyncTimeProgress() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.sync_time_progress)
        }
    }

    override fun showDownloadProgress(videos: List<VideoItem>) {
        var label = ""
        videos.forEach {
            label += it.video + "\t" + (if (it.downloaded) getString(R.string.downloaded) else getString(R.string.not_downloaded)) + "\n"
        }
        label = String.format(getString(R.string.download_progress), label)
        runOnUiThread {
            progressLabelView.text = label
        }
    }

    override fun hideProgress() {
        runOnUiThread { progressLabelView.text = null }
    }

    override fun showNoNetworkError() {
        runOnUiThread { progressLabelView.text = getString(R.string.no_network_error) }
    }

    override fun showWrongScheduleError() {
        runOnUiThread { progressLabelView.text = getString(R.string.wrong_schedule_error) }
    }

    override fun showStartingVideo() {
        runOnUiThread { progressLabelView.text = getString(R.string.starting_video) }
    }

    override fun showNotAllDownloadedError(videos: ArrayList<VideoItem>) {
        var label = ""
        videos.forEach {
            if (!it.downloaded) {
                label += it.video + "\n"
            }
        }
        label = String.format(getString(R.string.not_all_downloaded_error), label)
        runOnUiThread {
            progressLabelView.text = label
        }
    }

    override fun showShowNoVideosError() {
        runOnUiThread { progressLabelView.text = getString(R.string.no_videos_error) }
    }

    override fun showDeviceIdError(isError: Boolean) {
        deviceIdView.error = if (isError) getString(R.string.field_missing) else null
    }

    override fun showShowWrongConfigError() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.download_config_wrong_error)
        }
    }

    override fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            progressView.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getDeviceId(): String? {
        return deviceIdView.text.toString()
    }

    override fun showDownloadConfig() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.download_config)
        }
    }

    override fun showShowNoConfigError() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.download_config_error)
        }
    }

    override fun setupPresenter(): SetupPresenter {
        return SetupPresenter(this, this)
    }

    override fun setDeviceId(storedDeviceId: String) {
        deviceIdView.text = storedDeviceId
    }

    override fun isRewriteFilesChecked(): Boolean {
        return rewriteFilesView.isChecked
    }

    override fun getLayoutResId(): Int {
        return R.layout.activity_setup
    }

    override fun silent() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: SecurityException) {
            Crashlytics.logException(e)
        }
    }

    override fun setNetworkAvailable(networkConnected: Boolean) {
        runOnUiThread { internetIndicatorView.setBackgroundResource(if (networkConnected) R.color.green_light else R.color.red_light) }
    }

    override fun setTrueTimeSync(synced: Boolean) {
        runOnUiThread { trueTimeIndicatorView.setBackgroundResource(if (synced) R.color.green_light else R.color.red_light) }
    }

    override fun setTrueTime(time: Long?) {
        runOnUiThread { trueTimeView.text = if (time != null) timeFormat.format(time) else null }
    }

    override fun onResume() {
        super.onResume()
        window.attributes.screenBrightness = 0.8f
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        var allAccepted = true
        grantResults.forEach { allAccepted = allAccepted && it == PackageManager.PERMISSION_GRANTED }
        if (allAccepted) {
            presenter.onPermissionsAccepted()
        } else {
            presenter.onPermissionsDenied()
        }
    }

    override fun showNoPermissionsError() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.missing_permisions)
        }
    }

    override fun enableInputs(enabled: Boolean) {
        runOnUiThread {
            deviceIdView.isEnabled = enabled
            startView.isEnabled = enabled
            rewriteFilesView.isEnabled = enabled
            quickDemoView.isEnabled = enabled
        }
    }

    override fun isQuickDemoChecked(): Boolean {
        return quickDemoView.isChecked
    }

    override fun showNotSyncedError() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.not_synced_error)
        }
    }

    override fun runVideoActivity(schedule: Schedule, config: RemoteConfig) {
        val intent = Intent(this, VideoActivity::class.java)
        intent.putExtra(SCHEDULE_KEY, schedule)
        intent.putExtra(CONFIG_KEY, config)
        startActivity(intent)
        finish()
    }

    override fun canSilent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            return true
        }
        return false
    }

    override fun showParsingSchedule() {
        runOnUiThread {
            progressLabelView.text = getString(R.string.parsing_schedule)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun askSilent() {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        startActivityForResult(intent, 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            presenter.onCanSilent(notificationManager.isNotificationPolicyAccessGranted)
        }
    }
}
