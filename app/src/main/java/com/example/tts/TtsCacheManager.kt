package com.example.tts

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TtsCacheManager"

class TtsCacheManager(private val context: Context) {

    private val cacheDir: File by lazy {
        val dir = File(context.filesDir, "audio_cache")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val exportDir: File by lazy {
        val dir = File(context.cacheDir, "audio_exports")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun getFileName(
        bookId: Long,
        chapterIndex: Int,
        paragraphIndex: Int,
        engine: String,
        voiceId: String,
        speed: Float
    ): String {
        val speedStr = "%.2f".format(speed).replace('.', '_')
        val cleanEngine = engine.lowercase()
        return "tts_${bookId}_c${chapterIndex}_p${paragraphIndex}_${cleanEngine}_${voiceId}_${speedStr}.mp3"
    }

    fun getCachedFile(
        bookId: Long,
        chapterIndex: Int,
        paragraphIndex: Int,
        engine: String = "EDGE_TTS",
        voiceId: String,
        speed: Float
    ): File? {
        val file = File(cacheDir, getFileName(bookId, chapterIndex, paragraphIndex, engine, voiceId, speed))
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isCached(
        bookId: Long,
        chapterIndex: Int,
        paragraphIndex: Int,
        engine: String = "EDGE_TTS",
        voiceId: String,
        speed: Float
    ): Boolean {
        return getCachedFile(bookId, chapterIndex, paragraphIndex, engine, voiceId, speed) != null
    }

    fun saveAudio(
        bookId: Long,
        chapterIndex: Int,
        paragraphIndex: Int,
        engine: String = "EDGE_TTS",
        voiceId: String,
        speed: Float,
        bytes: ByteArray
    ): File {
        trimCacheIfNeeded()
        val file = File(cacheDir, getFileName(bookId, chapterIndex, paragraphIndex, engine, voiceId, speed))
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    // --- New ElevenLabs SHA-256 Hashed Caching System --- //

    private fun computeSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getCachedAudioElevenLabs(
        text: String,
        voiceId: String,
        modelId: String,
        stability: Float,
        similarityBoost: Float,
        style: Float
    ): TtsSynthesisResult? {
        val hashInput = "$text|$voiceId|$modelId|$stability|$similarityBoost|$style"
        val hash = computeSha256(hashInput)

        val audioFile = File(cacheDir, "$hash.mp3")
        val alignmentsFile = File(cacheDir, "$hash.json")

        if (audioFile.exists() && audioFile.length() > 0 && alignmentsFile.exists()) {
            try {
                val jsonString = alignmentsFile.readText()
                val jsonArray = JSONArray(jsonString)
                val alignments = mutableListOf<WordAlignment>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    alignments.add(
                        WordAlignment(
                            word = obj.getString("word"),
                            startSeconds = obj.getDouble("startSeconds").toFloat(),
                            endSeconds = obj.getDouble("endSeconds").toFloat()
                        )
                    )
                }
                return TtsSynthesisResult(audioFile, alignments)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse alignments for hash $hash", e)
                return null
            }
        }
        return null
    }

    fun saveAudioToCacheElevenLabs(
        text: String,
        voiceId: String,
        modelId: String,
        stability: Float,
        similarityBoost: Float,
        style: Float,
        audioFile: File,
        alignments: List<WordAlignment>
    ): File {
        trimCacheIfNeeded()
        val hashInput = "$text|$voiceId|$modelId|$stability|$similarityBoost|$style"
        val hash = computeSha256(hashInput)

        val targetAudioFile = File(cacheDir, "$hash.mp3")
        audioFile.copyTo(targetAudioFile, overwrite = true)

        val alignmentsFile = File(cacheDir, "$hash.json")
        val jsonArray = JSONArray()
        for (alignment in alignments) {
            val obj = JSONObject().apply {
                put("word", alignment.word)
                put("startSeconds", alignment.startSeconds)
                put("endSeconds", alignment.endSeconds)
            }
            jsonArray.put(obj)
        }
        alignmentsFile.writeText(jsonArray.toString())

        return targetAudioFile
    }

    // ---------------------------------------------------- //

    private fun trimCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            if (files.size > 250) {
                val sorted = files.sortedBy { it.lastModified() }
                val toDeleteCount = files.size - 200
                for (i in 0 until toDeleteCount) {
                    sorted.getOrNull(i)?.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Deletes cached audio file for a specific paragraph.
     */
    fun deleteParagraphCache(bookId: Long, chapterIndex: Int, paragraphIndex: Int): Boolean {
        val prefix = "tts_${bookId}_c${chapterIndex}_p${paragraphIndex}_"
        var deleted = false
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) {
                if (file.delete()) deleted = true
            }
        }
        return deleted
    }

    /**
     * Deletes all cached audio files for a specific chapter.
     */
    fun deleteChapterCache(bookId: Long, chapterIndex: Int): Int {
        val prefix = "tts_${bookId}_c${chapterIndex}_"
        var count = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) {
                if (file.delete()) count++
            }
        }
        return count
    }

    /**
     * Deletes all cached audio files for a specific book.
     */
    fun deleteBookCache(bookId: Long): Int {
        val prefix = "tts_${bookId}_"
        var count = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) {
                if (file.delete()) count++
            }
        }
        return count
    }

    fun getChapterAudioFiles(
        bookId: Long,
        chapterIndex: Int,
        totalParagraphs: Int,
        engine: String = "EDGE_TTS",
        voiceId: String,
        speed: Float
    ): List<File?> {
        return (0 until totalParagraphs).map { pIndex ->
            getCachedFile(bookId, chapterIndex, pIndex, engine, voiceId, speed)
        }
    }

    /**
     * Concatenates list of MP3/audio files into a single consolidated file.
     */
    fun mergeAudioFiles(sourceFiles: List<File>, outputFileName: String): File {
        val outputFile = File(exportDir, outputFileName)
        if (outputFile.exists()) outputFile.delete()

        FileOutputStream(outputFile).use { outputStream ->
            for (file in sourceFiles) {
                if (file.exists() && file.length() > 0) {
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            outputStream.flush()
        }
        return outputFile
    }

    /**
     * Saves an audio file to Android's public Downloads directory and returns its content Uri.
     */
    fun saveToPublicDownloads(context: Context, audioFile: File, title: String): Uri? {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        val ext = if (audioFile.name.endsWith(".wav", ignoreCase = true)) ".wav" else ".mp3"
        val fileName = if (cleanTitle.endsWith(ext, ignoreCase = true)) cleanTitle else "$cleanTitle$ext"
        val mimeType = if (ext == ".wav") "audio/wav" else "audio/mpeg"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VoxReader")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { destUri ->
                    resolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(audioFile).use { it.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(destUri, contentValues, null, null)
                    destUri
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "VoxReader")
                if (!appDir.exists()) appDir.mkdirs()
                val destFile = File(appDir, fileName)
                FileInputStream(audioFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio to Downloads", e)
            null
        }
    }
}
