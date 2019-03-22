package com.kszalach.bigpixelvideo.framework

import android.os.Bundle
import android.support.annotation.CallSuper

abstract class BasePresenter<T : BaseUi> {
    @CallSuper
    open fun onCreate(savedInstanceState: Bundle?) {
    }

    @CallSuper
    open fun onStart() {
    }

    @CallSuper
    open fun onResume() {
    }

    @CallSuper
    open fun onPause() {
    }

    @CallSuper
    open fun onStop() {
    }

    @CallSuper
    open fun onDestroy() {
    }
}