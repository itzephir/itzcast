package dev.itzcast.platform

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.LaunchHook
import dev.itzcast.core.Pipeline
import dev.itzcast.core.PrefixHook
import dev.itzcast.core.QueryContext
import dev.itzcast.core.StartupHook
import dev.itzcast.core.SuggestHook
import dev.itzcast.core.UseHook
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                printf '%s\n' '{"suggestions":[{"id":"external:1","title":"External result","score":77.0,"kind":"CUSTOM","action":{"id":"itzcast/none"}}]}'
                """.trimIndent(),
            )
            check(toFile().setExecutable(true))
        }

        ExternalExtensionCatalog(root).use { catalog ->
            val extensions = catalog.load()
            val pipeline = Pipeline(extensions, desktopActions())
            val first = pipeline.suggest(QueryContext("anything"))
            val second = pipeline.suggest(QueryContext("anything else"))

            assertEquals(1, extensions.size)
            assertEquals(1, extensions.single().hooks.count { it is LaunchHook })
            assertEquals(1, extensions.single().hooks.count { it is PrefixHook })
            assertEquals(1, extensions.single().hooks.count { it is SuggestHook })
            assertEquals(1, extensions.single().hooks.count { it is UseHook })
            assertEquals("External result", first.single().title)
            assertEquals("External result", second.single().title)
            assertEquals("test.extension", first.single().sourceId)
            assertEquals(ActionSpec("itzcast/none"), first.single().action)
        }
    }

    @Test
    fun keepsProcessStateBetweenStartupAndSuggestion() = runTest {
        val directory = root.resolve("extensions/test.persistent").also(Path::createDirectories)
        directory.resolve("manifest.toml").writeText(
            """
            id = "test.persistent"
            command = ["./extension.sh"]
            hooks = ["startup", "suggest"]
            timeoutMs = 1000
            """.trimIndent(),
        )
        directory.resolve("extension.sh").apply {
            writeText(
                """
                #!/bin/sh
                started=false
                while IFS= read -r request; do
                    case "${'$'}request" in
                        *'"type":"startup"'*)
                            started=true
                            printf '%s\n' '{"suggestions":[]}'
                            ;;
                        *'"type":"suggest"'*)
                            if [ "${'$'}started" = true ]; then
                                printf '%s\n' '{"suggestions":[{"id":"persistent:1","title":"Prepared","score":1.0,"kind":"CUSTOM","action":{"id":"itzcast/none"}}]}'
                            else
                                exit 2
                            fi
                            ;;
                    esac
                done
                """.trimIndent(),
            )
            check(toFile().setExecutable(true))
        }

        ExternalExtensionCatalog(root).use { catalog ->
            val extensions = catalog.load()
            val pipeline = Pipeline(extensions, desktopActions())

            assertEquals(1, extensions.single().hooks.count { it is StartupHook })
            pipeline.startup()

            assertEquals("Prepared", pipeline.suggest(QueryContext("query")).single().title)
        }
    }

    @Test
    fun restartsTimedOutProcessAndReplaysStartup() = runTest {
        val directory = root.resolve("extensions/test.restart").also(Path::createDirectories)
        directory.resolve("manifest.toml").writeText(
            """
            id = "test.restart"
            command = ["./extension.sh"]
            hooks = ["startup", "suggest"]
            timeoutMs = 1000
            """.trimIndent(),
        )
        directory.resolve("extension.sh").apply {
            writeText(
                """
                #!/bin/sh
                started=false
                while IFS= read -r request; do
                    case "${'$'}request" in
                        *'"type":"startup"'*)
                            started=true
                            printf '%s\n' '{"suggestions":[]}'
                            ;;
                        *hang*)
                            sleep 2
                            ;;
                        *'"type":"suggest"'*)
                            if [ "${'$'}started" = true ]; then
                                printf '%s\n' '{"suggestions":[{"id":"restart:1","title":"Recovered","score":1.0,"kind":"CUSTOM","action":{"id":"itzcast/none"}}]}'
                            else
                                exit 2
                            fi
                            ;;
                    esac
                done
                """.trimIndent(),
            )
            check(toFile().setExecutable(true))
        }

        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), desktopActions())
            pipeline.startup()

            assertTrue(pipeline.suggest(QueryContext("hang")).isEmpty())
            assertEquals("Recovered", pipeline.suggest(QueryContext("recover")).single().title)
        }
    }
}
