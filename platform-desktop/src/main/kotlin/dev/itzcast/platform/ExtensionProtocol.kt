package dev.itzcast.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String = id,
    val version: String = "0.1.0",
    val enabled: Boolean = true,
    val command: List<String>,
    val hooks: Set<HookType> = emptySet(),
    val prefixes: Set<String> = emptySet(),
    val priority: Int = 0,
    val timeoutMs: Long = 2_000,
    val environment: Map<String, String> = emptyMap(),
)

@Serializable
enum class HookType {
    @SerialName("startup") STARTUP,
    @SerialName("launch") LAUNCH,
    @SerialName("prefix") PREFIX,
    @SerialName("suggest") SUGGEST,
    @SerialName("use") USE,
}
