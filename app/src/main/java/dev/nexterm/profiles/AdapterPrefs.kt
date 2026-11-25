package dev.nexterm.profiles

import android.content.Context
import androidx.core.content.edit
import dev.nexterm.usb.VidPid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which profile each adapter model connects with: the one used last, or the one picked in the adapter's details.
 * Kept by VID:PID, like the driver overrides.
 */
class AdapterPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("adapter_prefs", Context.MODE_PRIVATE)
    private val _lastProfile = MutableStateFlow(load())
    /** VID:PID key -> profile id. */
    val lastProfile: StateFlow<Map<String, String>> = _lastProfile.asStateFlow()

    private fun load(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun set(vidPid: VidPid, profileId: String) {
        prefs.edit { putString(vidPid.key, profileId) }
        _lastProfile.value = load()
    }

    companion object {
        @Volatile private var instance: AdapterPrefs? = null
        fun get(context: Context): AdapterPrefs =
            instance ?: synchronized(this) { instance ?: AdapterPrefs(context.applicationContext).also { instance = it } }
    }
}

/** The profile an adapter would connect with: its last used one if it still exists, else the first one saved for it. */
fun profileFor(vidPid: VidPid, profiles: List<Profile>, lastProfileIds: Map<String, String>): Profile? {
    val mine = profiles.filter { it.vidPid == vidPid }
    return mine.firstOrNull { it.id == lastProfileIds[vidPid.key] } ?: mine.firstOrNull()
}
