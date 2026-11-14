package com.blizzardfire.pumpkinlauncher

import java.io.File

object PumpkinNative {
    @Volatile
    private var loadedPath: String? = null

    val isLoaded: Boolean
        get() = loadedPath != null

    @Synchronized
    fun load(library: File) {
        val path = library.absolutePath
        if (loadedPath == path) return
        if (loadedPath != null) {
            throw IllegalStateException("A different engine is already loaded. Restart PumpkinLauncher to switch versions.")
        }
        if (!library.isFile || library.canWrite()) {
            throw IllegalStateException("The engine library is missing or is not read-only.")
        }
        System.load(path)
        loadedPath = path
    }

    /**
     * Starts the engine, applying an optional RAM limit (MB) when the loaded
     * library exports the newer entry point. Older engines ignore the limit.
     */
    fun start(workDir: String, logFile: String, ramLimitMb: Int): Int {
        return try {
            nativeStartLimited(workDir, logFile, ramLimitMb)
        } catch (_: UnsatisfiedLinkError) {
            nativeStart(workDir, logFile)
        }
    }

    @JvmStatic external fun nativeStart(workDir: String, logFile: String): Int
    @JvmStatic external fun nativeStartLimited(workDir: String, logFile: String, ramLimitMb: Int): Int
    @JvmStatic external fun nativeSendCommand(command: String): Boolean
    @JvmStatic external fun nativeStop(): Boolean
    @JvmStatic external fun nativeIsRunning(): Boolean
}
