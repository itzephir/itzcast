package dev.itzcast.platform

import dev.itzcast.core.ActionExecutor
import dev.itzcast.core.ActionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

class DesktopActionExecutor : ActionExecutor {
    override suspend fun execute(action: ActionSpec): Unit = withContext(Dispatchers.IO) {
        when (action) {
            is ActionSpec.OpenUrl -> Desktop.getDesktop().browse(URI(action.url))
            is ActionSpec.OpenPath -> { ProcessBuilder("open", action.path).start() }
            is ActionSpec.RunCommand -> {
                require(action.command.isNotEmpty()) { "Command cannot be empty" }
                ProcessBuilder(action.command)
                    .apply {
                        action.workingDirectory?.let { directory(File(it)) }
                        environment().putAll(action.environment)
                    }
                    .inheritIO()
                    .start()
            }
            is ActionSpec.CopyText -> Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(action.text), null)
            ActionSpec.None -> Unit
        }
        Unit
    }
}
