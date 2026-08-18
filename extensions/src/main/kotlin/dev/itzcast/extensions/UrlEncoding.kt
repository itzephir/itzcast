package dev.itzcast.extensions

internal fun urlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        when (unsigned) {
            in 'a'.code..'z'.code, in 'A'.code..'Z'.code, in '0'.code..'9'.code,
            '-'.code, '_'.code, '.'.code, '~'.code -> append(unsigned.toChar())
            else -> append('%').append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
