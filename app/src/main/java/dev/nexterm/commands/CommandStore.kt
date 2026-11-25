package dev.nexterm.commands

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What a saved command appends after its text. */
enum class CommandEnding(val label: String) {
    SESSION("Session default"), CR("CR"), LF("LF"), CRLF("CR+LF"), NONE("None"),
}

/**
 * A command kept for one tap: [text] is what to type, or hex bytes like "01 03 00 6B" when [hex] is set (then no
 * line ending is added). The same list is shared by every profile.
 */
data class SavedCommand(
    val id: String,
    val name: String,
    val text: String,
    val hex: Boolean = false,
    val ending: CommandEnding = CommandEnding.SESSION,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("text", text).put("hex", hex).put("ending", ending.name)

    companion object {
        fun fromJson(o: JSONObject): SavedCommand? = runCatching {
            SavedCommand(
                id = o.getString("id"),
                name = o.getString("name"),
                text = o.getString("text"),
                hex = o.optBoolean("hex", false),
                ending = runCatching { CommandEnding.valueOf(o.optString("ending")) }.getOrDefault(CommandEnding.SESSION),
            )
        }.getOrNull()
    }
}

/** The saved commands as a JSON array in SharedPreferences, in the order they were made. */
class CommandStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("commands", Context.MODE_PRIVATE)
    private val _commands = MutableStateFlow(load())
    val commands: StateFlow<List<SavedCommand>> = _commands.asStateFlow()

    private fun load(): List<SavedCommand> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { SavedCommand.fromJson(arr.getJSONObject(it)) }
    }.onFailure { Log.e(TAG, "corrupt command store", it) }.getOrDefault(emptyList())

    private fun persist(list: List<SavedCommand>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit { putString(KEY, arr.toString()) }
        _commands.value = list
    }

    /** Replaces the command with the same id, or adds it at the end. */
    fun upsert(c: SavedCommand) {
        val list = _commands.value.toMutableList()
        val i = list.indexOfFirst { it.id == c.id }
        if (i >= 0) list[i] = c else list += c
        persist(list)
    }

    fun delete(id: String) = persist(_commands.value.filterNot { it.id == id })

    companion object {
        private const val TAG = "NexTerm.Commands"
        private const val KEY = "commands_v1"
        @Volatile private var instance: CommandStore? = null
        fun get(context: Context): CommandStore =
            instance ?: synchronized(this) { instance ?: CommandStore(context.applicationContext).also { instance = it } }
    }
}
