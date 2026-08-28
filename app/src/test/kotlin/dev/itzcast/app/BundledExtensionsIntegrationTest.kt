package dev.itzcast.app

import dev.itzcast.core.ActionExecutor
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
        val extensions = ExternalExtensionCatalog(root).load()

        assertEquals(
            setOf(
                "itzcast.applications",
                "itzcast.bash",
                "itzcast.calculator",
                "itzcast.youtube",
                "itzcast.web-search",
            ),
            extensions.map { it.id }.toSet(),
        )

        val pipeline = Pipeline(extensions, NoopExecutor)
        val calculation = pipeline.suggest(QueryContext("--2 ^ 3 ^ 2 % 10"))
        assertTrue(calculation.any { it.sourceId == "itzcast.calculator" && it.title == "2" })

        val youtube = pipeline.suggest(QueryContext("yt funny cats"))
        val action = youtube.first { it.sourceId == "itzcast.youtube" }.action as ActionSpec.OpenUrl
        assertTrue(action.url.endsWith("funny%20cats"))

        val bash = pipeline.suggest(QueryContext("bash ls -la"))
        val command = bash.first { it.sourceId == "itzcast.bash" }.action as ActionSpec.RunCommand
        assertEquals(listOf("/bin/zsh", "-lc", "ls -la"), command.command)
    }

    private data object NoopExecutor : ActionExecutor {
        override suspend fun execute(action: ActionSpec) = Unit
    }
}
