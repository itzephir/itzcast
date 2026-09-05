package dev.itzcast.platform

import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Decode items separately so a malformed action cannot discard its valid neighbours. */
internal fun decodeExtensionResponse(json: Json, response: String): ExtensionResponse {
    val objectValue = json.parseToJsonElement(response) as? JsonObject ?: error("Expected an extension response object")
    val items = objectValue["suggestions"]?.let {
        it as? JsonArray ?: error("suggestions must be an array")
    } ?: JsonArray(emptyList())
    val envelope = json.decodeFromJsonElement<ExtensionResponse>(
        JsonObject(objectValue.filterKeys { it != "suggestions" }),
    )
    return envelope.copy(suggestions = items.mapNotNull {
        runCatching { json.decodeFromJsonElement<Suggestion>(it) }.getOrNull()
    })
}
