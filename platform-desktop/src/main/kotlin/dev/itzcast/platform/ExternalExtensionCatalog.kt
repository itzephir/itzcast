package dev.itzcast.platform

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import dev.itzcast.core.ExtensionHook
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.ItzExtension
import dev.itzcast.core.LaunchContext
import dev.itzcast.core.LaunchHook
import dev.itzcast.core.PrefixHook
import dev.itzcast.core.PrefixMatch
import dev.itzcast.core.PrefixMatchDto
import dev.itzcast.core.QueryContext
import dev.itzcast.core.SuggestHook
import dev.itzcast.core.Suggestion
import dev.itzcast.core.UseEvent
import dev.itzcast.core.UseHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

class ExternalExtensionCatalog(
    private val root: Path,
    private val toml: Toml = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true)),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    },
) {
    fun load(): List<ItzExtension> {
        root.createDirectories()
        val extensionsDirectory = root.resolve("extensions").also(Path::createDirectories)
        return Files.list(extensionsDirectory).use { entries ->
            entries.iterator().asSequence()
                .filter(Path::isDirectory)
                .mapNotNull { directory -> loadExtension(directory) }
                .toList()
        }
    }

    private fun loadExtension(directory: Path): ItzExtension? {
        val tomlManifest = directory.resolve("manifest.toml")
        if (!tomlManifest.exists()) return null
        val manifest = runCatching {
            toml.decodeFromString<ExtensionManifestConfig>(tomlManifest.readText()).toManifest()
        }.getOrNull() ?: return null
        if (!manifest.enabled || manifest.command.isEmpty()) return null
        return ExternalExtension(directory, manifest, json)
    }
}

@Serializable
private data class ExtensionManifestConfig(
    val id: String,
    val name: String = id,
    val version: String = "0.1.0",
    val enabled: Boolean = true,
    val command: List<String>,
    val hooks: List<String> = emptyList(),
    val prefixes: List<String> = emptyList(),
    val priority: Int = 0,
    val timeoutMs: Long = 2_000,
    val environment: Map<String, String> = emptyMap(),
) {
    fun toManifest() = ExtensionManifest(
        id = id,
        name = name,
        version = version,
        enabled = enabled,
        command = command,
        hooks = hooks.map { HookType.valueOf(it.trim().uppercase()) }.toSet(),
        prefixes = prefixes.toSet(),
        priority = priority,
        timeoutMs = timeoutMs,
        environment = environment,
    )
}

private class ExternalExtension(
    private val directory: Path,
    private val manifest: ExtensionManifest,
    private val json: Json,
) : ItzExtension {
    override val id = manifest.id
    override val hooks: List<ExtensionHook> = buildList {
        if (HookType.LAUNCH in manifest.hooks) add(object : LaunchHook {
            override val extensionId = id
            override val priority = manifest.priority
            override suspend fun onLaunch(context: LaunchContext): LaunchContext =
                invoke(ExtensionRequest.Launch(context)).launchContext ?: context
        })
        if (HookType.PREFIX in manifest.hooks) add(object : PrefixHook {
            override val extensionId = id
            override val priority = manifest.priority
            override val prefixes = manifest.prefixes
            override suspend fun suggest(context: QueryContext, match: PrefixMatch): List<Suggestion> =
                invoke(ExtensionRequest.Prefix(context, PrefixMatchDto(match))).suggestions.withSource()
        })
        if (HookType.SUGGEST in manifest.hooks) add(object : SuggestHook {
            override val extensionId = id
            override val priority = manifest.priority
            override suspend fun suggest(context: QueryContext): List<Suggestion> =
                invoke(ExtensionRequest.Suggest(context)).suggestions.withSource()
        })
        if (HookType.USE in manifest.hooks) add(object : UseHook {
            override val extensionId = id
            override val priority = manifest.priority
            override suspend fun onUse(event: UseEvent) {
                invoke(ExtensionRequest.Use(event))
            }
        })
    }

    private suspend fun invoke(request: ExtensionRequest): ExtensionResponse = withContext(Dispatchers.IO) {
        val resolvedCommand = manifest.command.mapIndexed { index, part ->
            when {
                index == 0 && part == "@java" -> Path.of(System.getProperty("java.home"), "bin", "java").toString()
                index == 0 && !Path.of(part).isAbsolute -> directory.resolve(part).toString()
                else -> part
            }
        }
        val process = ProcessBuilder(resolvedCommand)
            .directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .apply { environment().putAll(manifest.environment) }
            .start()
        try {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(json.encodeToString<ExtensionRequest>(request))
                writer.newLine()
            }
            check(process.waitFor(manifest.timeoutMs, TimeUnit.MILLISECONDS)) { "Extension timed out" }
            check(process.exitValue() == 0) { "Extension exited with ${process.exitValue()}" }
            val response = process.inputStream.bufferedReader().use { it.readLine() }
            if (response.isNullOrBlank()) ExtensionResponse()
            else json.decodeFromString<ExtensionResponse>(response)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun List<Suggestion>.withSource(): List<Suggestion> = map { suggestion ->
        if (suggestion.sourceId == id) suggestion else suggestion.copy(sourceId = id)
    }
}
