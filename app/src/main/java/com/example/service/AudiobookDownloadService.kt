package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.tts.AudiobookDownloadManager

private const val TAG = "AudiobookDownloadService"

class AudiobookDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "audiobook_download_channel"
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002

        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_COMPLETE = "ACTION_COMPLETE"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_STATUS = "EXTRA_STATUS"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"

        fun startService(context: Context, bookTitle: String) {
            val intent = Intent(context, AudiobookDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, bookTitle)
                putExtra(EXTRA_STATUS, "Iniciando processamento em segundo plano...")
                putExtra(EXTRA_PROGRESS, 0)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao iniciar ForegroundService", e)
            }
        }

        fun updateProgress(context: Context, bookTitle: String, status: String, progressPercent: Int) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val notification = buildProgressNotification(context, bookTitle, status, progressPercent)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao atualizar notificação", e)
            }
        }

        fun completeService(context: Context, message: String) {
            val intent = Intent(context, AudiobookDownloadService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_STATUS, message)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // If in background, notify completion directly
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val notification = buildCompletionNotification(context, message)
                notificationManager?.notify(COMPLETION_NOTIFICATION_ID, notification)
                notificationManager?.cancel(NOTIFICATION_ID)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudiobookDownloadService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
            }
        }

        fun buildProgressNotification(
            context: Context,
            title: String,
            status: String,
            progress: Int
        ): Notification {
            ensureNotificationChannel(context)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, AudiobookDownloadService::class.java).apply {
                action = ACTION_STOP
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                2,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Processando: $title")
                .setContentText(status)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setProgress(100, progress.coerceIn(0, 100), false)
                .setContentIntent(contentPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .build()
        }

        fun buildCompletionNotification(context: Context, status: String): Notification {
            ensureNotificationChannel(context)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Audiolivro Pronto!")
                .setContentText(status)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val existing = notificationManager?.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Processamento em Segundo Plano de Audiolivro",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Notificações do progresso de síntese e downloads de áudio em segundo plano"
                        setShowBadge(false)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP

        when (action) {
            ACTION_START -> {
                acquireWakeLock()
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "VoxReader"
                val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Processando audiolivro em segundo plano..."
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val notification = buildProgressNotification(this, title, status, progress)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "VoxReader"
                val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Processando..."
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(this, title, status, progress))
            }
            ACTION_COMPLETE -> {
                val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Audiolivro processado com sucesso!"
                val completionNotification = buildCompletionNotification(this, status)
                notificationManager.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                AudiobookDownloadManager.getExistingInstance()?.cancelInternalJob()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxReader:AudiobookDownload").apply {
                    setReferenceCounted(false)
                    acquire(60 * 60 * 1000L) // 1 hour safety timeout
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível adquirir WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao liberar WakeLock", e)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
