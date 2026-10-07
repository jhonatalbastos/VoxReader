package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
          AND (:onlyFavorites = 0 OR isFavorite = 1)
          AND (:formatFilter = '' OR format = :formatFilter OR (:formatFilter = 'TXT/MD' AND (format = 'TXT' OR format = 'MD')))
        ORDER BY lastReadTimestamp DESC
    """)
    fun getFilteredPagedBooks(query: String, onlyFavorites: Boolean, formatFilter: String): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    fun getPagedBooks(): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTimestamp DESC")
    fun getPagedFavoriteBooks(): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY lastReadTimestamp DESC")
    fun searchPagedBooks(query: String): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE isFavorite = 1 AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%') ORDER BY lastReadTimestamp DESC")
    fun searchPagedFavoriteBooks(query: String): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC LIMIT 1")
    fun getMostRecentBook(): Flow<BookEntity?>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC")
    suspend fun getAllBooksSync(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY lastReadTimestamp DESC LIMIT :limit")
    suspend fun getRecentBooksSync(limit: Int = 3): List<BookEntity>

    @Query("SELECT * FROM books WHERE title = :title AND author = :author LIMIT 1")
    suspend fun findBookByTitleAndAuthor(title: String, author: String): BookEntity?

    @Query("SELECT * FROM books WHERE isFavorite = 1 ORDER BY lastReadTimestamp DESC")
    fun getFavoriteBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun getBookById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookByIdSync(id: Long): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("""
        UPDATE books 
        SET currentChapterIndex = :chapterIndex, 
            currentParagraphIndex = :paragraphIndex, 
            progress = :progress,
            readingProgress = :progress, 
            lastReadTimestamp = :timestamp 
        WHERE id = :bookId
    """)
    suspend fun updateBookProgress(
        bookId: Long,
        chapterIndex: Int,
        paragraphIndex: Int,
        progress: Float,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE books 
        SET voiceEngine = :voiceEngine,
            voiceId = :voiceId, 
            voiceSpeed = :speed, 
            voicePitch = :pitch 
        WHERE id = :bookId
    """)
    suspend fun updateBookVoiceSettings(
        bookId: Long,
        voiceEngine: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    )

    @Query("""
        UPDATE books 
        SET voiceSpeed = :speed, 
            voiceId = :voiceId 
        WHERE id = :bookId
    """)
    suspend fun updateReadingPreferences(bookId: Long, speed: Float, voiceId: String)

    @Query("UPDATE books SET title = :title, path = :path WHERE id = :bookId")
    suspend fun updateBookMetadata(bookId: Long, title: String, path: String)

    @Query("""
        UPDATE books 
        SET fontSizeSp = :fontSizeSp,
            lineSpacing = :lineSpacing,
            readerTheme = :theme,
            readerFont = :font
        WHERE id = :bookId
    """)
    suspend fun updateVisualPreferences(bookId: Long, fontSizeSp: Float, lineSpacing: Float, theme: String, font: String)

    @Query("UPDATE books SET coverImagePath = :coverPath WHERE id = :bookId")
    suspend fun updateBookCover(bookId: Long, coverPath: String?)

    @Query("UPDATE books SET negativeWords = :negativeWords, skipPageNumbers = :skipPageNumbers WHERE id = :bookId")
    suspend fun updateNegativeWords(bookId: Long, negativeWords: String, skipPageNumbers: Boolean)

    // Chapters
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    fun getChaptersForBook(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getChaptersForBookSync(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId LIMIT 1")
    suspend fun getChapterById(chapterId: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapter(bookId: Long, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET title = :newTitle WHERE id = :chapterId")
    suspend fun updateChapterTitle(chapterId: Long, newTitle: String)

    @Query("UPDATE chapters SET content = :newContent WHERE id = :chapterId")
    suspend fun updateChapterContent(chapterId: Long, newContent: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
