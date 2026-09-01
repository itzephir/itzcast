package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.PrefixMatchDto
import dev.itzcast.core.QueryContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialExtensionHostTest {
    @Test
    fun bashBuildsShellAction() = runTest {
        val response = OfficialExtensionHost.handle(
            "bash",
            ExtensionRequest.Prefix(QueryContext("bash pwd"), PrefixMatchDto("bash", "pwd")),
        )

        val action = response.suggestions.single().action as ActionSpec.RunCommand
        assertEquals(listOf("/bin/zsh", "-lc", "pwd"), action.command)
    }

    @Test
    fun calculatorSupportsPrecedenceParenthesesAndPower() = runTest {
        val response = OfficialExtensionHost.handle(
            "calculator",
            ExtensionRequest.Suggest(QueryContext("2 + 3 * (4 ^ 2)")),
        )

        assertEquals("50", response.suggestions.single().title)
        assertEquals(ActionSpec.CopyText("50"), response.suggestions.single().action)
    }

    @Test
    fun calculatorSupportsUnaryOperators() = runTest {
        assertCalculation("-3", "-3")
        assertCalculation("+3", "3")
        assertCalculation("--3", "3")
        assertCalculation("-(3 + 5) * 2", "-16")
    }

    @Test
    fun calculatorKeepsModuloAndRightAssociativePower() = runTest {
        assertCalculation("10 % 4", "2")
        assertCalculation("5.5 % 2", "1.5")
        assertCalculation("1 % 0.3", "0.1")
        assertCalculation("-10 % 4", "-2")
        assertCalculation("2 ^ 3 ^ 2", "512")
    }

    @Test
    fun calculatorUsesPreciseDecimalArithmetic() = runTest {
        assertCalculation("0.1 + 0.2", "0.3")
        assertCalculation("1 / 3", "0.3333333333333333")
    }

    @Test
    fun calculatorAcceptsEqualsPrefixAndRejectsFailures() = runTest {
        assertCalculation("= 2 + 2", "4")
        assertTrue(calculatorResponse("1 / 0").suggestions.isEmpty())
        assertTrue(calculatorResponse("10 % 0").suggestions.isEmpty())
        assertTrue(calculatorResponse("2 +").suggestions.isEmpty())
        assertTrue(calculatorResponse("42").suggestions.isEmpty())
    }

    @Test
    fun youtubeUsesPrefixArguments() = runTest {
        val response = OfficialExtensionHost.handle(
            "youtube",
            ExtensionRequest.Prefix(QueryContext("yt funny cats"), PrefixMatchDto("yt", "funny cats")),
        )

        val action = response.suggestions.single().action as ActionSpec.OpenUrl
        assertTrue(action.url.endsWith("funny%20cats"))
    }

    @Test
    fun webSearchProvidesFallback() = runTest {
        val response = OfficialExtensionHost.handle(
            "web-search",
            ExtensionRequest.Suggest(QueryContext("kotlin compose")),
        )

        assertEquals(10.0, response.suggestions.single().score)
        assertEquals("itzcast.web-search", response.suggestions.single().sourceId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun applicationsStartupLoadsOnceAndSuggestAwaitsSharedResult() = runTest {
        val release = CompletableDeferred<Unit>()
        var loadCount = 0
        val extension = ApplicationsExtension(
            loader = {
                loadCount++
                release.await()
                listOf(Application("Safari", Path.of("/Applications/Safari.app")))
            },
            scope = backgroundScope,
        )

        extension.handle(ExtensionRequest.Startup)
        runCurrent()
        assertEquals(1, loadCount)

        val pending = async {
            extension.handle(ExtensionRequest.Suggest(QueryContext("saf")))
        }
        runCurrent()
        assertFalse(pending.isCompleted)

        release.complete(Unit)
        assertEquals("Safari", pending.await().suggestions.single().title)
        assertEquals(
            "Safari",
            extension.handle(ExtensionRequest.Suggest(QueryContext("saf"))).suggestions.single().title,
        )
        assertEquals(1, loadCount)
    }

    @Test
    fun applicationsSuggestCanStartLoadingWithoutStartup() = runTest {
        var loadCount = 0
        val extension = ApplicationsExtension(
            loader = {
                loadCount++
                listOf(Application("Terminal", Path.of("/Applications/Terminal.app")))
            },
            scope = backgroundScope,
        )

        val response = extension.handle(ExtensionRequest.Suggest(QueryContext("term")))

        assertEquals("Terminal", response.suggestions.single().title)
        assertEquals(1, loadCount)
    }

    private suspend fun assertCalculation(expression: String, expected: String) {
        val suggestion = calculatorResponse(expression).suggestions.single()
        assertEquals(expected, suggestion.title)
        assertEquals(ActionSpec.CopyText(expected), suggestion.action)
        assertEquals("itzcast.calculator", suggestion.sourceId)
    }

    private suspend fun calculatorResponse(expression: String) = OfficialExtensionHost.handle(
        "calculator",
        ExtensionRequest.Suggest(QueryContext(expression)),
    )
}
