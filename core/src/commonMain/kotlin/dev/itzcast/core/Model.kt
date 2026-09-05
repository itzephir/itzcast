package dev.itzcast.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LaunchContext(
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class QueryContext(
    val query: String,
    val launch: LaunchContext = LaunchContext(),
)

@Serializable
enum class SuggestionKind {
    APPLICATION,
    COMMAND,
    CALCULATION,
    WEB,
    CUSTOM,
}

@Serializable
data class Suggestion(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val score: Double = 0.0,
    val kind: SuggestionKind = SuggestionKind.CUSTOM,
    val action: ActionSpec,
    val sourceId: String = "",
)

@Serializable
data class ActionSpec(
    val id: String,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
enum class ActionOutcome {
    @SerialName("close") CLOSE,
    @SerialName("keepOpen") KEEP_OPEN,
    @SerialName("refresh") REFRESH,
}

@Serializable
data class ActionResult(
    val succeeded: Boolean,
    val error: String? = null,
    val outcome: ActionOutcome = ActionOutcome.CLOSE,
)

@Serializable
enum class UsePhase { BEFORE, AFTER }

@Serializable
data class UseEvent(
    val phase: UsePhase,
    val query: String,
    val suggestion: Suggestion,
    val succeeded: Boolean? = null,
    val error: String? = null,
)

data class PrefixMatch(
    val prefix: String,
    val arguments: String,
)
