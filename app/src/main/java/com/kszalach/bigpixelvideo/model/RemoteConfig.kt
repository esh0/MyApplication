package com.kszalach.bigpixelvideo.model

import java.io.Serializable

data class RemoteConfig(val url: String?,
                        val directory: String?,
                        val user: String?,
                        val pass: String?,
                        var syncIntervalMinutes: Long,
                        var syncDeviceCount: Int,
                        var syncGateSeconds: Long,
                        var deviceId: String?,
                        var ntpServer: String?) : Serializable