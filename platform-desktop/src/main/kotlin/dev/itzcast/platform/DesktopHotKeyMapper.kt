package dev.itzcast.platform

import dev.itzcast.core.HotKey
import dev.itzcast.core.HotKeyKey
import dev.itzcast.core.HotKeyModifier
import java.awt.event.KeyEvent

object DesktopHotKeyMapper {
    fun fromAwtKeyCode(
        keyCode: Int,
        isMetaPressed: Boolean,
        isAltPressed: Boolean,
        isShiftPressed: Boolean,
        isCtrlPressed: Boolean,
    ): HotKey? {
        val key = AWT_KEYS[keyCode] ?: return null
        val modifiers = buildSet {
            if (isCtrlPressed) add(HotKeyModifier.CONTROL)
            if (isAltPressed) add(HotKeyModifier.OPTION)
            if (isShiftPressed) add(HotKeyModifier.SHIFT)
            if (isMetaPressed) add(HotKeyModifier.COMMAND)
        }
        if (modifiers.isEmpty()) return null
        return HotKey(modifiers, key)
    }

    private val AWT_KEYS = buildMap {
        put(KeyEvent.VK_SPACE, HotKeyKey.SPACE)
        put(KeyEvent.VK_ENTER, HotKeyKey.ENTER)
        put(KeyEvent.VK_TAB, HotKeyKey.TAB)
        put(KeyEvent.VK_ESCAPE, HotKeyKey.ESCAPE)
        ('A'..'Z').forEach { letter -> put(KeyEvent.getExtendedKeyCodeForChar(letter.code), HotKeyKey.valueOf(letter.toString())) }
        ('0'..'9').forEach { digit -> put(KeyEvent.getExtendedKeyCodeForChar(digit.code), HotKeyKey.valueOf("DIGIT_$digit")) }
        (1..12).forEach { number -> put(KeyEvent.VK_F1 + number - 1, HotKeyKey.valueOf("F$number")) }
        put(KeyEvent.VK_COMMA, HotKeyKey.COMMA)
        put(KeyEvent.VK_PERIOD, HotKeyKey.PERIOD)
        put(KeyEvent.VK_SLASH, HotKeyKey.SLASH)
        put(KeyEvent.VK_SEMICOLON, HotKeyKey.SEMICOLON)
        put(KeyEvent.VK_QUOTE, HotKeyKey.QUOTE)
        put(KeyEvent.VK_OPEN_BRACKET, HotKeyKey.LEFT_BRACKET)
        put(KeyEvent.VK_CLOSE_BRACKET, HotKeyKey.RIGHT_BRACKET)
        put(KeyEvent.VK_BACK_SLASH, HotKeyKey.BACKSLASH)
        put(KeyEvent.VK_MINUS, HotKeyKey.MINUS)
        put(KeyEvent.VK_EQUALS, HotKeyKey.EQUALS)
        put(KeyEvent.VK_BACK_QUOTE, HotKeyKey.BACKTICK)
    }
}
