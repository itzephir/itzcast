package dev.itzcast.platform

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.LaunchHook
import dev.itzcast.core.Pipeline
import dev.itzcast.core.PrefixHook
import dev.itzcast.core.QueryContext
import dev.itzcast.core.SuggestHook
import dev.itzcast.core.UseHook
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalExtensionCatalogTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun loadsAndInvokesLanguageAgnosticExtension() = runTest {
        val directory = root.resolve("extensions/test.extension").also(Path::createDirectories)
        directory.resolve("manifest.toml").writeText(
            """
            id = "test.extension"
            command = ["./extension.sh"]
            hooks = ["launch", "prefix", "suggest", "use"]
            prefixes = ["ext"]
            timeoutMs = 1000
            """.trimIndent(),
        )
        directory.resolve("extension.sh").apply {
            writeText(
                """
                #!/bin/sh
                read request
                printf '%s\n' '{"suggestions":[{"id":"external:1","title":"External result","score":77.0,"kind":"CUSTOM","action":{"type":"none"}}]}'
                """.trimIndent(),
            )
            check(toFile().setExecutable(true))
        }

        val extensions = ExternalExtensionCatalog(root).load()
        val pipeline = Pipeline(extensions, DesktopActionExecutor())
        val suggestions = pipeline.suggest(QueryContext("anything"))

        assertEquals(1, extensions.size)
        assertEquals(1, extensions.single().hooks.count { it is LaunchHook })
        assertEquals(1, extensions.single().hooks.count { it is PrefixHook })
        assertEquals(1, extensions.single().hooks.count { it is SuggestHook })
        assertEquals(1, extensions.single().hooks.count { it is UseHook })
        assertEquals("External result", suggestions.single().title)
        assertEquals("test.extension", suggestions.single().sourceId)
        assertEquals(ActionSpec.None, suggestions.single().action)
    }
}
