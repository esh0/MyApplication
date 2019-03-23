package com.kszalach.bigpixelvideo.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.widget.ContentLoadingProgressBar
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.kszalach.bigpixelvideo.R
import com.kszalach.bigpixelvideo.framework.BaseActivity
import com.kszalach.bigpixelvideo.model.Schedule
import com.kszalach.bigpixelvideo.ui.video.SCHEDULE_KEY
import com.kszalach.bigpixelvideo.ui.video.VideoActivity

class SetupActivity : BaseActivity<SetupPresenter>(), SetupUi {

    private lateinit var deviceIdView: TextView
    private lateinit var startView: View
    private lateinit var progressView: ContentLoadingProgressBar

    override fun showNoNetworkError() {
        runOnUiThread { Toast.makeText(this, getString(R.string.no_network_error), Toast.LENGTH_LONG).show() }
    }

    override fun showWrongScheduleError() {
        runOnUiThread { Toast.makeText(this, getString(R.string.wrong_schedule_error), Toast.LENGTH_LONG).show() }
    }

    override fun showNotAllDownloadedError() {
        runOnUiThread { Toast.makeText(this, getString(R.string.not_all_downloaded_error), Toast.LENGTH_LONG).show() }
    }

    override fun showShowNoVideosError() {
        runOnUiThread { Toast.makeText(this, getString(R.string.no_videos_error), Toast.LENGTH_LONG).show() }
    }

    override fun showDeviceIdError(isError: Boolean) {
        deviceIdView.error = if (isError) getString(R.string.field_missing) else null
    }

    override fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            progressView.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun getDeviceId(): String? {
        return deviceIdView.text.toString()
    }

    override fun setupPresenter(): SetupPresenter {
        return SetupPresenter(this, this)
    }

    override fun getLayoutResId(): Int {
        return R.layout.activity_setup
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceIdView = findViewById(R.id.device_id)
        startView = findViewById(R.id.start)
        progressView = findViewById(R.id.progress)

        startView.setOnClickListener { presenter.onStartClicked() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.WRITE_SETTINGS), 1)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.WRITE_SETTINGS), 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED
            || grantResults[2] != PackageManager.PERMISSION_GRANTED || grantResults[3] != PackageManager.PERMISSION_GRANTED) {
            presenter.onPermissionsDenied()
        } else {
            presenter.onPermissionsAccepted()
        }
    }

    override fun showNoPermissionsError() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.missing_permisions), Toast.LENGTH_LONG).show()
        }
    }

    override fun enableInputs(enabled: Boolean) {
        runOnUiThread {
            deviceIdView.isEnabled = enabled
            startView.isEnabled = enabled
        }
    }

    override fun showNotSyncedError() {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.not_synced_error), Toast.LENGTH_LONG).show()
        }
    }

    override fun runVideoActivity(schedule: Schedule) {
        val intent = Intent(this, VideoActivity::class.java)
        intent.putExtra(SCHEDULE_KEY, schedule)
        startActivity(intent)
        finish()
    }
}
