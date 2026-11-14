package com.blizzardfire.pumpkinlauncher

import android.content.Context
import java.io.File

data class LaunchSettings(
    val ramLimitMb: Int = 0,
    val prelaunchCommand: String = "",
    val wrapperCommand: String = "",
    val argsCommand: String = ""
)

object ServerSettings {
    private const val PREFS = "pumpkin_server_settings"
    private const val KEY_RAM_LIMIT_MB = "ram_limit_mb"
    private const val KEY_PRELAUNCH = "prelaunch_command"
    private const val KEY_WRAPPER = "wrapper_command"
    private const val KEY_ARGS = "args_command"

    fun load(context: Context): LaunchSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return LaunchSettings(
            ramLimitMb = prefs.getInt(KEY_RAM_LIMIT_MB, 0).coerceAtLeast(0),
            prelaunchCommand = prefs.getString(KEY_PRELAUNCH, "") ?: "",
            wrapperCommand = prefs.getString(KEY_WRAPPER, "") ?: "",
            argsCommand = prefs.getString(KEY_ARGS, "") ?: ""
        )
    }

    fun save(context: Context, settings: LaunchSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RAM_LIMIT_MB, settings.ramLimitMb.coerceAtLeast(0))
            .putString(KEY_PRELAUNCH, settings.prelaunchCommand)
            .putString(KEY_WRAPPER, settings.wrapperCommand)
            .putString(KEY_ARGS, settings.argsCommand)
            .apply()
    }
}

object ServerLocation {
    private const val PREFS = "pumpkin_server_settings"
    private const val KEY_SERVER_DIR = "server_dir"

    /** The directory the engine runs in. Falls back to the legacy worlds folder. */
    fun getServerDir(context: Context): File {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_DIR, null)
        if (saved != null) {
            val dir = File(saved)
            dir.mkdirs()
            return dir
        }
        return WorldManager.getActiveWorldDir(context)
    }

    fun setServerDir(context: Context, dir: File) {
        dir.mkdirs()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_DIR, dir.absolutePath)
            .apply()
    }

    fun resetToDefault(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SERVER_DIR)
            .apply()
    }

    fun isCustom(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_SERVER_DIR)
}
