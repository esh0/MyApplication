package com.kszalach.bigpixelvideo.framework

import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

abstract class BaseActivity<T : BasePresenter<*>> : AppCompatActivity(), BaseUi {

    private val flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
        (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.KEEP_SCREEN_ON)
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.KEEP_SCREEN_ON)
    } else {
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.KEEP_SCREEN_ON)
    }

    internal lateinit var presenter: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResId())
        presenter = setupPresenter()
        presenter.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        window.decorView.systemUiVisibility = flags
        super.setContentView(layoutResID)
    }

    override fun onStart() {
        super.onStart()
        presenter.onStart()
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun onPause() {
        super.onPause()
        presenter.onPause()
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    abstract fun setupPresenter(): T

    abstract fun getLayoutResId(): Int
}
