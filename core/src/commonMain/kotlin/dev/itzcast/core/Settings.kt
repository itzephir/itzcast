package dev.itzcast.core

import kotlinx.serialization.Serializable

@Serializable
data class ItzcastSettings(
    val hotKey: HotKey = HotKey.DEFAULT,
)

@Serializable
data class HotKey(
    val modifiers: Set<HotKeyModifier>,
    val key: HotKeyKey,
) {
    init {
        require(modifiers.isNotEmpty()) { "A global hotkey must include a modifier" }
    }

    fun displayName(): String = buildString {
        HotKeyModifier.entries.forEach { modifier ->
            if (modifier in modifiers) append(modifier.symbol)
        }
        append(key.label)
    }

    companion object {
        val DEFAULT = HotKey(setOf(HotKeyModifier.OPTION), HotKeyKey.SPACE)
    }
}

@Serializable
enum class HotKeyModifier(val symbol: String) {
    CONTROL("⌃"),
    OPTION("⌥"),
    SHIFT("⇧"),
    COMMAND("⌘"),
}

@Serializable
enum class HotKeyKey(val label: String) {
    SPACE("Space"),
    ENTER("Enter"),
    TAB("Tab"),
    ESCAPE("Esc"),
    A("A"), B("B"), C("C"), D("D"), E("E"), F("F"), G("G"),
    H("H"), I("I"), J("J"), K("K"), L("L"), M("M"), N("N"),
    O("O"), P("P"), Q("Q"), R("R"), S("S"), T("T"), U("U"),
    V("V"), W("W"), X("X"), Y("Y"), Z("Z"),
    DIGIT_0("0"), DIGIT_1("1"), DIGIT_2("2"), DIGIT_3("3"), DIGIT_4("4"),
    DIGIT_5("5"), DIGIT_6("6"), DIGIT_7("7"), DIGIT_8("8"), DIGIT_9("9"),
    F1("F1"), F2("F2"), F3("F3"), F4("F4"), F5("F5"), F6("F6"),
    F7("F7"), F8("F8"), F9("F9"), F10("F10"), F11("F11"), F12("F12"),
    COMMA(","), PERIOD("."), SLASH("/"), SEMICOLON(";"), QUOTE("'"),
    LEFT_BRACKET("["), RIGHT_BRACKET("]"), BACKSLASH("\\"), MINUS("-"),
    EQUALS("="), BACKTICK("`"),
}
