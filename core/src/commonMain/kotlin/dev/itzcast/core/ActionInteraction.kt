package dev.itzcast.core

/** Identity of the results currently shown by a launcher window. */
data class ActionViewContext(val session: Int, val query: String, val visible: Boolean)

data class ActionCompletion(val outcome: ActionOutcome? = null, val error: String? = null)

/** Prevents duplicate activation and ignores results belonging to an obsolete view. */
class ActionInteraction {
    class Ticket internal constructor(internal val generation: Long)

    private var context: ActionViewContext? = null
    private var generation = 0L
    private var active: Ticket? = null

    fun update(view: ActionViewContext) {
        if (context != view) {
            context = view
            generation++
        }
    }

    fun begin(view: ActionViewContext): Ticket? {
        update(view)
        if (active != null || !view.visible) return null
        return Ticket(generation).also { active = it }
    }

    fun finish(ticket: Ticket, result: Result<ActionOutcome>, view: ActionViewContext): ActionCompletion? {
        update(view)
        if (active !== ticket) return null
        active = null
        if (ticket.generation != generation || !view.visible) return null
        return result.fold(
            onSuccess = { ActionCompletion(outcome = it) },
            onFailure = { ActionCompletion(error = it.message ?: "Action failed") },
        )
    }

    fun abandon(ticket: Ticket) {
        if (active === ticket) active = null
    }
}
