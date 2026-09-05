package dev.itzcast.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionRegistration
import dev.itzcast.core.ActionSpec
import dev.itzcast.core.Pipeline
import dev.itzcast.core.PrefixHook
import dev.itzcast.core.PrefixMatch
import dev.itzcast.core.QueryContext
import dev.itzcast.core.StaticExtension
import dev.itzcast.core.SuggestHook
import dev.itzcast.core.Suggestion
import kotlinx.coroutines.test.runTest
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
    fun removingPrefixUpdatesSuggestionsEvenWhenQueryTextIsUnchanged() = runTest {
        for (prefix in listOf("yt", "bash")) {
            val matches = mutableListOf<PrefixMatch>()
            val normalQueries = mutableListOf<String>()
            fun suggestion(id: String) = Suggestion(id, id, action = ActionSpec("test/none"))
            val prefixHook = object : PrefixHook {
                override val extensionId = "test.prefix"
                override val prefixes = setOf(prefix)
                override suspend fun suggest(context: QueryContext, match: PrefixMatch): List<Suggestion> {
                    matches += match
                    return listOf(suggestion("prefix"))
                }
            }
            val normalHook = object : SuggestHook {
                override val extensionId = "test.normal"
                override suspend fun suggest(context: QueryContext): List<Suggestion> {
                    normalQueries += context.query
                    return listOf(suggestion("normal"))
                }
            }
            val pipeline = Pipeline(
                listOf(StaticExtension("test", listOf(prefixHook, normalHook))),
                listOf(ActionRegistration("test/none") { ActionOutcome.CLOSE }),
            )
            suspend fun results(state: LauncherQueryState) = pipeline.suggest(
                QueryContext(state.query),
                includePrefixSuggestions = state.prefixActive,
            ).map { it.id }.toSet()
            val plainPrefix = LauncherQueryState.Plain(TextFieldValue(prefix))
            assertEquals(setOf("normal"), results(plainPrefix))
            val active = assertIs<LauncherQueryState.Prefixed>(
                plainPrefix.update(TextFieldValue("$prefix "), pipeline.matchPrefix("$prefix ")),
            ).copy(arguments = TextFieldValue("cats", selection = TextRange.Zero))
            assertEquals(setOf("normal", "prefix"), results(active))
            assertEquals(listOf(PrefixMatch(prefix, "cats")), matches)

            val removed = active.removePrefix()
            assertEquals(active.query, removed.query)
            assertEquals(TextRange(prefix.length + 1), removed.value.selection)
            assertEquals(setOf("normal"), results(removed))
            assertEquals(removed.query, normalQueries.last())

            val edited = assertIs<LauncherQueryState.Plain>(
                removed.update(TextFieldValue("$prefix dogs"), pipeline.matchPrefix("$prefix dogs")),
            )
            assertEquals(setOf("normal"), results(edited))
            assertEquals(1, matches.size)

            val reset = assertIs<LauncherQueryState.Plain>(
                edited.update(TextFieldValue(prefix), pipeline.matchPrefix(prefix)),
            )
            val reactivated = assertIs<LauncherQueryState.Prefixed>(
                reset.update(TextFieldValue("$prefix "), pipeline.matchPrefix("$prefix ")),
            )
            assertEquals(setOf("normal", "prefix"), results(reactivated))
            assertEquals(PrefixMatch(prefix, ""), matches.last())
            assertEquals(setOf("normal"), results(reactivated.removePrefix()))
            assertEquals(2, matches.size)
        }
    }

    @Test
    fun unmatchedAndResetQueriesStayPlain() {
        val value = TextFieldValue("ordinary search", selection = TextRange(4))

        assertEquals(LauncherQueryState.Plain(value), LauncherQueryState.Plain().update(value, null))
        assertEquals("", LauncherQueryState.Plain().query)
    }
}
