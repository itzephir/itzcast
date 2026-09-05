package dev.itzcast.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startupHooksRunConcurrentlyOnceAndIsolateFailures() = runTest {
        val release = CompletableDeferred<Unit>()
        val starts = mutableMapOf<String, Int>()
        fun startupHook(id: String, fail: Boolean = false) = object : StartupHook {
            override val extensionId = id
            override suspend fun onStartup() {
                starts[id] = starts.getOrDefault(id, 0) + 1
                if (fail) error("boom")
                release.await()
            }
        }
        val pipeline = Pipeline(
            listOf(
                StaticExtension("first", listOf(startupHook("first"))),
                StaticExtension("second", listOf(startupHook("second"))),
                StaticExtension("broken", listOf(startupHook("broken", fail = true))),
            ),
            RecordingExecutor().registrations,
        )

        val startup = async { pipeline.startup() }
        runCurrent()

        assertEquals(setOf("first", "second", "broken"), starts.keys)
        assertTrue(!startup.isCompleted)

        release.complete(Unit)
        startup.await()
        pipeline.startup()

        assertEquals(mapOf("first" to 1, "second" to 1, "broken" to 1), starts)
    }

    @Test
    fun matchesRegisteredPrefixesForLauncherPresentation() {
        val pipeline = Pipeline(
            listOf(
                StaticExtension(
                    "test.prefix",
                    listOf(prefixHook("git"), prefixHook("git commit")),
                ),
            ),
            RecordingExecutor().registrations,
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
            RecordingExecutor().registrations,
        )

        val suggestions = pipeline.suggest(QueryContext("bash ls -la"))

        assertEquals("ls -la", received?.arguments)
        assertEquals("prefix result", suggestions.single().title)
    }

    @Test
    fun plainQueriesSkipEveryPrefixHookAndKeepNormalSuggestions() = runTest {
        val prefixCalls = mutableListOf<String>()
        val normalQueries = mutableListOf<String>()
        fun hook(id: String) = object : PrefixHook {
            override val extensionId = id
            override val prefixes = setOf("yt", "bash")
            override suspend fun suggest(context: QueryContext, match: PrefixMatch): List<Suggestion> {
                prefixCalls += id
                return listOf(suggestion(id))
            }
        }
        val normal = object : SuggestHook {
            override val extensionId = "normal"
            override suspend fun suggest(context: QueryContext): List<Suggestion> {
                normalQueries += context.query
                return listOf(suggestion("normal"))
            }
        }
        val pipeline = Pipeline(
            listOf(StaticExtension("test", listOf(hook("first"), hook("second"), normal))),
            RecordingExecutor().registrations,
        )

        for (query in listOf("yt cats", "bash pwd")) {
            val context = QueryContext(query)
            assertEquals(3, pipeline.suggest(context).size)
            prefixCalls.clear()

            assertEquals(
                listOf("normal"),
                pipeline.suggest(context, includePrefixSuggestions = false).map { it.title },
            )
            assertTrue(prefixCalls.isEmpty())
            assertEquals(query, normalQueries.last())

            assertEquals(3, pipeline.suggest(context, includePrefixSuggestions = true).size)
            assertEquals(setOf("first", "second"), prefixCalls.toSet())
        }
    }

    @Test
    fun useHooksRunBeforeAndAfterAction() = runTest {
        val events = mutableListOf<UseEvent>()
        val hook = object : UseHook {
            override val extensionId = "test.metrics"
            override suspend fun onUse(event: UseEvent) { events += event }
        }
        val executor = RecordingExecutor()
        val pipeline = Pipeline(listOf(StaticExtension("test", listOf(hook))), executor.registrations)
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
            RecordingExecutor().registrations,
        )

        assertEquals("still works", pipeline.suggest(QueryContext("query")).single().title)
    }

    private fun suggestion(title: String) = Suggestion(
        id = title,
        title = title,
        action = ActionSpec("itzcast/none"),
        sourceId = "test",
    )

    private fun prefixHook(vararg prefixes: String) = object : PrefixHook {
        override val extensionId = "test.${prefixes.joinToString(".")}"
        override val prefixes = prefixes.toSet()
        override suspend fun suggest(context: QueryContext, match: PrefixMatch) = emptyList<Suggestion>()
    }

    private class RecordingExecutor {
        val actions = mutableListOf<ActionSpec>()
        val registrations = listOf(ActionRegistration("itzcast/none") {
            actions += it.action
            ActionOutcome.CLOSE
        })
    }
}
