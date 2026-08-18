package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import kotlin.math.abs
import kotlin.math.pow

internal object CalculatorExtension : OfficialExtension {
    override fun handle(request: ExtensionRequest): ExtensionResponse {
        val query = (request as? ExtensionRequest.Suggest)?.context?.query.orEmpty()
        val expression = query.trim().removePrefix("=").trim()
        if (expression.isEmpty() || expression.none(Char::isDigit) || expression.none { it in "+-*/%^" }) {
            return ExtensionResponse()
        }
        val value = runCatching { ExpressionParser(expression).parse() }.getOrNull()
            ?.takeIf(Double::isFinite)
            ?: return ExtensionResponse()
        val formatted = if (abs(value - value.toLong()) < 1e-10) value.toLong().toString() else value.toString()
        return ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.calculator:$expression",
                    title = formatted,
                    subtitle = "$expression — press Enter to copy",
                    score = 110.0,
                    kind = SuggestionKind.CALCULATION,
                    action = ActionSpec.CopyText(formatted),
                    sourceId = "itzcast.calculator",
                ),
            ),
        )
    }
}

private class ExpressionParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        val result = expression()
        skipWhitespace()
        require(index == source.length) { "Unexpected input at $index" }
        return result
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipWhitespace()
            value = when {
                consume('+') -> value + term()
                consume('-') -> value - term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = power()
        while (true) {
            skipWhitespace()
            value = when {
                consume('*') -> value * power()
                consume('/') -> value / power()
                consume('%') -> value % power()
                else -> return value
            }
        }
    }

    private fun power(): Double {
        val base = unary()
        skipWhitespace()
        return if (consume('^')) base.pow(power()) else base
    }

    private fun unary(): Double {
        skipWhitespace()
        return when {
            consume('+') -> unary()
            consume('-') -> -unary()
            consume('(') -> expression().also {
                skipWhitespace()
                require(consume(')')) { "Missing closing parenthesis" }
            }
            else -> number()
        }
    }

    private fun number(): Double {
        skipWhitespace()
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        require(start != index) { "Expected a number at $index" }
        return source.substring(start, index).toDouble()
    }

    private fun consume(expected: Char): Boolean =
        if (index < source.length && source[index] == expected) {
            index++
            true
        } else {
            false

        }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }
}
