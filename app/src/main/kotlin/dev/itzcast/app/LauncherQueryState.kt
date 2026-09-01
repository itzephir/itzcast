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

        fun update(value: TextFieldValue, match: PrefixMatch?): LauncherQueryState {
            val prefix = match?.prefix ?: return Plain(value)
            val previousText = this.value.text.trimStart()
            val updatedText = value.text.trimStart()
            return if (previousText == prefix && updatedText == "$prefix ") {
                Prefixed(
                    prefix = prefix,
                    arguments = TextFieldValue("", selection = TextRange.Zero),
                )
            } else {
                Plain(value)
            }
        }
    }

    data class Prefixed(
        val prefix: String,
        val arguments: TextFieldValue,
    ) : LauncherQueryState {
        override val query: String
            get() = if (arguments.text.isEmpty()) prefix else "$prefix ${arguments.text}"

        fun removePrefix(): Plain {
            val separatorOffset = prefix.length + 1
            return Plain(
                TextFieldValue(
                    text = "$prefix ${arguments.text}",
                    selection = TextRange(
                        start = arguments.selection.start + separatorOffset,
                        end = arguments.selection.end + separatorOffset,
                    ),
                ),
            )
        }
    }
}
