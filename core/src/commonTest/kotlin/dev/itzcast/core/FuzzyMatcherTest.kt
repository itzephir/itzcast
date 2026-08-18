package dev.itzcast.core

import kotlin.test.Test
import kotlin.test.assertTrue

class FuzzyMatcherTest {
    @Test
    fun exactAndPrefixMatchesBeatSubsequence() {
        assertTrue(FuzzyMatcher.score("safari", "Safari") > FuzzyMatcher.score("safari", "Safari Technology Preview"))
        assertTrue(FuzzyMatcher.score("sf", "Safari") > FuzzyMatcher.score("sf", "System Information"))
    }

    @Test
    fun nonMatchIsRejected() {
        assertTrue(!FuzzyMatcher.score("xyz", "Safari").isFinite())
    }
}
