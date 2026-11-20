package dev.fabik.bluetoothhid.tally

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TallyProfileRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TallyProfileRepository"
        private const val PROFILES_FILE = "tally_profiles.json"
        private const val PREF_ACTIVE_ID = "active_profile_id"
        private const val PREF_AUTOMATION_MODE = "automation_mode"

        @Volatile
        private var instance: TallyProfileRepository? = null

        fun getInstance(context: Context): TallyProfileRepository =
            instance ?: synchronized(this) {
                instance ?: TallyProfileRepository(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("tally_automation_prefs", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<TallyProfile>>(emptyList())
    val profiles: StateFlow<List<TallyProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(TallyProfile.createDefaultProfiles().first())
    val activeProfile: StateFlow<TallyProfile> = _activeProfile.asStateFlow()

    private val _automationMode = MutableStateFlow(AutomationMode.MANUAL)
    val automationMode: StateFlow<AutomationMode> = _automationMode.asStateFlow()

    init {
        loadProfiles()
        loadPreferences()
    }

    private fun loadPreferences() {
        val modeStr = prefs.getString(PREF_AUTOMATION_MODE, AutomationMode.MANUAL.name)
        _automationMode.value = runCatching { AutomationMode.valueOf(modeStr!!) }.getOrDefault(AutomationMode.MANUAL)
    }

    private fun loadProfiles() {
        val file = File(context.filesDir, PROFILES_FILE)
        if (!file.exists()) {
            val defaults = TallyProfile.createDefaultProfiles()
            _profiles.value = defaults
            _activeProfile.value = defaults.first()
            saveProfilesToFile(defaults)
            return
        }

        try {
            val jsonStr = file.readText()
            val parsedList = parseProfilesJson(jsonStr)
            if (parsedList.isNotEmpty()) {
                _profiles.value = parsedList
                val savedActiveId = prefs.getString(PREF_ACTIVE_ID, parsedList.first().id)
                _activeProfile.value = parsedList.find { it.id == savedActiveId } ?: parsedList.first()
            } else {
                val defaults = TallyProfile.createDefaultProfiles()
                _profiles.value = defaults
                _activeProfile.value = defaults.first()
                saveProfilesToFile(defaults)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profiles from file: ${e.message}", e)
            val defaults = TallyProfile.createDefaultProfiles()
            _profiles.value = defaults
            _activeProfile.value = defaults.first()
        }
    }

    private fun saveProfilesToFile(profileList: List<TallyProfile>) {
        scope.launch {
            try {
                val file = File(context.filesDir, PROFILES_FILE)
                val jsonStr = serializeProfilesJson(profileList)
                file.writeText(jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profiles to file: ${e.message}", e)
            }
        }
    }

    fun setAutomationMode(mode: AutomationMode) {
        _automationMode.value = mode
        prefs.edit().putString(PREF_AUTOMATION_MODE, mode.name).apply()
    }

    fun setActiveProfile(profileId: String) {
        val found = _profiles.value.find { it.id == profileId } ?: return
        _activeProfile.value = found
        prefs.edit().putString(PREF_ACTIVE_ID, profileId).apply()
    }

    fun saveProfile(profile: TallyProfile) {
        val currentList = _profiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }
        _profiles.value = currentList
        if (_activeProfile.value.id == profile.id) {
            _activeProfile.value = profile
        }
        saveProfilesToFile(currentList)
    }

    fun duplicateProfile(profileId: String): TallyProfile? {
        val original = _profiles.value.find { it.id == profileId } ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            isDefault = false,
            steps = original.steps.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        val currentList = _profiles.value.toMutableList()
        currentList.add(copy)
        _profiles.value = currentList
        saveProfilesToFile(currentList)
        return copy
    }

    fun deleteProfile(profileId: String): Boolean {
        val currentList = _profiles.value.toMutableList()
        if (currentList.size <= 1) return false // Prevent deleting the last profile
        val removed = currentList.removeAll { it.id == profileId }
        if (removed) {
            _profiles.value = currentList
            if (_activeProfile.value.id == profileId) {
                _activeProfile.value = currentList.first()
                prefs.edit().putString(PREF_ACTIVE_ID, currentList.first().id).apply()
            }
            saveProfilesToFile(currentList)
        }
        return removed
    }

    fun restoreDefaults() {
        val defaults = TallyProfile.createDefaultProfiles()
        _profiles.value = defaults
        _activeProfile.value = defaults.first()
        prefs.edit().putString(PREF_ACTIVE_ID, defaults.first().id).apply()
        saveProfilesToFile(defaults)
    }

    fun exportProfilesJson(): String = serializeProfilesJson(_profiles.value)

    fun importProfilesJson(jsonStr: String): Boolean {
        return try {
            val imported = parseProfilesJson(jsonStr)
            if (imported.isNotEmpty()) {
                _profiles.value = imported
                _activeProfile.value = imported.first()
                prefs.edit().putString(PREF_ACTIVE_ID, imported.first().id).apply()
                saveProfilesToFile(imported)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import profiles: ${e.message}", e)
            false
        }
    }

    // JSON Serialization Helpers
    private fun serializeProfilesJson(list: List<TallyProfile>): String {
        val array = JSONArray()
        for (profile in list) {
            val pObj = JSONObject()
            pObj.put("id", profile.id)
            pObj.put("name", profile.name)
            pObj.put("description", profile.description)
            pObj.put("isDefault", profile.isDefault)

            val stepsArray = JSONArray()
            for (step in profile.steps) {
                val sObj = JSONObject()
                sObj.put("id", step.id)
                sObj.put("type", step.type.name)
                sObj.put("key", step.key)
                val modArray = JSONArray()
                step.modifiers.forEach { modArray.put(it) }
                sObj.put("modifiers", modArray)
                sObj.put("delayMs", step.delayMs)
                sObj.put("textPayload", step.textPayload)
                sObj.put("isEnabled", step.isEnabled)
                stepsArray.put(sObj)
            }
            pObj.put("steps", stepsArray)
            array.put(pObj)
        }
        return array.toString(2)
    }

    private fun parseProfilesJson(jsonStr: String): List<TallyProfile> {
        val list = mutableListOf<TallyProfile>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val pObj = array.getJSONObject(i)
            val id = pObj.optString("id", UUID.randomUUID().toString())
            val name = pObj.getString("name")
            val desc = pObj.optString("description", "")
            val isDefault = pObj.optBoolean("isDefault", false)

            val steps = mutableListOf<TallyStep>()
            val stepsArray = pObj.optJSONArray("steps")
            if (stepsArray != null) {
                for (j in 0 until stepsArray.length()) {
                    val sObj = stepsArray.getJSONObject(j)
                    val sId = sObj.optString("id", UUID.randomUUID().toString())
                    val type = runCatching { TallyActionType.valueOf(sObj.getString("type")) }
                        .getOrDefault(TallyActionType.PRESS_ENTER)
                    val key = sObj.optString("key", "ENTER")
                    val delayMs = sObj.optLong("delayMs", 300L)
                    val textPayload = sObj.optString("textPayload", "")
                    val isEnabled = sObj.optBoolean("isEnabled", true)

                    val modArray = sObj.optJSONArray("modifiers")
                    val modifiers = mutableListOf<String>()
                    if (modArray != null) {
                        for (k in 0 until modArray.length()) {
                            modifiers.add(modArray.getString(k))
                        }
                    }

                    steps.add(
                        TallyStep(
                            id = sId,
                            type = type,
                            key = key,
                            modifiers = modifiers,
                            delayMs = delayMs,
                            textPayload = textPayload,
                            isEnabled = isEnabled
                        )
                    )
                }
            }
            list.add(TallyProfile(id, name, desc, steps, isDefault))
        }
        return list
    }
}
