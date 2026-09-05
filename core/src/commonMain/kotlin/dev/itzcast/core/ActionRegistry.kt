package dev.itzcast.core

fun interface ActionHandler {
    suspend fun execute(context: ActionContext): ActionOutcome
}

data class ActionContext(val action: ActionSpec, val query: String, val suggestion: Suggestion)

data class ActionRegistration(val id: String, val handler: ActionHandler)

/** Immutable for the lifetime of a pipeline; every action takes the same dispatch path. */
class ActionRegistry(registrations: List<ActionRegistration>) {
    private val handlers: Map<String, ActionHandler>

    init {
        require(registrations.all { isActionId(it.id) }) { "Invalid action ID" }
        require(registrations.map { it.id }.distinct().size == registrations.size) { "Duplicate action ID" }
        handlers = registrations.associate { it.id to it.handler }
    }

    fun contains(id: String): Boolean = id in handlers

    suspend fun execute(context: ActionContext): ActionOutcome {
        val handler = handlers[context.action.id] ?: error("Unknown action: ${context.action.id}")
        return handler.execute(context)
    }
}

fun isActionName(value: String): Boolean = value.isNotBlank() && value == value.trim() && '/' !in value

fun isActionId(value: String): Boolean = value.split('/').let { it.size == 2 && it.all(::isActionName) }
