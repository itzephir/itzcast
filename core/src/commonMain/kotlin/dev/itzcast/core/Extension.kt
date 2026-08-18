package dev.itzcast.core

interface ExtensionHook {
    val extensionId: String
    val priority: Int get() = 0
}

interface LaunchHook : ExtensionHook {
    suspend fun onLaunch(context: LaunchContext): LaunchContext = context
}

interface PrefixHook : ExtensionHook {
    val prefixes: Set<String>
    suspend fun suggest(context: QueryContext, match: PrefixMatch): List<Suggestion>
}

interface SuggestHook : ExtensionHook {
    suspend fun suggest(context: QueryContext): List<Suggestion>
}

interface UseHook : ExtensionHook {
    suspend fun onUse(event: UseEvent) = Unit
}

interface ItzExtension {
    val id: String
    val hooks: List<ExtensionHook>
}

interface ActionExecutor {
    suspend fun execute(action: ActionSpec)
}

data class StaticExtension(
    override val id: String,
    override val hooks: List<ExtensionHook>,
) : ItzExtension
