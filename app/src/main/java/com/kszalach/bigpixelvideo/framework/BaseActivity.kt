package com.kszalach.bigpixelvideo.framework

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

abstract class BaseActivity<T : BasePresenter<*>> : AppCompatActivity(), BaseUi {

    internal lateinit var presenter: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResId())
        presenter = setupPresenter()
        presenter.onCreate(savedInstanceState)
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
