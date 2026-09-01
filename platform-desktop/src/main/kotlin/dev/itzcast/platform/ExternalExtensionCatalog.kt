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
import dev.itzcast.core.StartupHook
import dev.itzcast.core.SuggestHook
import dev.itzcast.core.Suggestion
import dev.itzcast.core.UseEvent
import dev.itzcast.core.UseHook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
) : AutoCloseable {
    private val lifecycleLock = Any()
    private var loadedExtensions: List<ExternalExtension>? = null
    private var closed = false

    fun load(): List<ItzExtension> = synchronized(lifecycleLock) {
        check(!closed) { "Extension catalog is closed" }
        loadedExtensions ?: discover().also { loadedExtensions = it }
    }

    private fun discover(): List<ExternalExtension> {
        root.createDirectories()
        val extensionsDirectory = root.resolve("extensions").also(Path::createDirectories)
        return Files.list(extensionsDirectory).use { entries ->
            entries.iterator().asSequence()
                .filter(Path::isDirectory)
                .mapNotNull { directory -> loadExtension(directory) }
                .toList()
        }
    }

    override fun close() {
        val extensions = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            loadedExtensions.orEmpty().also { loadedExtensions = null }
        }
        extensions.forEach(ExternalExtension::close)
    }

    private fun loadExtension(directory: Path): ExternalExtension? {
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
) : ItzExtension, AutoCloseable {
    override val id = manifest.id
    private val invocationMutex = Mutex()
    private val sessionLock = Any()
    private var session: ProcessSession? = null
    private var startupRequested = false
    private var closed = false

    override val hooks: List<ExtensionHook> = buildList {
        if (HookType.STARTUP in manifest.hooks) add(object : StartupHook {
            override val extensionId = id
            override val priority = manifest.priority
            override suspend fun onStartup() {
                invoke(ExtensionRequest.Startup)
            }
        })
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

    private suspend fun invoke(request: ExtensionRequest): ExtensionResponse = invocationMutex.withLock {
        check(!closed) { "Extension $id is closed" }
        if (request is ExtensionRequest.Startup) startupRequested = true

        var mayRetryWrite = true
        while (true) {
            val active = ensureSession()
            try {
                if (request !is ExtensionRequest.Startup && startupRequested && !active.startupSent) {
                    send(active, ExtensionRequest.Startup)
                    active.startupSent = true
                }
                return@withLock send(active, request).also {
                    if (request is ExtensionRequest.Startup) active.startupSent = true
                }
            } catch (error: RequestWriteException) {
                discardSession()
                if (!mayRetryWrite) throw error.cause ?: error
                mayRetryWrite = false
            } catch (error: Throwable) {
                discardSession()
                throw error
            }
        }
        error("Unreachable")
    }

    private fun ensureSession(): ProcessSession = synchronized(sessionLock) {
        check(!closed) { "Extension $id is closed" }
        session?.takeIf { it.process.isAlive }?.let { return@synchronized it }
        session?.let(::closeSession)
        session = null

        val process = ProcessBuilder(resolvedCommand())
            .directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .apply { environment().putAll(manifest.environment) }
            .start()
        return ProcessSession(
            process = process,
            writer = process.outputStream.bufferedWriter(),
            reader = process.inputStream.bufferedReader(),
            readerExecutor = Executors.newSingleThreadExecutor { task ->
                Thread(task, "itzcast-extension-$id-reader").apply { isDaemon = true }
            },
        ).also { session = it }
    }

    private suspend fun send(active: ProcessSession, request: ExtensionRequest): ExtensionResponse {
        try {
            withContext(Dispatchers.IO) {
                active.writer.write(json.encodeToString<ExtensionRequest>(request))
                active.writer.newLine()
                active.writer.flush()
            }
        } catch (error: IOException) {
            throw RequestWriteException(error)
        }

        val response = runInterruptible(Dispatchers.IO) {
            val read = active.readerExecutor.submit(Callable { active.reader.readLine() })
            try {
                read.get(manifest.timeoutMs, TimeUnit.MILLISECONDS)
            } catch (error: TimeoutException) {
                read.cancel(true)
                throw IllegalStateException("Extension timed out", error)
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
        check(response != null) {
            if (active.process.isAlive) "Extension closed its output"
            else "Extension exited with ${active.process.exitValue()}"
        }
        return if (response.isBlank()) ExtensionResponse()
        else json.decodeFromString<ExtensionResponse>(response)
    }

    private fun resolvedCommand(): List<String> = manifest.command.mapIndexed { index, part ->
        when {
            index == 0 && part == "@java" -> Path.of(System.getProperty("java.home"), "bin", "java").toString()
            index == 0 && !Path.of(part).isAbsolute -> directory.resolve(part).toString()
            else -> part
        }
    }

    private fun discardSession() {
        val active = synchronized(sessionLock) {
            session?.also { session = null }
        } ?: return
        closeSession(active)
    }

    private fun closeSession(active: ProcessSession) {
        runCatching { active.writer.close() }
        runCatching { active.reader.close() }
        active.readerExecutor.shutdownNow()
        if (active.process.isAlive) active.process.destroy()
        if (active.process.isAlive && !active.process.waitFor(250, TimeUnit.MILLISECONDS)) {
            active.process.destroyForcibly()
        }
    }

    override fun close() {
        val active = synchronized(sessionLock) {
            if (closed) return
            closed = true
            session?.also { session = null }
        } ?: return
        closeSession(active)
    }

    private fun List<Suggestion>.withSource(): List<Suggestion> = map { suggestion ->
        if (suggestion.sourceId == id) suggestion else suggestion.copy(sourceId = id)
    }

    private data class ProcessSession(
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader,
        val readerExecutor: ExecutorService,
        var startupSent: Boolean = false,
    )

    private class RequestWriteException(cause: IOException) : IOException(cause)
}
