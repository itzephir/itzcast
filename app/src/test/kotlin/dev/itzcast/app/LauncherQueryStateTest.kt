package dev.itzcast.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.itzcast.core.PrefixMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LauncherQueryStateTest {
    @Test
    fun typingPrefixCharacterByCharacterStaysPlain() {
        val initial = LauncherQueryState.Plain()
        val y = assertIs<LauncherQueryState.Plain>(
            initial.update(TextFieldValue("y", selection = TextRange(1)), null),
        )

        val yt = y.update(
            TextFieldValue("yt", selection = TextRange(2)),
            PrefixMatch("yt", ""),
        )

        assertEquals(
            LauncherQueryState.Plain(TextFieldValue("yt", selection = TextRange(2))),
            yt,
        )
    }

    @Test
    fun exactPrefixWaitsForSeparator() {
        val value = TextFieldValue("yt", selection = TextRange(2))

        val state = LauncherQueryState.Plain().update(value, PrefixMatch("yt", ""))

        assertEquals(LauncherQueryState.Plain(value), state)
    }

    @Test
    fun typingSeparatorAfterExactPrefixActivatesToken() {
        val state = LauncherQueryState.Plain(
            TextFieldValue("yt", selection = TextRange(2)),
        ).update(
            TextFieldValue("yt ", selection = TextRange(3)),
            PrefixMatch("yt", ""),
        )

        val prefixed = assertIs<LauncherQueryState.Prefixed>(state)
        assertEquals("yt", prefixed.prefix)
        assertEquals("", prefixed.arguments.text)
        assertEquals(TextRange.Zero, prefixed.arguments.selection)
        assertEquals("yt", prefixed.query)
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
    fun removedPrefixStaysPlainWhileTypingArguments() {
        val removed = LauncherQueryState.Prefixed(
            prefix = "yt",
            arguments = TextFieldValue("", selection = TextRange.Zero),
        ).removePrefix()
        val value = TextFieldValue("yt cats", selection = TextRange(7))

        val state = removed.update(value, PrefixMatch("yt", "cats"))

        val plain = assertIs<LauncherQueryState.Plain>(state)
        assertEquals("yt cats", plain.query)
    }

    @Test
    fun editingPlainTextCanStartANewPrefixTransition() {
        val edited = LauncherQueryState.Plain(
            TextFieldValue("yt ", selection = TextRange(3)),
        ).update(
            TextFieldValue("bash", selection = TextRange(4)),
            PrefixMatch("bash", ""),
        )
        val plain = assertIs<LauncherQueryState.Plain>(edited)

        val state = plain.update(
            TextFieldValue("bash ", selection = TextRange(5)),
            PrefixMatch("bash", ""),
        )

        assertIs<LauncherQueryState.Prefixed>(state)
    }

    @Test
    fun unmatchedAndResetQueriesStayPlain() {
        val value = TextFieldValue("ordinary search", selection = TextRange(4))

        assertEquals(LauncherQueryState.Plain(value), LauncherQueryState.Plain().update(value, null))
        assertEquals("", LauncherQueryState.Plain().query)
    }
}
