package dev.itzcast.extensions

import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionResult
import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion

/** A bundled demonstration of a stateful action; nothing is persisted to disk. */
internal class CounterExtension : OfficialExtension {
    private var count = 0L

    override suspend fun handle(request: ExtensionRequest): ExtensionResponse = when (request) {
        is ExtensionRequest.Prefix -> ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.counter:value",
                    title = "Counter: $count",
                    subtitle = "Press Enter to increment; the window stays open",
                    score = 100.0,
                    action = ActionSpec("itzcast.counter/increment"),
                    sourceId = "itzcast.counter",
                ),
            ),
        )
        is ExtensionRequest.Execute -> {
            if (request.id == "itzcast.counter/increment") {
                count++
                ExtensionResponse(actionResult = ActionResult(succeeded = true, outcome = ActionOutcome.REFRESH))
            } else {
                ExtensionResponse(actionResult = ActionResult(succeeded = false, error = "Unknown counter action"))
            }
        }
        else -> ExtensionResponse()
    }
}
