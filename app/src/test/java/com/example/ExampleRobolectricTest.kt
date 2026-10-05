package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.VoiceCatalog
import com.example.parser.SampleLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VoxReader", appName)
  }

  @Test
  fun `voice catalog contains natural Portuguese voices`() {
    val defaultVoice = VoiceCatalog.findById("pt-BR-FranciscaNeural")
    assertNotNull(defaultVoice)
    assertEquals("pt-BR", defaultVoice.locale)
    assertTrue(VoiceCatalog.VOICES.any { it.id == "pt-BR-AntonioNeural" })
  }

  @Test
  fun `sample library provides books with chapters`() {
    val sampleBooks = SampleLibrary.getSampleBooks()
    assertTrue(sampleBooks.isNotEmpty())
    val domCasmurro = sampleBooks.first()
    assertEquals("Dom Casmurro", domCasmurro.first.title)
    assertTrue(domCasmurro.second.isNotEmpty())
  }
}
