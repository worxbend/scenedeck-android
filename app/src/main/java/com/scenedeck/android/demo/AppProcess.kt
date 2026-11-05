package com.scenedeck.android.demo

/** Selected only from the OS-reported process name, never from a link or profile. */
enum class AppProcess {
    LIVE,
    DEMO;

    companion object {
        fun resolve(packageName: String, processName: String?): AppProcess =
            when (processName) {
                packageName -> LIVE
                "$packageName:demo" -> DEMO
                else -> error("Unrecognized application process")
            }
    }
}
