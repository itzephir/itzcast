package dev.itzcast.platform

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import dev.itzcast.core.HotKey
import dev.itzcast.core.HotKeyKey
import dev.itzcast.core.HotKeyModifier
import dev.itzcast.core.ItzcastSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class SettingsStore(
    private val path: Path,
    private val toml: Toml = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true)),
) {
    fun load(): ItzcastSettings {
        if (path.exists()) {
            return runCatching { toml.decodeFromString<SettingsConfig>(path.readText()).toSettings() }
                .getOrDefault(ItzcastSettings())
        }
        return ItzcastSettings()
    }

    fun save(settings: ItzcastSettings): Result<Unit> = runCatching {
        path.parent?.createDirectories()
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, toml.encodeToString(settings.toConfig()))
        runCatching {
            Files.move(
                temporary,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
        Unit
    }
}

@Serializable
private data class SettingsConfig(
    val hotKey: HotKeyConfig = HotKey.DEFAULT.toConfig(),
) {
    fun toSettings() = ItzcastSettings(hotKey.toHotKey())
}

@Serializable
private data class HotKeyConfig(
    val modifiers: List<String>,
    val key: String,
) {
    fun toHotKey() = HotKey(
        modifiers = modifiers.map { HotKeyModifier.valueOf(it.trim().uppercase()) }.toSet(),
        key = HotKeyKey.valueOf(key.trim().uppercase()),
    )
}

private fun ItzcastSettings.toConfig() = SettingsConfig(hotKey.toConfig())

private fun HotKey.toConfig() = HotKeyConfig(
    modifiers = HotKeyModifier.entries.filter(modifiers::contains).map(Enum<*>::name),
    key = key.name,
)
