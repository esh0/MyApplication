package com.kszalach.bigpixelvideo.ui.video

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.api.mockito.PowerMockito.mock
import org.powermock.api.mockito.PowerMockito.mockStatic
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@PrepareForTest(System::class, Calendar::class)
@RunWith(PowerMockRunner::class)
class VideoPresenterTest {

    @Before
    fun setUp() {
        mockStatic(System::class.java)
        mockStatic(Calendar::class.java, Mockito.CALLS_REAL_METHODS)
    }

    @Test
    fun testSeek() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 0
        PowerMockito.`when`(Calendar.getInstance()).thenReturn(calendar)
        assertEquals(0, Calendar.getInstance().timeInMillis)

        val ui = mock(VideoUi::class.java)
        val presenter = VideoPresenter(mock(Context::class.java), ui)
    }
}