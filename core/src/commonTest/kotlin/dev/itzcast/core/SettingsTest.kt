package dev.itzcast.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsTest {
    @Test
    fun defaultHotKeyAvoidsSpotlight() {
        assertEquals("⌥Space", HotKey.DEFAULT.displayName())
        assertEquals(HotKeyKey.SPACE, HotKey.DEFAULT.key)
        assertEquals(setOf(HotKeyModifier.OPTION), HotKey.DEFAULT.modifiers)
    }

    @Test
    fun hotKeyRequiresModifier() {
        assertFailsWith<IllegalArgumentException> { HotKey(emptySet(), HotKeyKey.K) }
    }
}
