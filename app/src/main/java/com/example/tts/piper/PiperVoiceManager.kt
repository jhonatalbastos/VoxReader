package com.example.tts.piper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "PiperVoiceManager"
private const val PREFS_NAME = "piper_voice_prefs"
private const val KEY_CUSTOM_VOICES = "custom_piper_voices"

class PiperVoiceManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PiperVoiceManager? = null

        fun getInstance(context: Context): PiperVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: PiperVoiceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val voicesDir: File by lazy {
        val dir = File(context.filesDir, "piper_voices")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val _installedVoiceIds = MutableStateFlow<Set<String>>(emptySet())
    val installedVoiceIds: StateFlow<Set<String>> = _installedVoiceIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _customVoices = MutableStateFlow<List<PiperVoice>>(emptyList())
    val customVoices: StateFlow<List<PiperVoice>> = _customVoices.asStateFlow()

    init {
        loadCustomVoices()
        refreshInstalledVoices()
    }

    fun refreshInstalledVoices() {
        val installed = mutableSetOf<String>()
        val all = getAllVoices()
        for (voice in all) {
            val modelFile = getModelFile(voice.id)
            val configFile = getConfigFile(voice.id)
            if (modelFile.exists() && modelFile.length() > 1024 * 100 && configFile.exists()) {
                installed.add(voice.id)
            }
        }
        _installedVoiceIds.value = installed
    }

    fun isVoiceInstalled(voiceId: String): Boolean {
        return _installedVoiceIds.value.contains(voiceId) ||
                (getModelFile(voiceId).exists() && getConfigFile(voiceId).exists())
    }

    fun getModelFile(voiceId: String): File {
        val voiceFolder = File(voicesDir, voiceId)
        if (!voiceFolder.exists()) voiceFolder.mkdirs()
        return File(voiceFolder, "$voiceId.onnx")
    }

    fun getConfigFile(voiceId: String): File {
        val voiceFolder = File(voicesDir, voiceId)
        if (!voiceFolder.exists()) voiceFolder.mkdirs()
        return File(voiceFolder, "$voiceId.onnx.json")
    }

    fun getAllVoices(): List<PiperVoice> {
        val catalogMap = (PiperVoiceCatalog.SEARCHABLE_CATALOG + _customVoices.value).associateBy { it.id }
        return catalogMap.values.toList()
    }

    fun getBrazilianVoices(): List<PiperVoice> {
        return getAllVoices().filter { it.languageCode == "pt_BR" }
    }

    fun getVoiceById(voiceId: String): PiperVoice? {
        return getAllVoices().find { it.id == voiceId } ?: PiperVoiceCatalog.DEFAULT_VOICE
    }

    fun downloadVoice(
        voice: PiperVoice,
        onProgress: (Float) -> Unit = {},
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val voiceId = voice.id
        if (isVoiceInstalled(voiceId)) {
            onProgress(1.0f)
            onComplete(true, "Voz já está instalada localmente.")
            return
        }

        scope.launch {
            try {
                _downloadProgress.value = _downloadProgress.value + (voiceId to 0.05f)
                onProgress(0.05f)

                val targetModelFile = getModelFile(voiceId)
                val targetConfigFile = getConfigFile(voiceId)

                // 1. Download do arquivo de configuração JSON (leve)
                downloadFile(voice.configUrl, targetConfigFile) { configProg ->
                    val total = 0.05f + (configProg * 0.05f)
                    _downloadProgress.value = _downloadProgress.value + (voiceId to total)
                    onProgress(total)
                }

                // 2. Download do modelo neural ONNX (maior)
                downloadFile(voice.modelUrl, targetModelFile) { modelProg ->
                    val total = 0.10f + (modelProg * 0.90f)
                    _downloadProgress.value = _downloadProgress.value + (voiceId to total)
                    onProgress(total)
                }

                _downloadProgress.value = _downloadProgress.value - voiceId
                refreshInstalledVoices()
                withContext(Dispatchers.Main) {
                    onProgress(1.0f)
                    onComplete(true, "Voz ${voice.name} instalada com sucesso para uso offline!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao baixar voz Piper ${voice.name}", e)
                _downloadProgress.value = _downloadProgress.value - voiceId
                // Limpa arquivos incompletos
                try {
                    getModelFile(voiceId).delete()
                    getConfigFile(voiceId).delete()
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    onComplete(false, "Falha no download: ${e.localizedMessage ?: "Erro desconhecido"}")
                }
            }
        }
    }

    private fun downloadFile(url: String, destinationFile: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code} ao baixar arquivo de voz Piper: $url")
        }

        val body = response.body ?: throw IOException("Corpo da resposta vazio ao baixar de $url")
        val contentLength = body.contentLength()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")

        body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead: Long = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (contentLength > 0) {
                        val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }
        }

        if (destinationFile.exists()) destinationFile.delete()
        if (!tempFile.renameTo(destinationFile)) {
            tempFile.copyTo(destinationFile, overwrite = true)
            tempFile.delete()
        }
    }

    fun deleteVoice(voiceId: String): Boolean {
        return try {
            val model = getModelFile(voiceId)
            val config = getConfigFile(voiceId)
            if (model.exists()) model.delete()
            if (config.exists()) config.delete()
            val folder = File(voicesDir, voiceId)
            if (folder.exists()) folder.delete()
            refreshInstalledVoices()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir voz Piper $voiceId", e)
            false
        }
    }

    fun addCustomVoice(voice: PiperVoice) {
        val updated = _customVoices.value.filter { it.id != voice.id } + voice.copy(isCustom = true)
        _customVoices.value = updated
        saveCustomVoices(updated)
    }

    private fun saveCustomVoices(list: List<PiperVoice>) {
        val jsonArray = JSONArray()
        list.forEach { v ->
            val obj = JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("languageCode", v.languageCode)
                put("languageDisplayName", v.languageDisplayName)
                put("gender", v.gender)
                put("quality", v.quality)
                put("sampleRate", v.sampleRate)
                put("modelUrl", v.modelUrl)
                put("configUrl", v.configUrl)
                put("sizeMb", v.sizeMb.toDouble())
                put("description", v.description)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_VOICES, jsonArray.toString()).apply()
    }

    private fun loadCustomVoices() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_VOICES, null) ?: return
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<PiperVoice>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PiperVoice(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        languageCode = obj.optString("languageCode", "pt_BR"),
                        languageDisplayName = obj.optString("languageDisplayName", "Personalizada"),
                        gender = obj.optString("gender", "Neutro"),
                        quality = obj.optString("quality", "Média"),
                        sampleRate = obj.optInt("sampleRate", 22050),
                        modelUrl = obj.getString("modelUrl"),
                        configUrl = obj.getString("configUrl"),
                        sizeMb = obj.optDouble("sizeMb", 50.0).toFloat(),
                        description = obj.optString("description", "Voz customizada adicionada pelo usuário."),
                        isCustom = true
                    )
                )
            }
            _customVoices.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao carregar vozes customizadas: ${e.message}")
        }
    }
}
