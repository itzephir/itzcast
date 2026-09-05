package dev.itzcast.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionRegistryTest {
    @Test
    fun payloadIsOptionalAndLegacyObjectsAreNotAccepted() {
        assertEquals(ActionSpec("itzcast/none"), Json.decodeFromString<ActionSpec>("""{"id":"itzcast/none"}"""))
        assertEquals("""{"id":"itzcast/none"}""", Json.encodeToString(ActionSpec("itzcast/none")))
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<ActionSpec>("""{"type":"none"}""") }
    }

    @Test
    fun rejectsConflictsAndMalformedIds() {
        val handler = ActionHandler { ActionOutcome.CLOSE }
        assertFailsWith<IllegalArgumentException> {
            ActionRegistry(listOf(ActionRegistration("test/run", handler), ActionRegistration("test/run", handler)))
        }
        listOf("", "run", "/run", "test/", "test/a/b", "test/ ").forEach {
            assertFailsWith<IllegalArgumentException> { ActionRegistry(listOf(ActionRegistration(it, handler))) }
        }
    }

    @Test
    fun filtersUnknownActionsBeforeDeduplicationAndLimit() = runTest {
        val valid = suggestion("shared", "itzcast/none")
        val hook = object : SuggestHook {
            override val extensionId = "producer"
            override suspend fun suggest(context: QueryContext) = listOf(
                valid.copy(score = 999.0, action = ActionSpec("missing/action")),
                suggestion("unknown", "provider/undeclared").copy(score = 998.0),
                valid,
            )
        }
        val pipeline = Pipeline(listOf(StaticExtension("producer", listOf(hook))),
            listOf(ActionRegistration("itzcast/none") { ActionOutcome.CLOSE }))
        assertEquals(listOf(valid), pipeline.suggest(QueryContext("query"), limit = 1))
        assertTrue(pipeline.use("query", suggestion("missing", "missing/action")).isFailure)
    }

    @Test
    fun routesToOwnerAndPreservesProducerInUseEvents() = runTest {
        val events = mutableListOf<UseEvent>()
        var received: ActionContext? = null
        val metrics = object : UseHook {
            override val extensionId = "metrics"
            override suspend fun onUse(event: UseEvent) { events += event }
        }
        val action = ActionSpec("provider/run", buildJsonObject { put("value", 42) })
        val item = suggestion("item", action.id).copy(action = action)
        val provider = StaticExtension("provider", emptyList(), listOf(ActionRegistration(action.id) {
            received = it
            ActionOutcome.REFRESH
        }))
        val pipeline = Pipeline(listOf(provider, StaticExtension("metrics", listOf(metrics))))
        assertEquals(ActionOutcome.REFRESH, pipeline.use("query", item).getOrThrow())
        assertEquals(ActionContext(action, "query", item), received)
        assertEquals(listOf(UsePhase.BEFORE, UsePhase.AFTER), events.map { it.phase })
        assertTrue(events.all { it.suggestion.sourceId == "producer" })
        assertEquals(true, events.last().succeeded)
    }

    @Test
    fun reportsHandlerFailuresToUseObservers() = runTest {
        val events = mutableListOf<UseEvent>()
        val hook = object : UseHook {
            override val extensionId = "observer"
            override suspend fun onUse(event: UseEvent) { events += event }
        }
        val pipeline = Pipeline(listOf(StaticExtension("observer", listOf(hook))),
            listOf(ActionRegistration("test/fail") { error("Invalid parameter") }))
        val result = pipeline.use("q", suggestion("item", "test/fail"))
        assertFalse(result.isSuccess)
        assertEquals(false, events.last().succeeded)
        assertEquals("Invalid parameter", events.last().error)
    }

    private fun suggestion(id: String, action: String) = Suggestion(
        id = id, title = id, action = ActionSpec(action), sourceId = "producer",
    )
}
