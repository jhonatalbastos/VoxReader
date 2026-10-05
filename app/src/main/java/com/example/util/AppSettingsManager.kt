package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ReaderFont
import com.example.model.ReaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackNotificationMode(val title: String, val description: String) {
    MUSIC_PLAYER(
        "Modo Player de Música",
        "Trata a narração como um reprodutor de áudio nativo: controles de mídia, controles na tela de bloqueio e capa do livro em alta resolução"
    ),
    CLASSIC(
        "Modo Clássico",
        "Notificação padrão com o texto e parágrafo sendo lidos atualizados em tempo real na barra de notificações"
    )
}

enum class AppTheme(val displayName: String) {
    SYSTEM("Padrão do Sistema"),
    BOOK_PAPER("Papel Livro"),
    SEPIA("Sépia Clássico"),
    LIGHT("Branco Limpo"),
    DARK("Noturno"),
    AMOLED("Preto Puro")
}

enum class MediaProgressMode(val displayName: String, val description: String) {
    ESTIMATED_TIME(
        "Tempo Estimado (min:seg)",
        "Calcula a duração total do capítulo com base no texto e velocidade da narração"
    ),
    PERCENTAGE(
        "Progresso Percentual (0 a 100%)",
        "Exibe a barra de reprodução e contador em percentual do capítulo"
    )
}

enum class CoverFitMode(val displayName: String, val description: String) {
    AMBIENT_BLUR_FIT(
        "Enquadrar Capa com Fundo Desfocado",
        "Mantém a proporção vertical original do livro sem achatar, com fundo atmosférico suave"
    ),
    CENTER_CROP(
        "Recorte Centralizado 1:1",
        "Preenche o quadrado recortando suavemente as bordas sem distorcer o livro"
    )
}

class AppSettingsManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "voxreader_global_app_settings"
        private const val KEY_PLAYBACK_MODE = "key_playback_mode"
        private const val KEY_AUTO_PLAYBACK_MODE = "key_auto_playback_mode"
        private const val KEY_AUTO_RESUME_LAST = "key_auto_resume_last"
        private const val KEY_APP_THEME = "key_app_theme"
        private const val KEY_DEFAULT_THEME = "key_default_theme"
        private const val KEY_DEFAULT_FONT = "key_default_font"
        private const val KEY_DEFAULT_FONT_SIZE = "key_default_font_size"
        private const val KEY_DEFAULT_LINE_SPACING = "key_default_line_spacing"
        private const val KEY_DEFAULT_VOICE_ENGINE = "key_default_voice_engine"
        private const val KEY_DEFAULT_VOICE_ID = "key_default_voice_id"
        private const val KEY_DEFAULT_VOICE_SPEED = "key_default_voice_speed"
        private const val KEY_DEFAULT_VOICE_PITCH = "key_default_voice_pitch"
        private const val KEY_MEDIA_PROGRESS_MODE = "key_media_progress_mode"
        private const val KEY_COVER_FIT_MODE = "key_cover_fit_mode"
        private const val KEY_NOTIFICATION_SHOW_IMAGES = "key_notification_show_images"
        private const val KEY_NOTIFICATION_IMAGE_DURATION_SEC = "key_notification_image_duration_sec"
        private const val KEY_BACKGROUND_AUDIO_ENABLED = "key_background_audio_enabled"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "key_background_sync_enabled"

        @Volatile
        private var instance: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: AppSettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _playbackMode = MutableStateFlow(loadPlaybackMode())
    val playbackMode: StateFlow<PlaybackNotificationMode> = _playbackMode.asStateFlow()

    private val _autoPlaybackMode = MutableStateFlow(loadAutoPlaybackMode())
    val autoPlaybackMode: StateFlow<PlaybackNotificationMode> = _autoPlaybackMode.asStateFlow()

    private val _autoResumeLast = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RESUME_LAST, true))
    val autoResumeLast: StateFlow<Boolean> = _autoResumeLast.asStateFlow()

    // Global application theme (Sistema, Papel Livro, Sépia, Branco Limpo, Noturno, AMOLED)
    private val _appTheme = MutableStateFlow(loadAppTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    // Default reader preferences
    private val _defaultTheme = MutableStateFlow(loadDefaultTheme())
    val defaultTheme: StateFlow<ReaderTheme> = _defaultTheme.asStateFlow()

    private val _defaultFont = MutableStateFlow(loadDefaultFont())
    val defaultFont: StateFlow<ReaderFont> = _defaultFont.asStateFlow()

    private val _defaultFontSize = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_FONT_SIZE, 18f))
    val defaultFontSize: StateFlow<Float> = _defaultFontSize.asStateFlow()

    private val _defaultLineSpacing = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_LINE_SPACING, 1.5f))
    val defaultLineSpacing: StateFlow<Float> = _defaultLineSpacing.asStateFlow()

    // Default voice preferences
    private val _defaultVoiceEngine = MutableStateFlow(prefs.getString(KEY_DEFAULT_VOICE_ENGINE, "EDGE_TTS") ?: "EDGE_TTS")
    val defaultVoiceEngine: StateFlow<String> = _defaultVoiceEngine.asStateFlow()

    private val _defaultVoiceId = MutableStateFlow(prefs.getString(KEY_DEFAULT_VOICE_ID, "pt-BR-FranciscaNeural") ?: "pt-BR-FranciscaNeural")
    val defaultVoiceId: StateFlow<String> = _defaultVoiceId.asStateFlow()

    private val _defaultVoiceSpeed = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_VOICE_SPEED, 1.0f))
    val defaultVoiceSpeed: StateFlow<Float> = _defaultVoiceSpeed.asStateFlow()

    private val _defaultVoicePitch = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_VOICE_PITCH, 1.0f))
    val defaultVoicePitch: StateFlow<Float> = _defaultVoicePitch.asStateFlow()

    // Progress counter mode for notification (ESTIMATED_TIME is default as requested)
    private val _mediaProgressMode = MutableStateFlow(loadMediaProgressMode())
    val mediaProgressMode: StateFlow<MediaProgressMode> = _mediaProgressMode.asStateFlow()

    // Cover artwork fit mode for square music player (AMBIENT_BLUR_FIT keeps original unflattened proportions)
    private val _coverFitMode = MutableStateFlow(loadCoverFitMode())
    val coverFitMode: StateFlow<CoverFitMode> = _coverFitMode.asStateFlow()

    // Show illustrations in media notification during narration
    private val _notificationShowImages = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_SHOW_IMAGES, true))
    val notificationShowImages: StateFlow<Boolean> = _notificationShowImages.asStateFlow()

    // Duration in seconds to display chapter illustrations in notification (default 8s)
    private val _notificationImageDurationSec = MutableStateFlow(prefs.getInt(KEY_NOTIFICATION_IMAGE_DURATION_SEC, 8))
    val notificationImageDurationSec: StateFlow<Int> = _notificationImageDurationSec.asStateFlow()

    private val _backgroundAudioEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_AUDIO_ENABLED, true))
    val backgroundAudioEnabled: StateFlow<Boolean> = _backgroundAudioEnabled.asStateFlow()

    private val _backgroundSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, true))
    val backgroundSyncEnabled: StateFlow<Boolean> = _backgroundSyncEnabled.asStateFlow()

    private fun loadPlaybackMode(): PlaybackNotificationMode {
        val name = prefs.getString(KEY_PLAYBACK_MODE, PlaybackNotificationMode.MUSIC_PLAYER.name)
        return try {
            PlaybackNotificationMode.valueOf(name ?: PlaybackNotificationMode.MUSIC_PLAYER.name)
        } catch (_: Exception) {
            PlaybackNotificationMode.MUSIC_PLAYER
        }
    }

    private fun loadAutoPlaybackMode(): PlaybackNotificationMode {
        val name = prefs.getString(KEY_AUTO_PLAYBACK_MODE, PlaybackNotificationMode.MUSIC_PLAYER.name)
        return try {
            PlaybackNotificationMode.valueOf(name ?: PlaybackNotificationMode.MUSIC_PLAYER.name)
        } catch (_: Exception) {
            PlaybackNotificationMode.MUSIC_PLAYER
        }
    }

    private fun loadAppTheme(): AppTheme {
        val name = prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.name)
        return try {
            AppTheme.valueOf(name ?: AppTheme.SYSTEM.name)
        } catch (_: Exception) {
            AppTheme.SYSTEM
        }
    }

    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _appTheme.value = theme
    }

    private fun loadDefaultTheme(): ReaderTheme {
        val name = prefs.getString(KEY_DEFAULT_THEME, ReaderTheme.BOOK_PAPER.name)
        return try {
            ReaderTheme.valueOf(name ?: ReaderTheme.BOOK_PAPER.name)
        } catch (_: Exception) {
            ReaderTheme.BOOK_PAPER
        }
    }

    private fun loadDefaultFont(): ReaderFont {
        val name = prefs.getString(KEY_DEFAULT_FONT, ReaderFont.SERIF.name)
        return try {
            ReaderFont.valueOf(name ?: ReaderFont.SERIF.name)
        } catch (_: Exception) {
            ReaderFont.SERIF
        }
    }

    fun setPlaybackMode(mode: PlaybackNotificationMode) {
        prefs.edit().putString(KEY_PLAYBACK_MODE, mode.name).apply()
        _playbackMode.value = mode
    }

    fun setAutoPlaybackMode(mode: PlaybackNotificationMode) {
        prefs.edit().putString(KEY_AUTO_PLAYBACK_MODE, mode.name).apply()
        _autoPlaybackMode.value = mode
    }

    fun setAutoResumeLast(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_LAST, enabled).apply()
        _autoResumeLast.value = enabled
    }

    fun setDefaultTheme(theme: ReaderTheme) {
        prefs.edit().putString(KEY_DEFAULT_THEME, theme.name).apply()
        _defaultTheme.value = theme
    }

    fun setDefaultFont(font: ReaderFont) {
        prefs.edit().putString(KEY_DEFAULT_FONT, font.name).apply()
        _defaultFont.value = font
    }

    fun setDefaultFontSize(sizeSp: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_FONT_SIZE, sizeSp).apply()
        _defaultFontSize.value = sizeSp
    }

    fun setDefaultLineSpacing(spacing: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_LINE_SPACING, spacing).apply()
        _defaultLineSpacing.value = spacing
    }

    fun setDefaultVoiceEngine(engine: String) {
        prefs.edit().putString(KEY_DEFAULT_VOICE_ENGINE, engine).apply()
        _defaultVoiceEngine.value = engine
    }

    fun setDefaultVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_DEFAULT_VOICE_ID, voiceId).apply()
        _defaultVoiceId.value = voiceId
    }

    fun setDefaultVoiceSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_VOICE_SPEED, speed).apply()
        _defaultVoiceSpeed.value = speed
    }

    fun setDefaultVoicePitch(pitch: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_VOICE_PITCH, pitch).apply()
        _defaultVoicePitch.value = pitch
    }

    private fun loadMediaProgressMode(): MediaProgressMode {
        val name = prefs.getString(KEY_MEDIA_PROGRESS_MODE, MediaProgressMode.ESTIMATED_TIME.name)
        return try {
            MediaProgressMode.valueOf(name ?: MediaProgressMode.ESTIMATED_TIME.name)
        } catch (_: Exception) {
            MediaProgressMode.ESTIMATED_TIME
        }
    }

    private fun loadCoverFitMode(): CoverFitMode {
        val name = prefs.getString(KEY_COVER_FIT_MODE, CoverFitMode.AMBIENT_BLUR_FIT.name)
        return try {
            CoverFitMode.valueOf(name ?: CoverFitMode.AMBIENT_BLUR_FIT.name)
        } catch (_: Exception) {
            CoverFitMode.AMBIENT_BLUR_FIT
        }
    }

    fun setMediaProgressMode(mode: MediaProgressMode) {
        prefs.edit().putString(KEY_MEDIA_PROGRESS_MODE, mode.name).apply()
        _mediaProgressMode.value = mode
    }

    fun setCoverFitMode(mode: CoverFitMode) {
        prefs.edit().putString(KEY_COVER_FIT_MODE, mode.name).apply()
        _coverFitMode.value = mode
    }

    fun setNotificationShowImages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_SHOW_IMAGES, enabled).apply()
        _notificationShowImages.value = enabled
    }

    fun setNotificationImageDurationSec(seconds: Int) {
        val clamped = seconds.coerceIn(3, 30)
        prefs.edit().putInt(KEY_NOTIFICATION_IMAGE_DURATION_SEC, clamped).apply()
        _notificationImageDurationSec.value = clamped
    }

    fun isBackgroundAudioEnabled(): Boolean {
        return _backgroundAudioEnabled.value
    }

    fun setBackgroundAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO_ENABLED, enabled).apply()
        _backgroundAudioEnabled.value = enabled
    }

    fun isBackgroundSyncEnabled(): Boolean {
        return _backgroundSyncEnabled.value
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled).apply()
        _backgroundSyncEnabled.value = enabled
    }
}
