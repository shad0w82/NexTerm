package dev.nexterm.usb

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user-forced driver choices per VID:PID (like Serial USB Terminal's "custom probe").
 * SharedPreferences is enough for a small key->value map; profiles will get their own store later.
 */
class DriverOverrideStore(context: Context) {
    private val prefs = context.getSharedPreferences("driver_overrides", Context.MODE_PRIVATE)
    private val _overrides = MutableStateFlow(load())
    val overrides: StateFlow<Map<VidPid, DriverType>> = _overrides.asStateFlow()

    private fun load(): Map<VidPid, DriverType> =
        prefs.all.mapNotNull { (k, v) ->
            val vp = VidPid.parse(k) ?: return@mapNotNull null
            val dt = DriverType.fromName(v as? String) ?: return@mapNotNull null
            vp to dt
        }.toMap()

    fun set(vidPid: VidPid, driver: DriverType?) {
        prefs.edit {
            if (driver == null) remove(vidPid.key) else putString(vidPid.key, driver.name)
        }
        _overrides.value = load()
    }
}
