package com.example.util

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MemoryProfiler"

data class MemoryStats(
    val usedHeapMb: Long = 0,
    val maxHeapMb: Long = 0,
    val freeHeapMb: Long = 0,
    val nativeHeapMb: Long = 0,
    val systemAvailMb: Long = 0,
    val systemTotalMb: Long = 0,
    val isLowMemory: Boolean = false,
    val heapUsagePercent: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

class MemoryProfiler private constructor(private val context: Context) : ComponentCallbacks2 {

    companion object {
        @Volatile
        private var instance: MemoryProfiler? = null

        fun getInstance(context: Context): MemoryProfiler {
            return instance ?: synchronized(this) {
                instance ?: MemoryProfiler(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _memoryStats = MutableStateFlow(getSnapshot())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    private val _memoryLogs = MutableStateFlow<List<String>>(emptyList())
    val memoryLogs: StateFlow<List<String>> = _memoryLogs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var monitoringJob: Job? = null

    init {
        context.registerComponentCallbacks(this)
        addLog("Inicializador do Monitor de Memória ativo.")
        startPeriodicMonitoring()
    }

    private fun formatTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun addLog(message: String) {
        val entry = "[${formatTime()}] $message"
        Log.i(TAG, entry)
        val current = _memoryLogs.value.toMutableList()
        if (current.size >= 80) {
            current.removeAt(0)
        }
        current.add(entry)
        _memoryLogs.value = current
    }

    fun getSnapshot(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val totalHeap = runtime.totalMemory()
        val freeHeap = runtime.freeMemory()
        val usedHeap = (totalHeap - freeHeap) / (1024 * 1024)
        val maxHeap = runtime.maxMemory() / (1024 * 1024)
        val remainingHeap = maxHeap - usedHeap
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val sysAvail = memInfo.availMem / (1024 * 1024)
        val sysTotal = memInfo.totalMem / (1024 * 1024)

        val percent = if (maxHeap > 0) ((usedHeap.toFloat() / maxHeap) * 100).toInt() else 0

        return MemoryStats(
            usedHeapMb = usedHeap,
            maxHeapMb = maxHeap,
            freeHeapMb = remainingHeap,
            nativeHeapMb = nativeHeap,
            systemAvailMb = sysAvail,
            systemTotalMb = sysTotal,
            isLowMemory = memInfo.lowMemory,
            heapUsagePercent = percent,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun startPeriodicMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = scope.launch {
            var counter = 0
            while (isActive) {
                val stats = getSnapshot()
                _memoryStats.value = stats

                counter++
                // Log every ~15 seconds or when memory is elevated (> 65%)
                if (counter % 3 == 0 || stats.heapUsagePercent >= 65 || stats.isLowMemory) {
                    val alert = if (stats.isLowMemory) " ⚠️ ALERTA SO BAIXA MEMÓRIA" else ""
                    addLog("RAM: ${stats.usedHeapMb}MB/${stats.maxHeapMb}MB (${stats.heapUsagePercent}%) | Nativa: ${stats.nativeHeapMb}MB | Disp: ${stats.systemAvailMb}MB$alert")
                }

                delay(5000)
            }
        }
    }

    fun forceGarbageCollection(): MemoryStats {
        addLog("Solicitando Forçar Coleta de Lixo (System.gc())...")
        System.gc()
        Runtime.getRuntime().gc()
        val after = getSnapshot()
        _memoryStats.value = after
        addLog("Pós-GC: ${after.usedHeapMb}MB/${after.maxHeapMb}MB (${after.heapUsagePercent}%)")
        return after
    }

    fun clearLogs() {
        _memoryLogs.value = listOf("[${formatTime()}] Histórico de memória limpo.")
    }

    override fun onTrimMemory(level: Int) {
        val levelName = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "MODERATE (20%)"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "LOW (Pressão Moderada)"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "CRITICAL (Perigo Iminente de Fechamento)"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN (App minimizado)"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND (Em segundo plano)"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE_BG"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE (Sistema prestes a matar processo)"
            else -> "Nível $level"
        }
        val msg = "⚠️ AVISO DO ANDROID onTrimMemory: $levelName"
        addLog(msg)
        Log.w(TAG, msg)
        _memoryStats.value = getSnapshot()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        val msg = "🚨 AVISO CRÍTICO: onLowMemory disparado pelo sistema operacional!"
        addLog(msg)
        Log.e(TAG, msg)
        _memoryStats.value = getSnapshot()
    }
}
