package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind

internal object YouTubeExtension : OfficialExtension {
    override suspend fun handle(request: ExtensionRequest): ExtensionResponse {
        val arguments = (request as? ExtensionRequest.Prefix)?.match?.arguments?.trim().orEmpty()
        if (arguments.isEmpty()) return ExtensionResponse()
        return ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.youtube:$arguments",
                    title = "Search YouTube for “$arguments”",
                    subtitle = "youtube.com",
                    score = 95.0,
                    kind = SuggestionKind.WEB,
                    action = ActionSpec.OpenUrl("https://www.youtube.com/results?search_query=${urlEncode(arguments)}"),
                    sourceId = "itzcast.youtube",
                ),
            ),
        )
    }
}
