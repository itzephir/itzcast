package dev.itzcast.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import dev.itzcast.core.HotKey
import dev.itzcast.core.HotKeyKey
import dev.itzcast.core.HotKeyModifier
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

class MacGlobalHotKey private constructor(
    private val hotKeyReference: Pointer,
    private val handlerReference: Pointer,
    @Suppress("unused") private val callback: EventHandlerCallback,
) : Closeable {
    override fun close() {
        Carbon.INSTANCE.UnregisterEventHotKey(hotKeyReference)
        Carbon.INSTANCE.RemoveEventHandler(handlerReference)
    }

    companion object {
        fun register(hotKey: HotKey, onPressed: () -> Unit): MacGlobalHotKey? {
            if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) return null
            val keyCode = CARBON_KEY_CODES[hotKey.key] ?: return null
            val callback = EventHandlerCallback { _, _, _ ->
                onPressed()
                0
            }
            val eventType = arrayOf(EventTypeSpec(KEYBOARD_EVENT_CLASS, HOT_KEY_PRESSED))
            val handlerReference = PointerByReference()
            val status = Carbon.INSTANCE.InstallEventHandler(
                Carbon.INSTANCE.GetApplicationEventTarget(),
                callback,
                eventType.size,
                eventType,
                null,
                handlerReference,
            )
            if (status != 0 || handlerReference.value == null) return null

            val reference = PointerByReference()
            val registered = Carbon.INSTANCE.RegisterEventHotKey(
                keyCode,
                hotKey.modifiers.sumOf { CARBON_MODIFIERS.getValue(it) },
                EventHotKeyId(fourCharCode("ITZC"), nextId.getAndIncrement()),
                Carbon.INSTANCE.GetApplicationEventTarget(),
                0,
                reference,
            )
            return if (registered == 0 && reference.value != null) {
                MacGlobalHotKey(reference.value, handlerReference.value, callback)
            } else {
                Carbon.INSTANCE.RemoveEventHandler(handlerReference.value)
                null
            }
        }

        private const val KEYBOARD_EVENT_CLASS = 0x6B657962
        private const val HOT_KEY_PRESSED = 5
        private val nextId = AtomicInteger(1)

        private val CARBON_MODIFIERS = mapOf(
            HotKeyModifier.COMMAND to (1 shl 8),
            HotKeyModifier.SHIFT to (1 shl 9),
            HotKeyModifier.OPTION to (1 shl 11),
            HotKeyModifier.CONTROL to (1 shl 12),
        )

        private val CARBON_KEY_CODES = mapOf(
            HotKeyKey.A to 0, HotKeyKey.S to 1, HotKeyKey.D to 2, HotKeyKey.F to 3,
            HotKeyKey.H to 4, HotKeyKey.G to 5, HotKeyKey.Z to 6, HotKeyKey.X to 7,
            HotKeyKey.C to 8, HotKeyKey.V to 9, HotKeyKey.B to 11, HotKeyKey.Q to 12,
            HotKeyKey.W to 13, HotKeyKey.E to 14, HotKeyKey.R to 15, HotKeyKey.Y to 16,
            HotKeyKey.T to 17, HotKeyKey.DIGIT_1 to 18, HotKeyKey.DIGIT_2 to 19,
            HotKeyKey.DIGIT_3 to 20, HotKeyKey.DIGIT_4 to 21, HotKeyKey.DIGIT_6 to 22,
            HotKeyKey.DIGIT_5 to 23, HotKeyKey.EQUALS to 24, HotKeyKey.DIGIT_9 to 25,
            HotKeyKey.DIGIT_7 to 26, HotKeyKey.MINUS to 27, HotKeyKey.DIGIT_8 to 28,
            HotKeyKey.DIGIT_0 to 29, HotKeyKey.RIGHT_BRACKET to 30, HotKeyKey.O to 31,
            HotKeyKey.U to 32, HotKeyKey.LEFT_BRACKET to 33, HotKeyKey.I to 34,
            HotKeyKey.P to 35, HotKeyKey.ENTER to 36, HotKeyKey.L to 37,
            HotKeyKey.J to 38, HotKeyKey.QUOTE to 39, HotKeyKey.K to 40,
            HotKeyKey.SEMICOLON to 41, HotKeyKey.BACKSLASH to 42, HotKeyKey.COMMA to 43,
            HotKeyKey.SLASH to 44, HotKeyKey.N to 45, HotKeyKey.M to 46,
            HotKeyKey.PERIOD to 47, HotKeyKey.TAB to 48, HotKeyKey.SPACE to 49,
            HotKeyKey.BACKTICK to 50, HotKeyKey.ESCAPE to 53,
            HotKeyKey.F1 to 122, HotKeyKey.F2 to 120, HotKeyKey.F3 to 99,
            HotKeyKey.F4 to 118, HotKeyKey.F5 to 96, HotKeyKey.F6 to 97,
            HotKeyKey.F7 to 98, HotKeyKey.F8 to 100, HotKeyKey.F9 to 101,
            HotKeyKey.F10 to 109, HotKeyKey.F11 to 103, HotKeyKey.F12 to 111,
        )

        private fun fourCharCode(value: String): Int = value.fold(0) { result, char ->
            (result shl 8) or char.code
        }
    }
}

fun interface EventHandlerCallback : Callback {
    fun invoke(nextHandler: Pointer?, event: Pointer?, userData: Pointer?): Int
}

@Structure.FieldOrder("eventClass", "eventKind")
class EventTypeSpec(
    @JvmField var eventClass: Int = 0,
    @JvmField var eventKind: Int = 0,
) : Structure()

@Structure.FieldOrder("signature", "id")
class EventHotKeyId(
    @JvmField var signature: Int = 0,
    @JvmField var id: Int = 0,
) : Structure(), Structure.ByValue

interface Carbon : Library {
    fun GetApplicationEventTarget(): Pointer

    fun InstallEventHandler(
        target: Pointer,
        handler: EventHandlerCallback,
        numberOfTypes: Int,
        types: Array<EventTypeSpec>,
        userData: Pointer?,
        handlerReference: PointerByReference?,
    ): Int

    fun RegisterEventHotKey(
        keyCode: Int,
        modifiers: Int,
        hotKeyId: EventHotKeyId,
        target: Pointer,
        options: Int,
        hotKeyReference: PointerByReference,
    ): Int

    fun UnregisterEventHotKey(hotKeyReference: Pointer): Int

    fun RemoveEventHandler(handlerReference: Pointer): Int

    companion object {
        val INSTANCE: Carbon = Native.load("Carbon", Carbon::class.java)
    }
}
