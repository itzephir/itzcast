package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object WebSearchExtension : OfficialExtension {
    override suspend fun handle(request: ExtensionRequest): ExtensionResponse {
        val query = (request as? ExtensionRequest.Suggest)?.context?.query?.trim().orEmpty()
        if (query.isEmpty()) return ExtensionResponse()
        return ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.web-search:$query",
                    title = "Search the web for “$query”",
                    subtitle = "Open in the default browser",
                    score = 10.0,
                    kind = SuggestionKind.WEB,
                    action = ActionSpec("itzcast/openUrl", buildJsonObject { put("url", "https://www.google.com/search?q=${urlEncode(query)}") }),
                    sourceId = "itzcast.web-search",
                ),
            ),
        )
    }
}
