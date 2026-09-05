package dev.itzcast.platform

import dev.itzcast.core.ActionHandler
import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** Platform implementations registered alongside extension actions. */
fun desktopActions(): List<ActionRegistration> = listOf(
    desktopAction("openUrl") { Desktop.getDesktop().browse(URI(it.string("url"))) },
    desktopAction("openPath") { ProcessBuilder("open", it.string("path")).start() },
    desktopAction("command") { payload ->
        val command = (payload["command"] as? JsonArray)?.map { it.stringValue("command") }
            ?: error("command must be an array of strings")
        require(command.isNotEmpty()) { "Command cannot be empty" }
        ProcessBuilder(command)
            .apply {
                if ("workingDirectory" in payload) directory(File(payload.string("workingDirectory")))
                if ("environment" in payload) {
                    val environment = payload["environment"] as? JsonObject
                        ?: error("environment must be an object")
                    environment().putAll(environment.mapValues { it.value.stringValue("environment") })
                }
            }
            .inheritIO()
            .start()
    },
    desktopAction("copy") {
        val text = it.string("text")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    },
    desktopAction("none") { },
)

private fun desktopAction(id: String, run: (JsonObject) -> Unit) = ActionRegistration(
    "itzcast/$id",
    ActionHandler { context ->
        withContext(Dispatchers.IO) { run(context.action.payload) }
        ActionOutcome.CLOSE
    },
)

private fun JsonObject.string(key: String): String = get(key).stringValue(key)

private fun kotlinx.serialization.json.JsonElement?.stringValue(key: String): String {
    val value = this as? JsonPrimitive
    require(value != null && value.isString) { "$key must be a string" }
    return value.content
}
