package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.PrefixMatchDto
import dev.itzcast.core.QueryContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialExtensionHostTest {
    @Test
    fun bashBuildsShellAction() {
        val response = OfficialExtensionHost.handle(
            "bash",
            ExtensionRequest.Prefix(QueryContext("bash pwd"), PrefixMatchDto("bash", "pwd")),
        )

        val action = response.suggestions.single().action as ActionSpec.RunCommand
        assertEquals(listOf("/bin/zsh", "-lc", "pwd"), action.command)
    }

    @Test
    fun calculatorSupportsPrecedenceParenthesesAndPower() {
        val response = OfficialExtensionHost.handle(
            "calculator",
            ExtensionRequest.Suggest(QueryContext("2 + 3 * (4 ^ 2)")),
        )

        assertEquals("50", response.suggestions.single().title)
        assertEquals(ActionSpec.CopyText("50"), response.suggestions.single().action)
    }

    @Test
    fun calculatorSupportsUnaryOperators() {
        assertCalculation("-3", "-3")
        assertCalculation("+3", "3")
        assertCalculation("--3", "3")
        assertCalculation("-(3 + 5) * 2", "-16")
    }

    @Test
    fun calculatorKeepsModuloAndRightAssociativePower() {
        assertCalculation("10 % 4", "2")
        assertCalculation("5.5 % 2", "1.5")
        assertCalculation("1 % 0.3", "0.1")
        assertCalculation("-10 % 4", "-2")
        assertCalculation("2 ^ 3 ^ 2", "512")
    }

    @Test
    fun calculatorUsesPreciseDecimalArithmetic() {
        assertCalculation("0.1 + 0.2", "0.3")
        assertCalculation("1 / 3", "0.3333333333333333")
    }

    @Test
    fun calculatorAcceptsEqualsPrefixAndRejectsFailures() {
        assertCalculation("= 2 + 2", "4")
        assertTrue(calculatorResponse("1 / 0").suggestions.isEmpty())
        assertTrue(calculatorResponse("10 % 0").suggestions.isEmpty())
        assertTrue(calculatorResponse("2 +").suggestions.isEmpty())
        assertTrue(calculatorResponse("42").suggestions.isEmpty())
    }

    @Test
    fun youtubeUsesPrefixArguments() {
        val response = OfficialExtensionHost.handle(
            "youtube",
            ExtensionRequest.Prefix(QueryContext("yt funny cats"), PrefixMatchDto("yt", "funny cats")),
        )

        val action = response.suggestions.single().action as ActionSpec.OpenUrl
        assertTrue(action.url.endsWith("funny%20cats"))
    }

    @Test
    fun webSearchProvidesFallback() {
        val response = OfficialExtensionHost.handle(
            "web-search",
            ExtensionRequest.Suggest(QueryContext("kotlin compose")),
        )

        assertEquals(10.0, response.suggestions.single().score)
        assertEquals("itzcast.web-search", response.suggestions.single().sourceId)
    }

    private fun assertCalculation(expression: String, expected: String) {
        val suggestion = calculatorResponse(expression).suggestions.single()
        assertEquals(expected, suggestion.title)
        assertEquals(ActionSpec.CopyText(expected), suggestion.action)
        assertEquals("itzcast.calculator", suggestion.sourceId)
    }

    private fun calculatorResponse(expression: String) = OfficialExtensionHost.handle(
        "calculator",
        ExtensionRequest.Suggest(QueryContext(expression)),
    )
}
