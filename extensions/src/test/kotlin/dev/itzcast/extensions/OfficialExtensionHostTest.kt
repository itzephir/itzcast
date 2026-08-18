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
}
