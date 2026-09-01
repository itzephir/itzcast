package dev.itzcast.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ExtensionRequest {
    @Serializable
    @SerialName("startup")
    data object Startup : ExtensionRequest

    @Serializable
    @SerialName("launch")
    data class Launch(val context: LaunchContext) : ExtensionRequest

    @Serializable
    @SerialName("prefix")
    data class Prefix(val context: QueryContext, val match: PrefixMatchDto) : ExtensionRequest

    @Serializable
    @SerialName("suggest")
    data class Suggest(val context: QueryContext) : ExtensionRequest

    @Serializable
    @SerialName("use")
    data class Use(val event: UseEvent) : ExtensionRequest
}

@Serializable
data class PrefixMatchDto(val prefix: String, val arguments: String) {
    constructor(match: PrefixMatch) : this(match.prefix, match.arguments)
}

@Serializable
data class ExtensionResponse(
    val launchContext: LaunchContext? = null,
    val suggestions: List<Suggestion> = emptyList(),
)
