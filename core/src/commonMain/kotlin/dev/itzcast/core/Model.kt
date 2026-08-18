package dev.itzcast.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
sealed interface ActionSpec {
    @Serializable
    @SerialName("openUrl")
    data class OpenUrl(val url: String) : ActionSpec

    @Serializable
    @SerialName("openPath")
    data class OpenPath(val path: String) : ActionSpec

    @Serializable
    @SerialName("command")
    data class RunCommand(
        val command: List<String>,
        val workingDirectory: String? = null,
        val environment: Map<String, String> = emptyMap(),
    ) : ActionSpec

    @Serializable
    @SerialName("copy")
    data class CopyText(val text: String) : ActionSpec

    @Serializable
    @SerialName("none")
    data object None : ActionSpec
}

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
