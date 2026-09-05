package dev.itzcast.app

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import dev.itzcast.core.ActionOutcome
import dev.itzcast.core.ActionSpec
import dev.itzcast.core.Pipeline
import dev.itzcast.core.QueryContext
import dev.itzcast.platform.BundledExtensionInstaller
import dev.itzcast.platform.ExternalExtensionCatalog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledExtensionsIntegrationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun officialCatalogInstallsAndRunsThroughPublicProtocol() = runTest {
        BundledExtensionInstaller(root).install().getOrThrow()
        ExternalExtensionCatalog(root).use { catalog ->
            val extensions = catalog.load()

            assertEquals(
                setOf(
                    "itzcast.applications",
                    "itzcast.bash",
                    "itzcast.calculator",
                    "itzcast.counter",
                    "itzcast.youtube",
                    "itzcast.web-search",
                ),
                extensions.map { it.id }.toSet(),
            )

            val pipeline = Pipeline(extensions, dev.itzcast.platform.desktopActions())
            pipeline.startup()
            val calculation = pipeline.suggest(QueryContext("--2 ^ 3 ^ 2 % 10"))
            assertTrue(calculation.any { it.sourceId == "itzcast.calculator" && it.title == "2" })

            val youtube = pipeline.suggest(QueryContext("yt funny cats"))
            val action = youtube.first { it.sourceId == "itzcast.youtube" }.action
            assertTrue(action.payload.getValue("url").jsonPrimitive.content.endsWith("funny%20cats"))

            val bash = pipeline.suggest(QueryContext("bash ls -la"))
            val command = bash.first { it.sourceId == "itzcast.bash" }.action
            assertEquals(listOf("/bin/zsh", "-lc", "ls -la"), command.payload.getValue("command").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun bundledCounterExecutesWithoutPayloadAndRefreshesItsPersistentState() = runTest {
        BundledExtensionInstaller(root).install().getOrThrow()
        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), dev.itzcast.platform.desktopActions())
            pipeline.startup()
            repeat(3) { value ->
                val suggestion = pipeline.suggest(QueryContext("count")).first { it.sourceId == "itzcast.counter" }
                assertEquals("Counter: $value", suggestion.title)
                assertEquals(ActionSpec("itzcast.counter/increment"), suggestion.action)
                assertEquals(ActionOutcome.REFRESH, pipeline.use("count", suggestion).getOrThrow())
            }
        }
        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), dev.itzcast.platform.desktopActions())
            assertEquals("Counter: 0", pipeline.suggest(QueryContext("count")).first { it.sourceId == "itzcast.counter" }.title)
        }
    }

}
