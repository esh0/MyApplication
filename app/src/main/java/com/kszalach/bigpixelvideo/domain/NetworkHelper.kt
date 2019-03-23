package com.kszalach.bigpixelvideo.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

fun isConnectedToNetwork(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetworkInfo
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return activeNetwork != null && activeNetwork.isConnected
                && NetworkCapabilities(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)).hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return activeNetwork != null && activeNetwork.isConnected
}
