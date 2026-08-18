package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.FuzzyMatcher
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal object ApplicationsExtension : OfficialExtension {
    override fun handle(request: ExtensionRequest): ExtensionResponse {
        val query = (request as? ExtensionRequest.Suggest)?.context?.query?.trim().orEmpty()
        if (query.isEmpty()) return ExtensionResponse()

        val suggestions = applicationRoots().flatMap(::scanApplications)
            .distinctBy(Application::path)
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

    private data class Application(val name: String, val path: Path)
}
