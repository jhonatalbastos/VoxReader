package com.example.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class BookRepository(val bookDao: BookDao) {

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val favoriteBooks: Flow<List<BookEntity>> = bookDao.getFavoriteBooks()
    val mostRecentBook: Flow<BookEntity?> = bookDao.getMostRecentBook()

    fun getPagedBooks(query: String = "", filter: String = "Todos"): Flow<PagingData<BookEntity>> {
        val onlyFavorites = filter == "Favoritos"
        val formatFilter = when (filter) {
            "EPUB" -> "EPUB"
            "PDF" -> "PDF"
            "TXT/MD" -> "TXT/MD"
            else -> ""
        }
        return Pager(
            config = PagingConfig(
                pageSize = 15,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = {
                bookDao.getFilteredPagedBooks(
                    query = query.trim(),
                    onlyFavorites = onlyFavorites,
                    formatFilter = formatFilter
                )
            }
        ).flow
    }

    fun getBookById(id: Long): Flow<BookEntity?> = bookDao.getBookById(id)

    suspend fun getBookByIdSync(id: Long): BookEntity? = bookDao.getBookByIdSync(id)

    suspend fun getAllBooksSync(): List<BookEntity> = bookDao.getAllBooksSync()

    suspend fun getRecentBooksSync(limit: Int = 3): List<BookEntity> = bookDao.getRecentBooksSync(limit)

    suspend fun findBookByTitleAndAuthor(title: String, author: String): BookEntity? =
        bookDao.findBookByTitleAndAuthor(title, author)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>): Long {
        val bookId = bookDao.insertBook(book)
        val chaptersWithBookId = chapters.map { it.copy(bookId = bookId) }
        bookDao.insertChapters(chaptersWithBookId)
        return bookId
    }

    suspend fun updateBook(book: BookEntity) {
        bookDao.updateBook(book)
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
    }

    suspend fun updateProgress(bookId: Long, chapterIndex: Int, paragraphIndex: Int, progress: Float) {
        bookDao.updateBookProgress(bookId, chapterIndex, paragraphIndex, progress)
    }

    suspend fun updateVoiceSettings(
        bookId: Long,
        voiceEngine: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    ) {
        bookDao.updateBookVoiceSettings(bookId, voiceEngine, voiceId, speed, pitch)
    }

    suspend fun updateReadingPreferences(bookId: Long, speed: Float, voiceId: String) {
        bookDao.updateReadingPreferences(bookId, speed, voiceId)
    }

    suspend fun updateBookMetadata(bookId: Long, title: String, path: String) {
        bookDao.updateBookMetadata(bookId, title, path)
    }

    suspend fun updateVisualPreferences(bookId: Long, fontSizeSp: Float, lineSpacing: Float, theme: String, font: String) {
        bookDao.updateVisualPreferences(bookId, fontSizeSp, lineSpacing, theme, font)
    }

    suspend fun updateBookCover(bookId: Long, coverPath: String?) {
        bookDao.updateBookCover(bookId, coverPath)
    }

    suspend fun updateNegativeWords(bookId: Long, negativeWords: String, skipPageNumbers: Boolean) {
        bookDao.updateNegativeWords(bookId, negativeWords, skipPageNumbers)
    }

    fun getChapters(bookId: Long): Flow<List<ChapterEntity>> = bookDao.getChaptersForBook(bookId)

    suspend fun getChaptersSync(bookId: Long): List<ChapterEntity> = bookDao.getChaptersForBookSync(bookId)

    suspend fun getChapter(bookId: Long, index: Int): ChapterEntity? = bookDao.getChapter(bookId, index)

    suspend fun updateChapterTitle(chapterId: Long, newTitle: String) {
        bookDao.updateChapterTitle(chapterId, newTitle)
    }

    suspend fun updateChapterContent(chapterId: Long, newContent: String) {
        bookDao.updateChapterContent(chapterId, newContent)
    }

    fun getBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookDao.getBookmarksForBook(bookId)

    suspend fun addBookmark(bookmark: BookmarkEntity): Long = bookDao.insertBookmark(bookmark)

    suspend fun removeBookmark(id: Long) = bookDao.deleteBookmark(id)
}
