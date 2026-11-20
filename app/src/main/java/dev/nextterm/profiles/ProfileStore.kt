package dev.nextterm.profiles

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Profiles as a JSON array in SharedPreferences: a handful of small records doesn't need a
 * database, and org.json ships with Android (no extra dependency).
 */
class ProfileStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private fun load(): List<Profile> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { Profile.fromJson(arr.getJSONObject(it)) }
    }.onFailure { Log.e(TAG, "corrupt profile store", it) }.getOrDefault(emptyList())

    private fun persist(list: List<Profile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit { putString(KEY, arr.toString()) }
        _profiles.value = list
    }

    fun get(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    /** Insert or replace by id. */
    fun upsert(p: Profile) {
        val list = _profiles.value.toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list += p
        persist(list.sortedBy { it.name.lowercase() })
        Log.i(TAG, "saved profile '${p.name}' ${p.vidPid} path=${p.devicePath} ${p.config.summary}")
    }

    fun delete(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
    }

    companion object {
        private const val TAG = "NextTerm.Profile"
        private const val KEY = "profiles_v1"
        @Volatile private var instance: ProfileStore? = null
        fun get(context: Context): ProfileStore =
            instance ?: synchronized(this) { instance ?: ProfileStore(context.applicationContext).also { instance = it } }
    }
}
