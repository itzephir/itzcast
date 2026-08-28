package dev.itzcast.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.itzcast.core.PrefixMatch

internal sealed interface LauncherQueryState {
    val query: String

    data class Plain(
        val value: TextFieldValue = TextFieldValue(""),
    ) : LauncherQueryState {
        override val query: String get() = value.text
    }

    data class Prefixed(
        val prefix: String,
        val arguments: TextFieldValue,
    ) : LauncherQueryState {
        override val query: String
            get() = if (arguments.text.isEmpty()) prefix else "$prefix ${arguments.text}"

        fun removePrefix(): Plain = Plain(
            arguments.copy(selection = TextRange.Zero),
        )
    }
}

internal fun launcherQueryState(
    value: TextFieldValue,
    match: PrefixMatch?,
): LauncherQueryState = match?.let {
    LauncherQueryState.Prefixed(
        prefix = it.prefix,
        arguments = TextFieldValue(
            text = it.arguments,
            selection = TextRange(it.arguments.length),
        ),
    )
} ?: LauncherQueryState.Plain(value)
