package com.example.tts

import com.example.BuildConfig
import com.example.data.BookRepository
import com.example.data.GeminiApiKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class GeminiApiKeyManager(private val repository: BookRepository) {

    private val roundRobinIndex = AtomicInteger(0)

    /**
     * Retrieves the next available API key in round-robin order.
     * Excludes keys that failed within the current request chain.
     */
    suspend fun getNextApiKey(excludedKeys: Set<String> = emptySet()): String? = withContext(Dispatchers.IO) {
        val dbKeys = repository.getActiveApiKeysSync().map { it.apiKey.trim() }.filter { it.isNotBlank() }

        val candidateKeys = mutableListOf<String>()
        candidateKeys.addAll(dbKeys)

        // If BuildConfig has a valid key not already in candidates, include it
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && !candidateKeys.contains(buildKey)) {
            candidateKeys.add(buildKey)
        }

        val availableKeys = candidateKeys.filterNot { excludedKeys.contains(it) }
        if (availableKeys.isEmpty()) {
            return@withContext null
        }

        val index = kotlin.math.abs(roundRobinIndex.getAndIncrement()) % availableKeys.size
        availableKeys[index]
    }

    suspend fun getAvailableKeyCount(): Int = withContext(Dispatchers.IO) {
        val dbCount = repository.getActiveApiKeysSync().size
        val hasBuildKey = try {
            val key = BuildConfig.GEMINI_API_KEY.trim()
            key.isNotBlank() && key != "MY_GEMINI_API_KEY"
        } catch (_: Exception) {
            false
        }
        dbCount + (if (hasBuildKey) 1 else 0)
    }
}
