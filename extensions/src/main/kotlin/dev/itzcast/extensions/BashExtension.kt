package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind

internal object BashExtension : OfficialExtension {
    override fun handle(request: ExtensionRequest): ExtensionResponse {
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
                    action = ActionSpec.RunCommand(
                        command = listOf("/bin/zsh", "-lc", command),
                        workingDirectory = home,
                    ),
                    sourceId = "itzcast.bash",
                ),
            ),
        )
    }
}
