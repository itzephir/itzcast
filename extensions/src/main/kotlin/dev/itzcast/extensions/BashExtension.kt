package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object BashExtension : OfficialExtension {
    override suspend fun handle(request: ExtensionRequest): ExtensionResponse {
        val command = (request as? ExtensionRequest.Prefix)?.match?.arguments?.trim().orEmpty()
        if (command.isEmpty()) return ExtensionResponse()
        val home = System.getProperty("user.home")
        return ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.bash:$command",
                    title = "Run $command",
                    subtitle = "Execute with zsh in $home",
                    score = 100.0,
                    kind = SuggestionKind.COMMAND,
                    action = ActionSpec("itzcast/command", buildJsonObject {
                        put("command", JsonArray(listOf("/bin/zsh", "-lc", command).map(::JsonPrimitive)))
                        put("workingDirectory", home)
                    }),
                    sourceId = "itzcast.bash",
                ),
            ),
        )
    }
}
