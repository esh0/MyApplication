package com.kszalach.bigpixelvideo.model

import java.io.Serializable

data class ScheduleItem(val position: Int, var startHour: Int, var startMinute: Int, val video: String, val length: Int) : Serializable