package com.kszalach.bigpixelvideo.model

import java.io.Serializable
import java.util.*

data class ScheduleItem(val position: Int, var startHour: Int, var startMinute: Int, val video: String, val length: Int, var startTime: Calendar?) : Serializable