package com.example

import android.app.Application
import android.util.Log
import com.example.service.AudiobookDownloadService
import com.example.service.ReaderMediaService

private const val TAG = "VoxReaderApp"

class VoxReaderApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Ensure notification channels exist right at startup
        try {
            ReaderMediaService.ensureChannel(this)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao inicializar canais de notificação: ${e.message}")
        }

        // 2. Initialize Memory Profiler
        val memoryProfiler = com.example.util.MemoryProfiler.getInstance(this)
        memoryProfiler.addLog("VoxReaderApplication inicializada. LeakCanary e Profiler ativos.")

        // 3. Global Safety Exception Handler to prevent the app from closing unexpectedly
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val snapshot = memoryProfiler.getSnapshot()
            val crashMsg = "CRASH em ${thread.name}: ${throwable.javaClass.simpleName} - ${throwable.message} [RAM: ${snapshot.usedHeapMb}MB/${snapshot.maxHeapMb}MB]"
            memoryProfiler.addLog("🚨 $crashMsg")
            Log.e(TAG, crashMsg, throwable)

            // Catch known transient background / OS exceptions that can terminate the app
            val isKnownRecoverable = throwable is java.net.SocketException ||
                    throwable is java.net.SocketTimeoutException ||
                    throwable is java.io.IOException ||
                    throwable.javaClass.name.contains("ForegroundServiceStartNotAllowedException") ||
                    throwable.message?.contains("RemoteServiceException") == true

            if (isKnownRecoverable) {
                Log.w(TAG, "Exceção recuperável interceptada para evitar fechamento do app: ${throwable.message}")
            } else {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
