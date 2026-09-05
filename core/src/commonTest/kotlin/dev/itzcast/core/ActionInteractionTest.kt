package dev.itzcast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ActionInteractionTest {
    private val view = ActionViewContext(1, "query", true)

    @Test
    fun acceptsEachOutcomeAndAllowsNextActivation() {
        ActionOutcome.entries.forEach { outcome ->
            val interaction = ActionInteraction()
            val ticket = assertNotNull(interaction.begin(view))
            assertNull(interaction.begin(view))
            assertEquals(ActionCompletion(outcome), interaction.finish(ticket, Result.success(outcome), view))
            assertNotNull(interaction.begin(view))
        }
    }

    @Test
    fun failureReturnsErrorWithoutClosing() {
        val interaction = ActionInteraction()
        val ticket = assertNotNull(interaction.begin(view))
        assertEquals(ActionCompletion(error = "Failed"),
            interaction.finish(ticket, Result.failure(IllegalStateException("Failed")), view))
    }

    @Test
    fun ignoresHiddenReopenedAndChangedQueryResults() {
        listOf(view.copy(visible = false), view.copy(session = 2), view.copy(query = "different")).forEach { next ->
            val interaction = ActionInteraction()
            val ticket = assertNotNull(interaction.begin(view))
            assertNull(interaction.finish(ticket, Result.success(ActionOutcome.CLOSE), next))
        }
    }

    @Test
    fun changingAwayAndBackStillInvalidatesOldCompletion() {
        val interaction = ActionInteraction()
        val ticket = assertNotNull(interaction.begin(view))
        interaction.update(view.copy(query = "other"))
        interaction.update(view)
        assertNull(interaction.begin(view))
        assertNull(interaction.finish(ticket, Result.success(ActionOutcome.REFRESH), view))
        assertNotNull(interaction.begin(view))
    }

    @Test
    fun cannotActivateHiddenViewAndCanReleaseCancelledRequest() {
        val interaction = ActionInteraction()
        assertNull(interaction.begin(view.copy(visible = false)))
        val ticket = assertNotNull(interaction.begin(view))
        interaction.abandon(ticket)
        assertNotNull(interaction.begin(view))
        assertNull(interaction.finish(ticket, Result.success(ActionOutcome.CLOSE), view))
    }
}
