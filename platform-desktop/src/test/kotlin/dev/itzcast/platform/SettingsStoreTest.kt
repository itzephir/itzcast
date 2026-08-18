package dev.itzcast.platform

import dev.itzcast.core.HotKey
import dev.itzcast.core.HotKeyKey
import dev.itzcast.core.HotKeyModifier
import dev.itzcast.core.ItzcastSettings
import org.junit.jupiter.api.io.TempDir
import java.awt.event.KeyEvent
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun settingsRoundTrip() {
        val path = root.resolve("settings.toml")
        val store = SettingsStore(path)
        val expected = ItzcastSettings(HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.SPACE))

        store.save(expected).getOrThrow()

        assertEquals(expected, store.load(), path.readText())
        assertEquals(true, path.readText().contains("[hotKey]"))
        assertEquals(true, path.readText().contains("key = \"SPACE\""))
    }

    @Test
    fun invalidFileFallsBackToSafeDefault() {
        val path = root.resolve("settings.toml").also { it.writeText("not valid toml") }

        assertEquals(ItzcastSettings(), SettingsStore(path).load())
    }

    @Test
    fun mapsRecordedDesktopShortcut() {
        val hotKey = DesktopHotKeyMapper.fromAwtKeyCode(
            keyCode = KeyEvent.VK_SPACE,
            isMetaPressed = true,
            isAltPressed = false,
            isShiftPressed = false,
            isCtrlPressed = false,
        )

        assertEquals(HotKey(setOf(HotKeyModifier.COMMAND), HotKeyKey.SPACE), hotKey)
    }
}
