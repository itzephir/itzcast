package dev.itzcast.extensions

import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.PrefixMatchDto
import dev.itzcast.core.QueryContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CounterExtensionTest {
    private val prefix = ExtensionRequest.Prefix(QueryContext("count"), PrefixMatchDto("count", ""))

    @Test
    fun incrementsWithoutPayloadAndRequestsRefresh() = runTest {
        val extension = CounterExtension()
        repeat(3) { value ->
            val suggestion = extension.handle(prefix).suggestions.single()
            assertEquals("Counter: $value", suggestion.title)
            assertEquals(ActionSpec("itzcast.counter/increment"), suggestion.action)
            assertEquals("itzcast.counter", suggestion.sourceId)
            val result = extension.handle(ExtensionRequest.Execute(suggestion.action.id, "count", suggestion)).actionResult!!
            assertTrue(result.succeeded)
            assertEquals(ActionOutcome.REFRESH, result.outcome)
        }
        assertEquals("Counter: 0", CounterExtension().handle(prefix).suggestions.single().title)
    }

    @Test
    fun unknownActionsAndUnrelatedHooksDoNotIncrement() = runTest {
        val extension = CounterExtension()
        val initial = extension.handle(prefix).suggestions.single()
        val result = extension.handle(ExtensionRequest.Execute("itzcast.counter/unknown", "count", initial)).actionResult!!
        assertFalse(result.succeeded)
        assertEquals("Unknown counter action", result.error)
        assertTrue(extension.handle(ExtensionRequest.Suggest(QueryContext("anything"))).suggestions.isEmpty())
        assertEquals(initial, extension.handle(prefix).suggestions.single())
    }
}
