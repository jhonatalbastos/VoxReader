package com.example.tts.piper

object PiperVoiceCatalog {

    /**
     * Vozes brasileiras pré-inseridas compatíveis com Piper TTS.
     */
    val DEFAULT_BRAZILIAN_VOICES = listOf(
        PiperVoice(
            id = "pt_BR-faber-medium",
            name = "Faber",
            languageCode = "pt_BR",
            languageDisplayName = "Português (Brasil)",
            gender = "Masculino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/faber/medium/pt_BR-faber-medium.onnx.json",
            sizeMb = 55.4f,
            description = "Voz brasileira de alta qualidade, firme e expressiva. Ideal para audiolivros e romances."
        ),
        PiperVoice(
            id = "pt_BR-edresson-low",
            name = "Edresson",
            languageCode = "pt_BR",
            languageDisplayName = "Português (Brasil)",
            gender = "Masculino",
            quality = "Leve (16kHz)",
            sampleRate = 16000,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/edresson/low/pt_BR-edresson-low.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/edresson/low/pt_BR-edresson-low.onnx.json",
            sizeMb = 15.2f,
            description = "Voz brasileira compacta e rápida, excelente performance em qualquer celular."
        ),
        PiperVoice(
            id = "pt_BR-cadu-medium",
            name = "Cadu",
            languageCode = "pt_BR",
            languageDisplayName = "Português (Brasil)",
            gender = "Masculino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/cadu/medium/pt_BR-cadu-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_BR/cadu/medium/pt_BR-cadu-medium.onnx.json",
            sizeMb = 54.8f,
            description = "Voz brasileira masculina com boa cadência e tom amigável."
        )
    )

    /**
     * Catálogo estendido para busca e adição de novas vozes internacionais e regionais.
     */
    val SEARCHABLE_CATALOG = DEFAULT_BRAZILIAN_VOICES + listOf(
        PiperVoice(
            id = "pt_PT-tugao-medium",
            name = "Tugão",
            languageCode = "pt_PT",
            languageDisplayName = "Português (Portugal)",
            gender = "Masculino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_PT/tug%C3%A3o/medium/pt_PT-tug%C3%A3o-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/pt/pt_PT/tug%C3%A3o/medium/pt_PT-tug%C3%A3o-medium.onnx.json",
            sizeMb = 56.1f,
            description = "Voz portuguesa europeia autêntica e expressiva."
        ),
        PiperVoice(
            id = "en_US-lessac-medium",
            name = "Lessac",
            languageCode = "en_US",
            languageDisplayName = "Inglês (EUA)",
            gender = "Feminino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
            sizeMb = 54.2f,
            description = "Voz em inglês americano clara, acadêmica e estável."
        ),
        PiperVoice(
            id = "en_US-amy-medium",
            name = "Amy",
            languageCode = "en_US",
            languageDisplayName = "Inglês (EUA)",
            gender = "Feminino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json",
            sizeMb = 54.0f,
            description = "Voz feminina natural em inglês americano."
        ),
        PiperVoice(
            id = "es_ES-davefx-medium",
            name = "Davefx",
            languageCode = "es_ES",
            languageDisplayName = "Espanhol (Espanha)",
            gender = "Masculino",
            quality = "Média (22kHz)",
            sampleRate = 22050,
            modelUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/davefx/medium/es_ES-davefx-medium.onnx",
            configUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/davefx/medium/es_ES-davefx-medium.onnx.json",
            sizeMb = 55.0f,
            description = "Voz castelhana masculina tradicional e sonora."
        )
    )

    val DEFAULT_VOICE = DEFAULT_BRAZILIAN_VOICES.first()
}
