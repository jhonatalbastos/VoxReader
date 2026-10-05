package com.example.tts

data class ContextualChunk(
    val index: Int,
    val targetText: String,
    val previousContext: String,
    val nextContext: String
)

class TextSegmentationEngine(
    private val targetChunkChars: Int = 500,
    private val contextWindowChars: Int = 400
) {

    fun createPayloads(rawNarrative: String): List<ContextualChunk> {
        // Split text based on punctuation followed by space (end of sentences).
        val sentences = rawNarrative.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        var currentBuffer = StringBuilder()

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) continue

            if (currentBuffer.length + trimmed.length > targetChunkChars && currentBuffer.isNotEmpty()) {
                chunks.add(currentBuffer.toString().trim())
                currentBuffer = StringBuilder(trimmed)
            } else {
                if (currentBuffer.isNotEmpty()) currentBuffer.append(" ")
                currentBuffer.append(trimmed)
            }
        }
        if (currentBuffer.isNotEmpty()) {
            chunks.add(currentBuffer.toString().trim())
        }

        val result = mutableListOf<ContextualChunk>()
        val total = chunks.size

        for (i in 0 until total) {
            val prevBuilder = StringBuilder()
            var p = i - 1
            while (p >= 0 && prevBuilder.length < contextWindowChars) {
                prevBuilder.insert(0, chunks[p] + " ")
                p--
            }

            val nextBuilder = StringBuilder()
            var n = i + 1
            while (n < total && nextBuilder.length < contextWindowChars) {
                nextBuilder.append(" ").append(chunks[n])
                n++
            }

            val prevText = if (prevBuilder.length > contextWindowChars) {
                prevBuilder.substring(prevBuilder.length - contextWindowChars).trim()
            } else {
                prevBuilder.toString().trim()
            }

            val nextText = if (nextBuilder.length > contextWindowChars) {
                nextBuilder.substring(0, contextWindowChars).trim()
            } else {
                nextBuilder.toString().trim()
            }

            result.add(
                ContextualChunk(
                    index = i,
                    targetText = chunks[i],
                    previousContext = prevText,
                    nextContext = nextText
                )
            )
        }

        return result
    }
}
