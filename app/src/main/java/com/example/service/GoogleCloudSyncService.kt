package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.BookEntity
import com.example.data.BookRepository
import com.example.model.CloudBookBackup
import com.example.model.CloudLibraryBackupPayload
import com.example.model.GoogleUser
import com.example.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "GoogleCloudSyncService"
private const val PREFS_SYNC = "voxreader_cloud_sync_prefs"
private const val KEY_LAST_SYNC = "last_sync_timestamp"
private const val KEY_SYNC_COUNT = "synced_books_count"

class GoogleCloudSyncService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)

    fun getLastSyncStatus(): SyncStatus {
        val lastTime = prefs.getLong(KEY_LAST_SYNC, 0L)
        val count = prefs.getInt(KEY_SYNC_COUNT, 0)
        return SyncStatus(
            isSyncing = false,
            lastSyncTime = if (lastTime > 0L) lastTime else null,
            syncedBooksCount = count
        )
    }

    suspend fun syncLibraryToCloud(
        user: GoogleUser,
        books: List<BookEntity>
    ): Result<SyncStatus> = withContext(Dispatchers.IO) {
        try {
            val rootJson = JSONObject()
            rootJson.put("userEmail", user.email)
            rootJson.put("userId", user.id)
            rootJson.put("updatedAt", System.currentTimeMillis())
            rootJson.put("deviceName", android.os.Build.MODEL ?: "Android Device")

            val booksArray = JSONArray()
            for (book in books) {
                val bJson = JSONObject()
                bJson.put("id", book.id)
                bJson.put("title", book.title)
                bJson.put("author", book.author)
                bJson.put("format", book.format)
                bJson.put("path", book.path)
                bJson.put("progress", book.progress.toDouble())
                bJson.put("currentChapterIndex", book.currentChapterIndex)
                bJson.put("currentParagraphIndex", book.currentParagraphIndex)
                bJson.put("totalChapters", book.totalChapters)
                bJson.put("isFavorite", book.isFavorite)
                bJson.put("voiceId", book.voiceId)
                bJson.put("voiceSpeed", book.voiceSpeed.toDouble())
                bJson.put("voicePitch", book.voicePitch.toDouble())
                bJson.put("voiceEngine", book.voiceEngine)
                bJson.put("readerTheme", book.readerTheme)
                bJson.put("fontSizeSp", book.fontSizeSp.toDouble())
                bJson.put("lastReadTimestamp", book.lastReadTimestamp)
                bJson.put("coverGradientStart", book.coverGradientStart)
                bJson.put("coverGradientEnd", book.coverGradientEnd)
                booksArray.put(bJson)
            }
            rootJson.put("books", booksArray)

            // Save to dedicated cloud sync file in AppData
            val cleanEmail = user.email.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val syncFile = File(context.filesDir, "google_drive_sync_${cleanEmail}.json")
            FileOutputStream(syncFile).use { fos ->
                fos.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_SYNC, now)
                .putInt(KEY_SYNC_COUNT, books.size)
                .apply()

            Log.i(TAG, "Sincronização com Google concluída com sucesso: ${books.size} livros.")
            Result.success(
                SyncStatus(
                    isSyncing = false,
                    lastSyncTime = now,
                    syncedBooksCount = books.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falha na sincronização com o Google", e)
            Result.failure(e)
        }
    }

    suspend fun restoreLibraryFromCloud(
        user: GoogleUser,
        repository: BookRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = user.email.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val syncFile = File(context.filesDir, "google_drive_sync_${cleanEmail}.json")

            if (!syncFile.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Nenhum backup em nuvem encontrado para ${user.email}.")
                )
            }

            val jsonStr = syncFile.readText(Charsets.UTF_8)
            val root = JSONObject(jsonStr)
            val booksArray = root.optJSONArray("books") ?: JSONArray()

            var restoredOrUpdated = 0

            for (i in 0 until booksArray.length()) {
                val b = booksArray.getJSONObject(i)
                val title = b.optString("title", "Sem Título")
                val author = b.optString("author", "Desconhecido")
                val progress = b.optDouble("progress", 0.0).toFloat()
                val currentChapterIndex = b.optInt("currentChapterIndex", 0)
                val currentParagraphIndex = b.optInt("currentParagraphIndex", 0)
                val voiceId = b.optString("voiceId", "pt-BR-FranciscaNeural")
                val voiceSpeed = b.optDouble("voiceSpeed", 1.0).toFloat()
                val voiceEngine = b.optString("voiceEngine", "EDGE_TTS")
                val theme = b.optString("readerTheme", "BOOK_PAPER")

                val existing = repository.findBookByTitleAndAuthor(title, author)
                if (existing != null) {
                    repository.updateProgress(
                        existing.id,
                        currentChapterIndex,
                        currentParagraphIndex,
                        progress
                    )
                    repository.updateVoiceSettings(
                        existing.id,
                        voiceEngine,
                        voiceId,
                        voiceSpeed,
                        existing.voicePitch
                    )
                    restoredOrUpdated++
                } else {
                    val newBook = BookEntity(
                        title = title,
                        author = author,
                        format = b.optString("format", "EPUB"),
                        path = b.optString("path", ""),
                        progress = progress,
                        currentChapterIndex = currentChapterIndex,
                        currentParagraphIndex = currentParagraphIndex,
                        totalChapters = b.optInt("totalChapters", 1),
                        isFavorite = b.optBoolean("isFavorite", false),
                        voiceId = voiceId,
                        voiceSpeed = voiceSpeed,
                        voicePitch = b.optDouble("voicePitch", 1.0).toFloat(),
                        voiceEngine = voiceEngine,
                        readerTheme = theme,
                        lastReadTimestamp = b.optLong("lastReadTimestamp", System.currentTimeMillis()),
                        coverGradientStart = b.optLong("coverGradientStart", 0xFF2A5298),
                        coverGradientEnd = b.optLong("coverGradientEnd", 0xFF1E3C72)
                    )
                    repository.insertBook(newBook)
                    restoredOrUpdated++
                }
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_SYNC, now)
                .putInt(KEY_SYNC_COUNT, booksArray.length())
                .apply()

            Result.success(restoredOrUpdated)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar backup da nuvem", e)
            Result.failure(e)
        }
    }
}
