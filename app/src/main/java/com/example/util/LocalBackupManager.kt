package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.BookDao
import com.example.data.BookEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalBackupManager {

    /**
     * Serializes all books' progress and voice/reading settings to a JSON string.
     */
    fun createBackupJson(books: List<BookEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "VoxReader")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("totalBooks", books.size)

        val booksArray = JSONArray()
        for (book in books) {
            val bookObj = JSONObject()
            bookObj.put("title", book.title)
            bookObj.put("author", book.author)
            bookObj.put("format", book.format)
            bookObj.put("currentChapterIndex", book.currentChapterIndex)
            bookObj.put("currentParagraphIndex", book.currentParagraphIndex)
            bookObj.put("readingProgress", book.readingProgress.toDouble())
            bookObj.put("lastReadTimestamp", book.lastReadTimestamp)
            bookObj.put("isFavorite", book.isFavorite)

            // Voice & Speech Settings
            bookObj.put("voiceEngine", book.voiceEngine)
            bookObj.put("voiceId", book.voiceId)
            bookObj.put("voiceSpeed", book.voiceSpeed.toDouble())
            bookObj.put("voicePitch", book.voicePitch.toDouble())

            // Appearance & Formatting Settings
            bookObj.put("fontSizeSp", book.fontSizeSp.toDouble())
            bookObj.put("lineSpacing", book.lineSpacing.toDouble())
            bookObj.put("readerTheme", book.readerTheme)
            bookObj.put("readerFont", book.readerFont)

            // Speech Filters
            bookObj.put("negativeWords", book.negativeWords)
            bookObj.put("skipPageNumbers", book.skipPageNumbers)

            booksArray.put(bookObj)
        }
        root.put("books", booksArray)

        return root.toString(2)
    }

    /**
     * Writes backup JSON to a user-selected SAF document Uri.
     */
    fun writeBackupToUri(context: Context, uri: Uri, books: List<BookEntity>): Result<Int> {
        return try {
            val json = createBackupJson(books)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            Result.success(books.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a temporary shareable JSON backup file in the cache directory.
     */
    fun createShareableBackupFile(context: Context, books: List<BookEntity>): Result<File> {
        return try {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "voxreader_backup_$dateStr.json")
            file.writeText(createBackupJson(books), Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads a backup JSON file from a Uri and updates existing matching books.
     */
    suspend fun restoreBackupFromUri(context: Context, uri: Uri, bookDao: BookDao): Result<Int> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            } ?: return Result.failure(Exception("Não foi possível ler o arquivo selecionado."))

            restoreBackupFromJson(jsonString, bookDao)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses JSON backup and updates book progress & voice settings in Room.
     */
    suspend fun restoreBackupFromJson(jsonString: String, bookDao: BookDao): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val booksArray = root.optJSONArray("books")
                ?: return Result.failure(Exception("Formato JSON inválido: chave 'books' ausente."))

            var restoredCount = 0

            for (i in 0 until booksArray.length()) {
                val bookObj = booksArray.getJSONObject(i)
                val title = bookObj.getString("title")
                val author = bookObj.optString("author", "")

                // Find matching book in database by title and author
                val existing = bookDao.findBookByTitleAndAuthor(title, author)
                    ?: bookDao.getAllBooksSync().find { it.title.equals(title, ignoreCase = true) }

                if (existing != null) {
                    val updated = existing.copy(
                        currentChapterIndex = bookObj.optInt("currentChapterIndex", existing.currentChapterIndex),
                        currentParagraphIndex = bookObj.optInt("currentParagraphIndex", existing.currentParagraphIndex),
                        readingProgress = bookObj.optDouble("readingProgress", existing.readingProgress.toDouble()).toFloat(),
                        progress = bookObj.optDouble("readingProgress", existing.progress.toDouble()).toFloat(),
                        lastReadTimestamp = bookObj.optLong("lastReadTimestamp", existing.lastReadTimestamp),
                        isFavorite = bookObj.optBoolean("isFavorite", existing.isFavorite),
                        voiceEngine = bookObj.optString("voiceEngine", existing.voiceEngine),
                        voiceId = bookObj.optString("voiceId", existing.voiceId),
                        voiceSpeed = bookObj.optDouble("voiceSpeed", existing.voiceSpeed.toDouble()).toFloat(),
                        voicePitch = bookObj.optDouble("voicePitch", existing.voicePitch.toDouble()).toFloat(),
                        fontSizeSp = bookObj.optDouble("fontSizeSp", existing.fontSizeSp.toDouble()).toFloat(),
                        lineSpacing = bookObj.optDouble("lineSpacing", existing.lineSpacing.toDouble()).toFloat(),
                        readerTheme = bookObj.optString("readerTheme", existing.readerTheme),
                        readerFont = bookObj.optString("readerFont", existing.readerFont),
                        negativeWords = bookObj.optString("negativeWords", existing.negativeWords),
                        skipPageNumbers = bookObj.optBoolean("skipPageNumbers", existing.skipPageNumbers)
                    )
                    bookDao.updateBook(updated)
                    restoredCount++
                }
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
