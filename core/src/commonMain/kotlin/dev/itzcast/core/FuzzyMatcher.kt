package dev.itzcast.core

object FuzzyMatcher {
    fun score(query: String, candidate: String): Double {
        val needle = query.trim().lowercase()
        val haystack = candidate.lowercase()
        if (needle.isEmpty()) return 0.0
        if (needle == haystack) return 100.0
        if (haystack.startsWith(needle)) return 85.0 - (haystack.length - needle.length) * 0.05
        val direct = haystack.indexOf(needle)
        if (direct >= 0) return 70.0 - direct * 0.5

        var queryIndex = 0
        var firstMatch = -1
        var previousMatch = -2
        var consecutive = 0
        var gaps = 0
        for (index in haystack.indices) {
            if (queryIndex < needle.length && haystack[index] == needle[queryIndex]) {
                if (firstMatch < 0) firstMatch = index
                if (index == previousMatch + 1) consecutive++ else gaps++
                previousMatch = index
                queryIndex++
            }
        }
        if (queryIndex != needle.length) return Double.NEGATIVE_INFINITY
        val span = previousMatch - firstMatch + 1
        val extraSpan = (span - needle.length).coerceAtLeast(0)
        return 40.0 + consecutive * 3.0 - gaps * 1.5 - firstMatch * 0.25 - extraSpan * 0.75
    }
}
