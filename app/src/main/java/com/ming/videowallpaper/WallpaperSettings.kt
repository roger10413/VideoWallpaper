package com.ming.videowallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

enum class SwitchMode { AFTER_EACH, INTERVAL, ON_RETURN }

enum class ScaleMode { FILL, FIT }

/**
 * Settings shared by the settings screen and the wallpaper engine. Both run in the same process,
 * so the engine picks up changes through [SharedPreferences.OnSharedPreferenceChangeListener].
 */
class WallpaperSettings(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Content URIs, one per line (URIs never contain newlines). */
    var playlist: List<Uri>
        get() = prefs.getString(KEY_PLAYLIST, "")!!
            .lineSequence().filter { it.isNotBlank() }.map(Uri::parse).toList()
        set(value) = prefs.edit { putString(KEY_PLAYLIST, value.joinToString("\n")) }

    var switchMode: SwitchMode
        get() = enumValueOr(prefs.getString(KEY_SWITCH_MODE, null), SwitchMode.AFTER_EACH)
        set(value) = prefs.edit { putString(KEY_SWITCH_MODE, value.name) }

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_MINUTES, 10)
        set(value) = prefs.edit { putInt(KEY_INTERVAL_MINUTES, value) }

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit { putBoolean(KEY_SHUFFLE, value) }

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ON, false)
        set(value) = prefs.edit { putBoolean(KEY_SOUND_ON, value) }

    var scaleMode: ScaleMode
        get() = enumValueOr(prefs.getString(KEY_SCALE_MODE, null), ScaleMode.FILL)
        set(value) = prefs.edit { putString(KEY_SCALE_MODE, value.name) }

    /** e-invoice mobile barcode (手機條碼), e.g. "/ABC1234"; empty when not set. */
    var carrierCode: String
        get() = prefs.getString(KEY_CARRIER_CODE, "")!!
        set(value) = prefs.edit { putString(KEY_CARRIER_CODE, value) }

    /** Show the carrier barcode as an ongoing notification, visible on the lock screen. */
    var carrierNotify: Boolean
        get() = prefs.getBoolean(KEY_CARRIER_NOTIFY, false)
        set(value) = prefs.edit { putBoolean(KEY_CARRIER_NOTIFY, value) }

    /** Last video the wallpaper showed, so it resumes there after a restart. Written by the engine. */
    var currentUri: Uri?
        get() = prefs.getString(KEY_CURRENT_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit { putString(KEY_CURRENT_URI, value?.toString()) }

    private inline fun <reified T : Enum<T>> enumValueOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val KEY_PLAYLIST = "playlist"
        const val KEY_SWITCH_MODE = "switch_mode"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_SOUND_ON = "sound_on"
        const val KEY_SCALE_MODE = "scale_mode"
        const val KEY_CURRENT_URI = "current_uri"
        const val KEY_CARRIER_CODE = "carrier_code"
        const val KEY_CARRIER_NOTIFY = "carrier_notify"

        val INTERVAL_CHOICES = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)
    }
}
