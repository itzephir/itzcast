package dev.itzcast.extensions

import dev.itzcast.core.ActionSpec
import dev.itzcast.core.ExtensionRequest
import dev.itzcast.core.ExtensionResponse
import dev.itzcast.core.Suggestion
import dev.itzcast.core.SuggestionKind
import kotlin.math.pow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.y9san9.calkt.annotation.CalculateSubclass
import me.y9san9.calkt.calculate.CalculateContext
import me.y9san9.calkt.calculate.CalculateResult
import me.y9san9.calkt.calculate.tryCalculate
import me.y9san9.calkt.math.InfixKey
import me.y9san9.calkt.math.annotation.InfixKeySubclass
import me.y9san9.calkt.math.calculate.DefaultMathCalculateInfixOperator
import me.y9san9.calkt.math.calculate.MathCalculateFailure.UnsupportedInfixOperator
import me.y9san9.calkt.math.calculate.MathCalculateInfixOperatorFunction
import me.y9san9.calkt.math.calculate.MathCalculateSuccess
import me.y9san9.calkt.math.calculate.calculateMathExpression
import me.y9san9.calkt.math.calculate.plus
import me.y9san9.calkt.math.parse.DefaultMathInfixOperators.Div
import me.y9san9.calkt.math.parse.DefaultMathInfixOperators.Minus
import me.y9san9.calkt.math.parse.DefaultMathInfixOperators.Plus
import me.y9san9.calkt.math.parse.DefaultMathInfixOperators.Times
import me.y9san9.calkt.math.parse.MathParseInfixAssociativity
import me.y9san9.calkt.math.parse.MathParseInfixKeyFunction
import me.y9san9.calkt.math.parse.MathParseInfixOperatorLevel
import me.y9san9.calkt.math.parse.MathParseInfixOperatorLevels
import me.y9san9.calkt.math.parse.parseMathExpression
import me.y9san9.calkt.math.parse.plus
import me.y9san9.calkt.number.PreciseNumber
import me.y9san9.calkt.parse.ParseContext
import me.y9san9.calkt.parse.ParseResult
import me.y9san9.calkt.parse.base.token
import me.y9san9.calkt.parse.cause.ExpectedInputCause
import me.y9san9.calkt.parse.tryParse

internal object CalculatorExtension : OfficialExtension {
    override suspend fun handle(request: ExtensionRequest): ExtensionResponse {
        val query = (request as? ExtensionRequest.Suggest)?.context?.query.orEmpty()
        val expression = query.trim().removePrefix("=").trim()
        if (expression.isEmpty() || expression.none(Char::isDigit) || expression.none { it in "+-*/%^" }) {
            return ExtensionResponse()
        }

        val formatted = runCatching { CalculatorMath.calculate(expression) }.getOrNull()
            ?: return ExtensionResponse()
        return ExtensionResponse(
            suggestions = listOf(
                Suggestion(
                    id = "itzcast.calculator:$expression",
                    title = formatted,
                    subtitle = "$expression — press Enter to copy",
                    score = 110.0,
                    kind = SuggestionKind.CALCULATION,
                    action = ActionSpec("itzcast/copy", buildJsonObject { put("text", formatted) }),
                    sourceId = "itzcast.calculator",
                ),
            ),
        )
    }
}

private object CalculatorMath {
    private const val PRECISION = 16L

    private val operatorLevels = MathParseInfixOperatorLevels(
        MathParseInfixOperatorLevel(PowerParse, MathParseInfixAssociativity.RIGHT),
        MathParseInfixOperatorLevel(Times + Div + ModParse),
        MathParseInfixOperatorLevel(Plus + Minus),
    )
    private val calculator = DefaultMathCalculateInfixOperator + ModCalculate + PowerCalculate

    fun calculate(source: String): String? {
        val parsed = tryParse(source) { context ->
            context.parseMathExpression(infixOperatorLevels = operatorLevels)
        }
        val expression = (parsed as? ParseResult.Success)?.value ?: return null
        val result = tryCalculate(expression, precision = PRECISION) { context ->
            context.calculateMathExpression(calculateInfixOperator = calculator)
        }
        return (result as? MathCalculateSuccess)?.number?.toString()
    }

}

@OptIn(InfixKeySubclass::class)
private data object ModKey : InfixKey

@OptIn(InfixKeySubclass::class)
private data object PowerKey : InfixKey

private data object ModParse : MathParseInfixKeyFunction {
    override fun invoke(context: ParseContext): InfixKey {
        context.token("%") { ExpectedInputCause.of("%") }
        return ModKey
    }
}

private data object PowerParse : MathParseInfixKeyFunction {
    override fun invoke(context: ParseContext): InfixKey {
        context.token("^") { ExpectedInputCause.of("^") }
        return PowerKey
    }
}

private data object ModCalculate : MathCalculateInfixOperatorFunction {
    override fun invoke(
        context: CalculateContext,
        left: CalculateResult.Success,
        right: CalculateResult.Success,
        key: InfixKey,
    ): CalculateResult.Success {
        if (left !is MathCalculateSuccess || right !is MathCalculateSuccess || key != ModKey) {
            context.fail(UnsupportedInfixOperator)
        }
        if (right.number.isZero()) {
            context.fail(CalculateResult.DivisionByZero)
        }
        return MathCalculateSuccess(left.number % right.number)
    }
}

private data object PowerCalculate : MathCalculateInfixOperatorFunction {
    override fun invoke(
        context: CalculateContext,
        left: CalculateResult.Success,
        right: CalculateResult.Success,
        key: InfixKey,
    ): CalculateResult.Success {
        if (left !is MathCalculateSuccess || right !is MathCalculateSuccess || key != PowerKey) {
            context.fail(UnsupportedInfixOperator)
        }
        val result = left.number.toString().toDouble().pow(right.number.toString().toDouble())
        if (!result.isFinite()) {
            context.fail(NonFiniteResult)
        }
        return MathCalculateSuccess(PreciseNumber.of(result))
    }
}

@OptIn(CalculateSubclass::class)
private data object NonFiniteResult : CalculateResult.Failure
