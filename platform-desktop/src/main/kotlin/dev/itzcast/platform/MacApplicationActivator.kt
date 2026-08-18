package dev.itzcast.platform

import com.sun.jna.NativeLibrary

/** Brings the JVM application to the foreground without a compile-time dependency on AppKit. */
object MacApplicationActivator {
    fun activate() {
        if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) return

        if (runCatching { activateWithAppKit() }.isSuccess) return
        runCatching { activateWithEawt() }
    }

    private fun activateWithAppKit() {
        val objectiveC = NativeLibrary.getInstance("objc")
        val getClass = objectiveC.getFunction("objc_getClass")
        val selector = objectiveC.getFunction("sel_registerName")
        val send = objectiveC.getFunction("objc_msgSend")

        fun classNamed(name: String) = checkNotNull(getClass.invokePointer(arrayOf(name)))
        fun selectorNamed(name: String) = checkNotNull(selector.invokePointer(arrayOf(name)))
        fun sendPointer(receiver: Any, name: String) =
            checkNotNull(send.invokePointer(arrayOf(receiver, selectorNamed(name))))

        val runningApplication = sendPointer(classNamed("NSRunningApplication"), "currentApplication")
        send.invokeInt(
            arrayOf(
                runningApplication,
                selectorNamed("activateWithOptions:"),
                ACTIVATE_ALL_WINDOWS or ACTIVATE_IGNORING_OTHER_APPS,
            ),
        )

        val application = sendPointer(classNamed("NSApplication"), "sharedApplication")
        send.invokeVoid(arrayOf(application, selectorNamed("activateIgnoringOtherApps:"), true))
    }

    private fun activateWithEawt() {
            val applicationClass = Class.forName("com.apple.eawt.Application")
            val application = applicationClass.getMethod("getApplication").invoke(null)
            applicationClass
                .getMethod("requestForeground", Boolean::class.javaPrimitiveType)
                .invoke(application, true)
    }

    private const val ACTIVATE_ALL_WINDOWS = 1
    private const val ACTIVATE_IGNORING_OTHER_APPS = 2
}
