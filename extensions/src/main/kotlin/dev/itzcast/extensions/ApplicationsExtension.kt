package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.FuzzyMatcher
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal class ApplicationsExtension(
    private val loader: suspend () -> List<Application> = { loadApplications() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : OfficialExtension {
    private val applications = scope.async(start = CoroutineStart.LAZY) { loader() }

    override suspend fun handle(request: ExtensionRequest): ExtensionResponse = when (request) {
        ExtensionRequest.Startup -> {
            applications.start()
            ExtensionResponse()
        }

        is ExtensionRequest.Suggest -> suggest(request)
        else -> ExtensionResponse()
    }

    private suspend fun suggest(request: ExtensionRequest.Suggest): ExtensionResponse {
        val query = request.context.query.trim()
        if (query.isEmpty()) return ExtensionResponse()

        val suggestions = applications.await()
            .mapNotNull { application ->
                val score = FuzzyMatcher.score(query, application.name)
                if (!score.isFinite()) null else Suggestion(
                    id = "itzcast.applications:${application.path}",
                    title = application.name,
                    subtitle = application.path.toString(),
                    score = score,
                    kind = SuggestionKind.APPLICATION,
                    action = ActionSpec.OpenPath(application.path.toString()),
                    sourceId = "itzcast.applications",
                )
            }
            .sortedByDescending(Suggestion::score)
            .take(10)
        return ExtensionResponse(suggestions = suggestions)
    }

}

private fun loadApplications(): List<Application> = applicationRoots()
    .flatMap(::scanApplications)
    .distinctBy(Application::path)

private fun applicationRoots(): List<Path> {
    val home = Path.of(System.getProperty("user.home"))
    return listOf(Path.of("/Applications"), Path.of("/System/Applications"), home.resolve("Applications"))
}

private fun scanApplications(root: Path): List<Application> {
    if (!root.isDirectory()) return emptyList()
    return Files.walk(root, 3).use { paths ->
        paths.filter { it.fileName.toString().endsWith(".app", ignoreCase = true) }
            .map { Application(it.fileName.toString().removeSuffix(".app"), it) }
            .toList()
    }
}

internal data class Application(val name: String, val path: Path)
