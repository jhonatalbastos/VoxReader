package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ReaderFont
import com.example.model.ReaderTheme
import com.example.util.AppSettingsManager
import com.example.util.AppTheme
import com.example.util.CoverArtHelper
import com.example.util.PlaybackNotificationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSettingsAndCoverTest {

    @Test
    fun testCoverArtHelperGeneratesValidBitmap() {
        val bitmap = CoverArtHelper.generateStyledArtwork(
            title = "Memórias Póstumas de Brás Cubas",
            author = "Machado de Assis"
        )
        assertNotNull(bitmap)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun testAppSettingsManagerDefaultAndChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = AppSettingsManager.getInstance(context)

        // Verify default mode is MUSIC_PLAYER
        assertEquals(PlaybackNotificationMode.MUSIC_PLAYER, settings.playbackMode.value)

        // Change mode to CLASSIC
        settings.setPlaybackMode(PlaybackNotificationMode.CLASSIC)
        assertEquals(PlaybackNotificationMode.CLASSIC, settings.playbackMode.value)

        // Reset back to MUSIC_PLAYER
        settings.setPlaybackMode(PlaybackNotificationMode.MUSIC_PLAYER)
        assertEquals(PlaybackNotificationMode.MUSIC_PLAYER, settings.playbackMode.value)

        // Test Auto Resume switch
        settings.setAutoResumeLast(true)
        assertTrue(settings.autoResumeLast.value)

        // Test Theme setting
        settings.setDefaultTheme(ReaderTheme.SEPIA)
        assertEquals(ReaderTheme.SEPIA, settings.defaultTheme.value)

        // Test Font setting
        settings.setDefaultFont(ReaderFont.SERIF)
        assertEquals(ReaderFont.SERIF, settings.defaultFont.value)

        // Test App Theme selection aligned with book themes
        settings.setAppTheme(AppTheme.BOOK_PAPER)
        assertEquals(AppTheme.BOOK_PAPER, settings.appTheme.value)

        settings.setAppTheme(AppTheme.SEPIA)
        assertEquals(AppTheme.SEPIA, settings.appTheme.value)

        settings.setAppTheme(AppTheme.AMOLED)
        assertEquals(AppTheme.AMOLED, settings.appTheme.value)

        // Test Default Voice Engine selection (Edge TTS as default, Gemini 3.5 optional)
        assertEquals("EDGE_TTS", settings.defaultVoiceEngine.value)

        settings.setDefaultVoiceEngine("GEMINI_TTS")
        assertEquals("GEMINI_TTS", settings.defaultVoiceEngine.value)

        settings.setDefaultVoiceId("Puck")
        assertEquals("Puck", settings.defaultVoiceId.value)

        // Reset to Edge TTS default
        settings.setDefaultVoiceEngine("EDGE_TTS")
        assertEquals("EDGE_TTS", settings.defaultVoiceEngine.value)
    }
}
