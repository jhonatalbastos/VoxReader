package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.BookEntity
import com.example.service.ReaderMediaService
import com.example.tts.ReaderTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReaderAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SELECT_BOOK = "com.example.widget.ACTION_SELECT_BOOK"
        const val ACTION_TOGGLE_PLAY = "com.example.widget.ACTION_TOGGLE_PLAY"
        const val ACTION_PREV_PARAGRAPH = "com.example.widget.ACTION_PREV_PARAGRAPH"
        const val ACTION_NEXT_PARAGRAPH = "com.example.widget.ACTION_NEXT_PARAGRAPH"

        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
        const val EXTRA_SLOT_INDEX = "EXTRA_SLOT_INDEX"
        const val PREFS_NAME = "ReaderAppWidgetPrefs"
        const val PREF_SELECTED_BOOK_PREFIX = "selected_book_id_"

        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, ReaderAppWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, ReaderAppWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_SELECT_BOOK -> {
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
                if (bookId > 0 && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putLong("$PREF_SELECTED_BOOK_PREFIX$appWidgetId", bookId).apply()
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }

            ACTION_TOGGLE_PLAY -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
                val ttsManager = ReaderTtsManager.getExistingInstance()
                if (ttsManager != null && ttsManager.activeBookId.value == bookId && ttsManager.isReading.value) {
                    ttsManager.togglePlayPause()
                    updateAllWidgets(context)
                } else if (bookId > 0) {
                    // Start playback for this specific book using ReaderMediaService
                    val playIntent = Intent(context, ReaderMediaService::class.java).apply {
                        this.action = ReaderMediaService.ACTION_PLAY_BOOK
                        putExtra(ReaderMediaService.EXTRA_BOOK_ID, bookId)
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(playIntent)
                        } else {
                            context.startService(playIntent)
                        }
                    } catch (_: Exception) {}
                    updateAllWidgets(context)
                }
            }

            ACTION_NEXT_PARAGRAPH -> {
                ReaderTtsManager.getExistingInstance()?.skipNext()
                updateAllWidgets(context)
            }

            ACTION_PREV_PARAGRAPH -> {
                ReaderTtsManager.getExistingInstance()?.skipPrevious()
                updateAllWidgets(context)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        widgetScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val recentBooks: List<BookEntity> = db.bookDao().getRecentBooksSync(3)
                val views = RemoteViews(context.packageName, R.layout.widget_reader_mini_player)

                if (recentBooks.isEmpty()) {
                    views.setTextViewText(R.id.widget_text_title, "Nenhum livro recente")
                    views.setTextViewText(R.id.widget_text_subtitle, "Abra o aplicativo e adicione livros")
                    views.setProgressBar(R.id.widget_progress_bar, 100, 0, false)
                    views.setViewVisibility(R.id.widget_tab_book_1, View.GONE)
                    views.setViewVisibility(R.id.widget_tab_book_2, View.GONE)
                    views.setViewVisibility(R.id.widget_tab_book_3, View.GONE)

                    val openAppIntent = Intent(context, MainActivity::class.java)
                    val pendingOpenApp = PendingIntent.getActivity(
                        context,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_open, pendingOpenApp)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingOpenApp)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                // Determine which book is selected
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedBookId = prefs.getLong("$PREF_SELECTED_BOOK_PREFIX$appWidgetId", -1L)
                val selectedBook: BookEntity = recentBooks.find { it.id == savedBookId } ?: recentBooks[0]

                val ttsManager = ReaderTtsManager.getExistingInstance()
                val isCurrentBookActive = ttsManager != null && ttsManager.activeBookId.value == selectedBook.id
                val isPlaying = isCurrentBookActive && ttsManager?.isPlaying?.value == true

                // Setup 3 Tabs for the 3 last books
                val tabViews = listOf(R.id.widget_tab_book_1, R.id.widget_tab_book_2, R.id.widget_tab_book_3)
                for (i in 0..2) {
                    val tabId = tabViews[i]
                    if (i < recentBooks.size) {
                        val book = recentBooks[i]
                        views.setViewVisibility(tabId, View.VISIBLE)
                        val shortTitle = if (book.title.length > 12) book.title.take(11) + "…" else book.title
                        views.setTextViewText(tabId, "${i + 1}. $shortTitle")

                        val isSelected = book.id == selectedBook.id
                        if (isSelected) {
                            views.setInt(tabId, "setBackgroundResource", R.drawable.widget_tab_selected_bg)
                            views.setTextColor(tabId, 0xFFFFFFFF.toInt())
                        } else {
                            views.setInt(tabId, "setBackgroundResource", R.drawable.widget_tab_unselected_bg)
                            views.setTextColor(tabId, 0xFF94A3B8.toInt())
                        }

                        // Intent to select this book slot
                        val selectIntent = Intent(context, ReaderAppWidgetProvider::class.java).apply {
                            action = ACTION_SELECT_BOOK
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra(EXTRA_BOOK_ID, book.id)
                            putExtra(EXTRA_SLOT_INDEX, i)
                        }
                        val selectPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 10 + i,
                            selectIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(tabId, selectPendingIntent)
                    } else {
                        views.setViewVisibility(tabId, View.GONE)
                    }
                }

                // Selected Book Title & Subtitle Info
                views.setTextViewText(R.id.widget_text_title, selectedBook.title)
                val progressPercent = (selectedBook.readingProgress * 100).toInt()
                val subtitle = if (isPlaying) {
                    "▶ Narrando • Cap. ${selectedBook.currentChapterIndex + 1} ($progressPercent%)"
                } else {
                    "${selectedBook.author} • Cap. ${selectedBook.currentChapterIndex + 1} ($progressPercent%)"
                }
                views.setTextViewText(R.id.widget_text_subtitle, subtitle)
                views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)

                // Play / Pause Button
                val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

                val toggleIntent = Intent(context, ReaderAppWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_PLAY
                    putExtra(EXTRA_BOOK_ID, selectedBook.id)
                }
                val pendingToggle = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + 1,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause, pendingToggle)

                // Prev Paragraph Button
                val prevIntent = Intent(context, ReaderAppWidgetProvider::class.java).apply {
                    action = ACTION_PREV_PARAGRAPH
                }
                val pendingPrev = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + 2,
                    prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_prev, pendingPrev)

                // Next Paragraph Button
                val nextIntent = Intent(context, ReaderAppWidgetProvider::class.java).apply {
                    action = ACTION_NEXT_PARAGRAPH
                }
                val pendingNext = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 100 + 3,
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_next, pendingNext)

                // Open in Reader Button & clicking book card
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_OPEN_BOOK_ID", selectedBook.id)
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    appWidgetId * 100 + 4,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_open, pendingOpen)
                views.setOnClickPendingIntent(R.id.widget_book_info_container, pendingOpen)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {}
        }
    }
}
