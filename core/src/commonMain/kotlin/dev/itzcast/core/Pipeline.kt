package dev.itzcast.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Pipeline(
    extensions: List<ItzExtension>,
    private val executor: ActionExecutor,
) {
    private val launchHooks = extensions.hooks<LaunchHook>()
    private val prefixHooks = extensions.hooks<PrefixHook>()
    private val suggestHooks = extensions.hooks<SuggestHook>()
    private val useHooks = extensions.hooks<UseHook>()

    suspend fun launch(initial: LaunchContext = LaunchContext()): LaunchContext =
        launchHooks.fold(initial) { context, hook ->
            runCatching { hook.onLaunch(context) }.getOrDefault(context)
        }

    fun matchPrefix(query: String): PrefixMatch? =
        findPrefix(query, prefixHooks.flatMapTo(linkedSetOf()) { it.prefixes })

    suspend fun suggest(context: QueryContext, limit: Int = 12): List<Suggestion> {
        val matches = prefixHooks.mapNotNull { hook ->
            findPrefix(context.query, hook.prefixes)?.let { hook to it }
        }

        val contributed = coroutineScope {
            val prefixContributions = matches.map { (hook, match) ->
                async { runCatching { hook.suggest(context, match) }.getOrDefault(emptyList()) }
            }
            val suggestions = suggestHooks.map { hook ->
                async { runCatching { hook.suggest(context) }.getOrDefault(emptyList()) }
            }
            (prefixContributions + suggestions).awaitAll().flatten()
        }

        return contributed
            .groupBy(Suggestion::id)
            .map { (_, duplicates) -> duplicates.maxBy(Suggestion::score) }
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.title })
            .take(limit)
    }

    suspend fun use(query: String, suggestion: Suggestion): Result<Unit> {
        notifyUse(UseEvent(UsePhase.BEFORE, query, suggestion))
        val result = runCatching { executor.execute(suggestion.action) }
        notifyUse(
            UseEvent(
                phase = UsePhase.AFTER,
                query = query,
                suggestion = suggestion,
                succeeded = result.isSuccess,
                error = result.exceptionOrNull()?.message,
            ),
        )
        return result
    }

    private suspend fun notifyUse(event: UseEvent) {
        useHooks.forEach { runCatching { it.onUse(event) } }
    }

    private fun findPrefix(query: String, prefixes: Set<String>): PrefixMatch? {
        val trimmed = query.trimStart()
        val prefix = prefixes
            .sortedByDescending(String::length)
            .firstOrNull { trimmed == it || trimmed.startsWith("$it ") }
            ?: return null
        return PrefixMatch(prefix, trimmed.removePrefix(prefix).trimStart())
    }

    private inline fun <reified T : ExtensionHook> List<ItzExtension>.hooks(): List<T> =
        flatMap(ItzExtension::hooks)
            .filterIsInstance<T>()
            .sortedByDescending(ExtensionHook::priority)
}
