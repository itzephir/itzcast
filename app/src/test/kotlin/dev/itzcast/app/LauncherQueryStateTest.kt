package dev.itzcast.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.itzcast.core.PrefixMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LauncherQueryStateTest {
    @Test
    fun exactPrefixWaitsForSeparator() {
        val value = TextFieldValue("yt", selection = TextRange(2))

        val state = launcherQueryState(value, PrefixMatch("yt", ""))

        assertEquals(LauncherQueryState.Plain(value), state)
    }

    @Test
    fun separatedPrefixBecomesPrefixed() {
        val state = launcherQueryState(
            TextFieldValue("  yt   funny cats"),
            PrefixMatch("yt", "funny cats"),
        )

        val prefixed = assertIs<LauncherQueryState.Prefixed>(state)
        assertEquals("yt", prefixed.prefix)
        assertEquals("funny cats", prefixed.arguments.text)
        assertEquals(TextRange(10), prefixed.arguments.selection)
        assertEquals("yt funny cats", prefixed.query)
    }

    @Test
    fun argumentEditsReconstructLogicalQuery() {
        val state = LauncherQueryState.Prefixed(
            prefix = "bash",
            arguments = TextFieldValue("pwd"),
        )

        assertEquals("bash pwd", state.query)
        assertEquals("bash", state.copy(arguments = TextFieldValue("")).query)
    }

    @Test
    fun removingPrefixRestoresPrefixSeparatorAndArgumentsAsPlainQuery() {
        val state = LauncherQueryState.Prefixed(
            prefix = "yt",
            arguments = TextFieldValue("funny cats", selection = TextRange.Zero),
        )

        val plain = state.removePrefix()

        assertEquals("yt funny cats", plain.query)
        assertEquals(TextRange(3), plain.value.selection)
    }

    @Test
    fun removingEmptyPrefixTokenRestoresTrailingSpace() {
        val state = LauncherQueryState.Prefixed(
            prefix = "yt",
            arguments = TextFieldValue("", selection = TextRange.Zero),
        )

        val plain = state.removePrefix()

        assertEquals("yt ", plain.query)
        assertEquals(TextRange(3), plain.value.selection)
    }

    @Test
    fun unmatchedAndResetQueriesStayPlain() {
        val value = TextFieldValue("ordinary search", selection = TextRange(4))

        assertEquals(LauncherQueryState.Plain(value), launcherQueryState(value, null))
        assertEquals("", LauncherQueryState.Plain().query)
    }
}
