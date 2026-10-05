package com.example.util

object TextSanitizer {

    private val PAGE_NUMBER_ISOLATED_REGEX = Regex(
        """(?m)^\s*[-–—#*]*\s*\d+\s*[-–—#*]*\s*$"""
    )

    private val PAGE_NUMBER_WORD_REGEX = Regex(
        """(?iu)\b(?:p[aá]gina|p[aá]g\.?|page)\s*\d+\b"""
    )

    /**
     * Converts a user-provided negative term (supporting wildcard '*' and '?')
     * into a compiled case-insensitive Regex.
     *
     * Rules:
     * - Sequence of multiple asterisks (e.g. `****`): matches exactly N word characters [a-zA-Z0-9_\-]
     *   (e.g. `part****` matches `part0024`, `part0001`, `part9999`, but protects `participar`).
     * - Single asterisk `*`: matches zero or more word characters `[\w\-]*`.
     * - Single question mark `?`: matches exactly 1 word character `[\w\-]`.
     * - Punctuation and literal characters are escaped safely.
     * - Word boundaries (`\b`) are preserved so ordinary words aren't accidentally damaged.
     */
    fun buildWildcardRegex(term: String): Regex? {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return null

        // Safety check: if term has ONLY wildcards and spaces (e.g. `****` or `*`), ignore to avoid stripping all text
        val hasNonWildcard = trimmed.any { it != '*' && it != '?' && !it.isWhitespace() }
        if (!hasNonWildcard) return null

        val patternBuilder = StringBuilder()

        // Check if pattern should start with a word boundary
        val firstChar = trimmed.first()
        val startsWithWordChar = firstChar.isLetterOrDigit() || firstChar == '_'
        if (startsWithWordChar) {
            patternBuilder.append("""\b""")
        }

        var i = 0
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '*') {
                // Count consecutive asterisks
                var count = 0
                while (i < trimmed.length && trimmed[i] == '*') {
                    count++
                    i++
                }
                if (count == 1) {
                    // Single wildcard: matches 0 or more word characters
                    patternBuilder.append("""[\w\-]*""")
                } else {
                    // Multiple wildcards (e.g. ****): matches exactly `count` word/digit characters
                    patternBuilder.append("""[\w\-]{$count}""")
                }
            } else if (c == '?') {
                patternBuilder.append("""[\w\-]""")
                i++
            } else {
                // Collect contiguous literal text to escape
                val literalStart = i
                while (i < trimmed.length && trimmed[i] != '*' && trimmed[i] != '?') {
                    i++
                }
                val literalChunk = trimmed.substring(literalStart, i)
                patternBuilder.append(Regex.escape(literalChunk))
            }
        }

        // Check if pattern should end with a word boundary
        val lastChar = trimmed.last()
        val endsWithWordChar = lastChar.isLetterOrDigit() || lastChar == '_' || lastChar == '*' || lastChar == '?'
        if (endsWithWordChar) {
            patternBuilder.append("""\b""")
        }

        return try {
            Regex("""(?iu)""" + patternBuilder.toString())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Filters out page numbers, header artifacts, and custom user negative words from speech text.
     * Supports wildcard '*' (e.g. 'part****' removes 'part0024', 'part0001', 'part9999').
     */
    fun cleanForSpeech(
        text: String,
        negativeWords: String,
        skipPageNumbers: Boolean = true
    ): String {
        var result = text

        // Remove [IMG:...] tags and markdown image tags so file paths are never read aloud
        result = result.replace(Regex("""\[IMG:(.*?)\]""")) { match ->
            val content = match.groupValues[1]
            if (content.contains('|')) content.substringAfter('|').trim() else ""
        }
        result = result.replace(Regex("""!\[(.*?)\]\(.*?\)"""), "$1")

        if (skipPageNumbers) {
            // Remove standalone line page numbers
            result = PAGE_NUMBER_ISOLATED_REGEX.replace(result, "")
            // Remove "página 12", "pág. 34", "page 56"
            result = PAGE_NUMBER_WORD_REGEX.replace(result, "")
        }

        if (negativeWords.isNotBlank()) {
            val terms = negativeWords.split(",", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            for (term in terms) {
                if (term.contains('*') || term.contains('?')) {
                    val wildcardRegex = buildWildcardRegex(term)
                    if (wildcardRegex != null) {
                        result = wildcardRegex.replace(result, "")
                    }
                } else {
                    // Case-insensitive removal of exact negative terms
                    val escaped = Regex.escape(term)
                    val termRegex = Regex("""(?iu)\b$escaped\b""")
                    result = termRegex.replace(result, "")
                    // Also match literal substring if not bounded by word
                    if (result.contains(term, ignoreCase = true)) {
                        result = result.replace(term, "", ignoreCase = true)
                    }
                }
            }
        }

        // Clean extra spaces & punctuation artifacts left over
        result = result
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([.,;:!?])"""), "$1")
            .trim()

        return result
    }
}
