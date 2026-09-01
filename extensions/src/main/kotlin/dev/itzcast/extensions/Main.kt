package dev.itzcast.extensions

import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

fun main(arguments: Array<String>) = runBlocking {
    val extension = arguments.singleOrNull() ?: error("Expected an extension name")
    System.`in`.bufferedReader().use { requests ->
        while (true) {
            val payload = requests.readLine() ?: break
            if (payload.isBlank()) continue
            val request = json.decodeFromString<ExtensionRequest>(payload)
            println(json.encodeToString(OfficialExtensionHost.handle(extension, request)))
            System.out.flush()
        }
    }
}

object OfficialExtensionHost {
    private val extensions = mapOf(
        "applications" to ApplicationsExtension(),
        "bash" to BashExtension,
        "calculator" to CalculatorExtension,
        "youtube" to YouTubeExtension,
        "web-search" to WebSearchExtension,
    )

    suspend fun handle(extension: String, request: ExtensionRequest): ExtensionResponse =
        extensions[extension]?.handle(request) ?: error("Unknown official extension: $extension")
}

internal fun interface OfficialExtension {
    suspend fun handle(request: ExtensionRequest): ExtensionResponse
}
