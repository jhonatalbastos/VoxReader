package com.example.model

/**
 * Representation of an Edge TTS Voice.
 */
data class EdgeVoice(
    val id: String,
    val displayName: String,
    val language: String,
    val locale: String,
    val gender: String,
    val description: String
)

object VoiceCatalog {
    val VOICES = listOf(
        // Português (Brasil)
        EdgeVoice(
            id = "pt-BR-FranciscaNeural",
            displayName = "Francisca (Natural)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Feminino",
            description = "Voz natural e fluida, ideal para romances e audiolivros"
        ),
        EdgeVoice(
            id = "pt-BR-AntonioNeural",
            displayName = "Antônio (Narrador)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Masculino",
            description = "Tom envolvente e firme, excelente para narrações e clássicos"
        ),
        EdgeVoice(
            id = "pt-BR-ThalitaNeural",
            displayName = "Thalita (Expressiva)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Feminino",
            description = "Voz jovem e alegre, ótima para diálogos e aventura"
        ),
        EdgeVoice(
            id = "pt-BR-DonatoNeural",
            displayName = "Donato (Maduro)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Masculino",
            description = "Tom experiente e pausado, perfeito para ensaios e história"
        ),
        EdgeVoice(
            id = "pt-BR-BrendaNeural",
            displayName = "Brenda (Serena)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Feminino",
            description = "Voz suave e calma para leitura noturna e poesia"
        ),
        EdgeVoice(
            id = "pt-BR-FabioNeural",
            displayName = "Fábio (Casual)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Masculino",
            description = "Voz descontraída e moderna"
        ),
        EdgeVoice(
            id = "pt-BR-GiovannaNeural",
            displayName = "Giovanna (Histórias)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Feminino",
            description = "Cadência rica e expressiva"
        ),
        EdgeVoice(
            id = "pt-BR-NicolauNeural",
            displayName = "Nicolau (Profundo)",
            language = "Português (BR)",
            locale = "pt-BR",
            gender = "Masculino",
            description = "Voz grave e cinematográfica"
        ),

        // Português (Portugal)
        EdgeVoice(
            id = "pt-PT-RaquelNeural",
            displayName = "Raquel (Lisboa)",
            language = "Português (PT)",
            locale = "pt-PT",
            gender = "Feminino",
            description = "Sotaque europeu padrão, claro e elegante"
        ),
        EdgeVoice(
            id = "pt-PT-DuarteNeural",
            displayName = "Duarte (Porto)",
            language = "Português (PT)",
            locale = "pt-PT",
            gender = "Masculino",
            description = "Narração portuguesa masculina sóbria"
        ),

        // Inglês
        EdgeVoice(
            id = "en-US-AriaNeural",
            displayName = "Aria (US Natural)",
            language = "English (US)",
            locale = "en-US",
            gender = "Feminino",
            description = "Highly expressive and versatile American voice"
        ),
        EdgeVoice(
            id = "en-US-GuyNeural",
            displayName = "Guy (US Narrator)",
            language = "English (US)",
            locale = "en-US",
            gender = "Masculino",
            description = "Deep, clear and articulate American male voice"
        ),
        EdgeVoice(
            id = "en-US-JennyNeural",
            displayName = "Jenny (US Clear)",
            language = "English (US)",
            locale = "en-US",
            gender = "Feminino",
            description = "Warm conversational tone"
        ),
        EdgeVoice(
            id = "en-GB-SoniaNeural",
            displayName = "Sonia (British)",
            language = "English (UK)",
            locale = "en-GB",
            gender = "Feminino",
            description = "Sophisticated British received pronunciation"
        ),

        // Espanhol
        EdgeVoice(
            id = "es-ES-ElviraNeural",
            displayName = "Elvira (Castellano)",
            language = "Español (ES)",
            locale = "es-ES",
            gender = "Feminino",
            description = "Voz española clara y articulada"
        ),
        EdgeVoice(
            id = "es-ES-AlvaroNeural",
            displayName = "Álvaro (Castellano)",
            language = "Español (ES)",
            locale = "es-ES",
            gender = "Masculino",
            description = "Voz masculina natural de España"
        ),
        EdgeVoice(
            id = "es-MX-DaliaNeural",
            displayName = "Dalia (México)",
            language = "Español (MX)",
            locale = "es-MX",
            gender = "Feminino",
            description = "Acento latino neutro y suave"
        )
    )

    val DEFAULT_VOICE = VOICES.first()

    fun findById(voiceId: String?): EdgeVoice {
        return VOICES.find { it.id == voiceId } ?: DEFAULT_VOICE
    }
}

enum class ReaderTheme(val displayName: String) {
    BOOK_PAPER("Papel Livro"),
    SEPIA("Sépia Clássico"),
    LIGHT("Branco Limpo"),
    DARK("Noturno"),
    AMOLED("Preto Puro")
}

enum class ReaderFont(val displayName: String) {
    SERIF("Serifada (Livro)"),
    SANS("Sem Serifa (Moderna)"),
    MONO("Monoespaçada (Máquina)")
}

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.5f,
    val theme: ReaderTheme = ReaderTheme.BOOK_PAPER,
    val font: ReaderFont = ReaderFont.SERIF,
    val horizontalMarginDp: Int = 20,
    val isJustified: Boolean = true,
    val hasFirstLineIndent: Boolean = true
)

data class GoogleUser(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String? = null
)

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val syncedBooksCount: Int = 0,
    val errorMessage: String? = null
)

data class CloudBookBackup(
    val id: Long,
    val title: String,
    val author: String,
    val format: String,
    val path: String,
    val progress: Float,
    val currentChapterIndex: Int,
    val currentParagraphIndex: Int,
    val totalChapters: Int,
    val isFavorite: Boolean,
    val voiceId: String,
    val voiceSpeed: Float,
    val voicePitch: Float,
    val voiceEngine: String,
    val readerTheme: String,
    val fontSizeSp: Float,
    val lastReadTimestamp: Long,
    val coverGradientStart: Long,
    val coverGradientEnd: Long
)

data class CloudLibraryBackupPayload(
    val userEmail: String,
    val updatedAt: Long,
    val deviceName: String = "Android Device",
    val books: List<CloudBookBackup>
)
