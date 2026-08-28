package dev.itzcast.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineTest {
    @Test
    fun matchesRegisteredPrefixesForLauncherPresentation() {
        val pipeline = Pipeline(
            listOf(
                StaticExtension(
                    "test.prefix",
                    listOf(prefixHook("git"), prefixHook("git commit")),
                ),
            ),
            RecordingExecutor(),
        )

        assertEquals(PrefixMatch("git", ""), pipeline.matchPrefix("git"))
        assertEquals(PrefixMatch("git", "status"), pipeline.matchPrefix("  git   status"))
        assertEquals(PrefixMatch("git commit", "-m message"), pipeline.matchPrefix("git commit -m message"))
        assertNull(pipeline.matchPrefix("github"))
    }

    @Test
    fun prefixHookReceivesArguments() = runTest {
        var received: PrefixMatch? = null
        val prefixHook = object : PrefixHook {
            override val extensionId = "test.prefix"
            override val prefixes = setOf("bash")
            override suspend fun suggest(context: QueryContext, match: PrefixMatch): List<Suggestion> {
                received = match
                return listOf(suggestion("prefix result"))
            }
        }
        val pipeline = Pipeline(
            listOf(StaticExtension("test.prefix", listOf(prefixHook))),
            RecordingExecutor(),
        )

        val suggestions = pipeline.suggest(QueryContext("bash ls -la"))

        assertEquals("ls -la", received?.arguments)
        assertEquals("prefix result", suggestions.single().title)
    }

    @Test
    fun useHooksRunBeforeAndAfterAction() = runTest {
        val events = mutableListOf<UseEvent>()
        val hook = object : UseHook {
            override val extensionId = "test.metrics"
            override suspend fun onUse(event: UseEvent) { events += event }
        }
        val executor = RecordingExecutor()
        val pipeline = Pipeline(listOf(StaticExtension("test", listOf(hook))), executor)
        val suggestion = suggestion("example")

        val result = pipeline.use("example", suggestion)

        assertTrue(result.isSuccess)
        assertEquals(listOf(UsePhase.BEFORE, UsePhase.AFTER), events.map(UseEvent::phase))
        assertEquals(true, events.last().succeeded)
        assertEquals(listOf(suggestion.action), executor.actions)
    }

    @Test
    fun brokenExtensionsAreIsolated() = runTest {
        val broken = object : SuggestHook {
            override val extensionId = "test.broken"
            override suspend fun suggest(context: QueryContext): List<Suggestion> = error("boom")
        }
        val working = object : SuggestHook {
            override val extensionId = "test.working"
            override suspend fun suggest(context: QueryContext) = listOf(suggestion("still works"))
        }
        val pipeline = Pipeline(
            listOf(StaticExtension("broken", listOf(broken)), StaticExtension("working", listOf(working))),
            RecordingExecutor(),
        )

        assertEquals("still works", pipeline.suggest(QueryContext("query")).single().title)
    }

    private fun suggestion(title: String) = Suggestion(
        id = title,
        title = title,
        action = ActionSpec.None,
        sourceId = "test",
    )

    private fun prefixHook(vararg prefixes: String) = object : PrefixHook {
        override val extensionId = "test.${prefixes.joinToString(".")}"
        override val prefixes = prefixes.toSet()
        override suspend fun suggest(context: QueryContext, match: PrefixMatch) = emptyList<Suggestion>()
    }

    private class RecordingExecutor : ActionExecutor {
        val actions = mutableListOf<ActionSpec>()
        override suspend fun execute(action: ActionSpec) { actions += action }
    }
}
