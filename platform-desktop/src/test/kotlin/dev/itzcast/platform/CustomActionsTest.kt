package dev.itzcast.platform

import dev.itzcast.core.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class CustomActionsTest {
    @TempDir
    lateinit var root: Path
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    @Test
    fun routesBetweenProcessesAndRetainsProviderSessionAndProducerIdentity() = runTest {
        val provider = extension("provider", """
            hooks = ["startup"]
            [[actions]]
            id = "run"
        """, """
            started=false
            while IFS= read -r request; do
                case "${'$'}request" in
                    *'"type":"startup"'*) started=true; printf '%s\n' '{}' ;;
                    *'"type":"execute"'*)
                        [ "${'$'}started" = true ] || exit 2
                        printf '%s\n' "${'$'}request" >> received.txt
                        printf '%s\n' '{"actionResult":{"succeeded":true,"outcome":"refresh"}}'
                        ;;
                esac
            done
        """)
        extension("producer", "hooks = [\"suggest\", \"use\"]", """
            while IFS= read -r request; do
                case "${'$'}request" in
                    *'"type":"suggest"'*) printf '%s\n' '{"suggestions":[{"id":"item","title":"Run","sourceId":"forged","action":{"id":"provider/run","payload":{"value":42}}}]}' ;;
                    *) printf '%s\n' "${'$'}request" >> events.txt; printf '%s\n' '{}' ;;
                esac
            done
        """)
        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), desktopActions())
            pipeline.startup()
            val suggestion = pipeline.suggest(QueryContext("query")).single()
            repeat(2) { assertEquals(ActionOutcome.REFRESH, pipeline.use("query", suggestion).getOrThrow()) }
            val requests = provider.resolve("received.txt").readLines().map { json.decodeFromString<ExtensionRequest.Execute>(it) }
            assertEquals(2, requests.size)
            assertEquals("provider/run", requests.first().id)
            assertEquals("query", requests.first().query)
            assertEquals(buildJsonObject { put("value", 42) }, requests.first().payload)
            assertEquals("producer", requests.first().suggestion.sourceId)
            val events = root.resolve("extensions/producer/events.txt").readLines()
                .map { json.decodeFromString<ExtensionRequest.Use>(it).event }
            assertEquals(listOf(UsePhase.BEFORE, UsePhase.AFTER, UsePhase.BEFORE, UsePhase.AFTER), events.map { it.phase })
            assertTrue(events.filter { it.phase == UsePhase.AFTER }.all { it.succeeded == true })
        }
    }

    @Test
    fun skipsBadSuggestionsIndividuallyForBothSuggestAndPrefix() = runTest {
        extension("producer", "hooks = [\"suggest\", \"prefix\"]\nprefixes = [\"test\"]", """
            while IFS= read -r request; do
                printf '%s\n' '{"suggestions":[{"id":"legacy","title":"Old","action":{"type":"none"}},{"id":"bad","title":"Bad","action":{"id":"itzcast/none","payload":[]}},{"id":"null","title":"Null","action":{"id":"itzcast/none","payload":null}},{"id":"unknown","title":"Unknown","action":{"id":"missing/run"}},{"id":"ok","title":"Valid","action":{"id":"itzcast/none"}}]}'
            done
        """)
        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), desktopActions())
            assertEquals(listOf("ok"), pipeline.suggest(QueryContext("q")).map { it.id })
            assertEquals(listOf("ok"), pipeline.suggest(QueryContext("test q")).map { it.id })
        }
    }

    @Test
    fun rejectsInvalidManifestRegistrationsAndDuplicateProviders() {
        extension("reserved", "", "", manifestId = "itzcast")
        extension("slash", "", "", manifestId = "bad/provider")
        extension("duplicateA", "", "", manifestId = "duplicate")
        extension("duplicateB", "", "", manifestId = "duplicate")
        extension("badAction", "[[actions]]\nid = \"bad/action\"", "")
        extension("emptyAction", "[[actions]]\nid = \"\"", "")
        extension("duplicateAction", "[[actions]]\nid = \"run\"\n[[actions]]\nid = \"run\"", "")
        extension("valid", "[[actions]]\nid = \"run\"", "")
        ExternalExtensionCatalog(root).use { catalog ->
            assertEquals(listOf("valid"), catalog.load().map { it.id })
        }
    }

    @Test
    fun explicitFailureMissingResultAndMalformedResultFailWithoutRetry() = runTest {
        listOf(
            """{"actionResult":{"succeeded":false,"error":"Rejected"}}""",
            "{}",
            """{"actionResult":{"outcome":"close"}}""",
            """{"actionResult":{"succeeded":true,"outcome":"unknown"}}""",
            "not json",
        ).forEachIndexed { index, response ->
            val id = "provider$index"
            val directory = extension(id, "[[actions]]\nid = \"run\"", """
                while IFS= read -r request; do
                    printf '%s\n' 'called' >> calls.txt
                    printf '%s\n' '$response'
                done
            """)
            ExternalExtensionCatalog(root).use { catalog ->
                val pipeline = Pipeline(catalog.load(), desktopActions())
                val result = pipeline.use("q", Suggestion("item", "Item", action = ActionSpec("$id/run")))
                assertTrue(result.isFailure, response)
                assertEquals(1, directory.resolve("calls.txt").readLines().size)
                if (index == 0) assertEquals("Rejected", result.exceptionOrNull()?.message)
            }
        }
    }

    @Test
    fun timedOutExecutionIsNotRepeatedAndLaterRequestsRecover() = runTest {
        val directory = extension("provider", "[[actions]]\nid = \"run\"", """
            while IFS= read -r request; do
                printf '%s\n' 'called' >> calls.txt
                if [ ! -f attempted ]; then
                    touch attempted
                    sleep 2
                else
                    printf '%s\n' '{"actionResult":{"succeeded":true}}'
                fi
            done
        """, timeoutMs = 500)
        ExternalExtensionCatalog(root).use { catalog ->
            val pipeline = Pipeline(catalog.load(), desktopActions())
            val item = Suggestion("item", "Item", action = ActionSpec("provider/run"))
            assertTrue(pipeline.use("q", item).isFailure)
            assertEquals(1, directory.resolve("calls.txt").readLines().size)
            assertEquals(ActionOutcome.CLOSE, pipeline.use("q", item).getOrThrow())
            assertEquals(2, directory.resolve("calls.txt").readLines().size)
        }
    }

    @Test
    fun builtinRegistryIncludesNoneAndValidatesParameters() = runTest {
        val registrations = desktopActions()
        assertEquals(setOf("openUrl", "openPath", "command", "copy", "none"), registrations.map { it.id.substringAfter('/') }.toSet())
        val pipeline = Pipeline(emptyList(), registrations)
        assertEquals(ActionOutcome.CLOSE, pipeline.use("", Suggestion("none", "None", action = ActionSpec("itzcast/none"))).getOrThrow())
        listOf("openUrl", "openPath", "command", "copy").forEach {
            assertTrue(pipeline.use("", Suggestion(it, it, action = ActionSpec("itzcast/$it"))).isFailure)
        }
    }

    @Test
    fun builtinCommandPreservesArgumentsEnvironmentAndWorkingDirectory() = runTest {
        val literal = "literal ${'$'}HOME with spaces"
        val action = ActionSpec("itzcast/command", buildJsonObject {
            put("command", JsonArray(listOf("/bin/sh", "-c", "printf '%s' \"${'$'}TOKEN\" > output.txt").map(::JsonPrimitive)))
            put("workingDirectory", root.toString())
            putJsonObject("environment") { put("TOKEN", literal) }
        })
        val result = Pipeline(emptyList(), desktopActions()).use("", Suggestion("command", "Command", action = action))
        assertEquals(ActionOutcome.CLOSE, result.getOrThrow())
        withContext(Dispatchers.IO) {
            withTimeout(5000) {
                while (!root.resolve("output.txt").exists() || root.resolve("output.txt").readText() != literal) delay(10)
            }
        }
        assertEquals(literal, root.resolve("output.txt").readText())
    }

    private fun extension(
        directoryName: String,
        declarations: String,
        script: String,
        manifestId: String = directoryName,
        timeoutMs: Long = 2000,
    ): Path {
        val directory = root.resolve("extensions/$directoryName").createDirectories()
        directory.resolve("manifest.toml").writeText("""
            id = "$manifestId"
            command = ["./extension.sh"]
            timeoutMs = $timeoutMs
        """.trimIndent() + "\n" + declarations.trimIndent())
        directory.resolve("extension.sh").apply {
            writeText("#!/bin/sh\n" + script.trimIndent() + "\n")
            check(toFile().setExecutable(true))
        }
        return directory
    }
}
