package com.example.data.preferences

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lyric_studio_prefs")

class PreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore

    init {
        try {
            val sp = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (!sp.contains("language")) {
                sp.edit().putString("language", "fa").apply()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "PreferencesManager"

        val KEY_THEME = stringPreferencesKey("theme_id")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_FONT_SIZE = floatPreferencesKey("font_size")
        val KEY_LINE_HEIGHT = floatPreferencesKey("line_height")
        val KEY_LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val KEY_AUTOSAVE_ENABLED = booleanPreferencesKey("autosave_enabled")
        val KEY_AUTOSAVE_DEBOUNCE_MS = longPreferencesKey("autosave_debounce_ms")
        val KEY_GRID_VIEW = booleanPreferencesKey("grid_view_enabled")

        // App Lock keys
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        private val APP_LOCK_PIN_SALT = stringPreferencesKey("app_lock_pin_salt")
    }

    val themeFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_THEME] ?: "midnight"
        }

    val languageFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LANGUAGE] ?: "fa"
        }

    val fontSizeFlow: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_FONT_SIZE] ?: 16f
        }

    val lineHeightFlow: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LINE_HEIGHT] ?: 1.5f
        }

    val letterSpacingFlow: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LETTER_SPACING] ?: 0f
        }

    val autosaveEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_AUTOSAVE_ENABLED] ?: true
        }

    val autosaveDebounceMsFlow: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_AUTOSAVE_DEBOUNCE_MS] ?: 600L
        }

    val appLockEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map {
            it[APP_LOCK_ENABLED] ?: false
        }

    val appLockPinHash: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map {
            it[APP_LOCK_PIN_HASH]
        }

    val appLockEnabledFlow: Flow<Boolean> get() = appLockEnabled
    val appLockPinHashFlow: Flow<String?> get() = appLockPinHash
    val pinHashFlow: Flow<String> get() = appLockPinHash.map { it ?: "" }

    val gridViewFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_GRID_VIEW] ?: false
        }

    suspend fun setTheme(themeId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME] = themeId
        }
    }

    suspend fun setLanguage(lang: String) {
        try {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("language", lang)
                .apply()
        } catch (_: Exception) {}
        dataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = lang
        }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = size
        }
    }

    suspend fun setLineHeight(height: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_LINE_HEIGHT] = height
        }
    }

    suspend fun setLetterSpacing(spacing: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_LETTER_SPACING] = spacing
        }
    }

    suspend fun setAutosaveEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTOSAVE_ENABLED] = enabled
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit {
            it[APP_LOCK_ENABLED] = enabled
            if (!enabled) {
                it.remove(APP_LOCK_PIN_HASH)
                it.remove(APP_LOCK_PIN_SALT)
            }
        }
    }

    suspend fun setAppLockPin(pin: String): Boolean {
        return try {
            val saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val hash = hashPin(pin, saltBase64)

            dataStore.edit {
                it[APP_LOCK_PIN_HASH] = hash
                it[APP_LOCK_PIN_SALT] = saltBase64
                it[APP_LOCK_ENABLED] = true
            }
            Log.d(TAG, "setAppLockPin: PIN saved")
            true
        } catch (e: Exception) {
            Log.e(TAG, "setAppLockPin failed", e)
            false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        return try {
            val prefs = dataStore.data.first()
            val storedHash = prefs[APP_LOCK_PIN_HASH]
            val salt = prefs[APP_LOCK_PIN_SALT]

            Log.d(TAG, "verifyPin: hash=$storedHash salt=$salt")

            if (storedHash.isNullOrEmpty() || salt.isNullOrEmpty()) {
                Log.w(TAG, "verifyPin: no PIN stored")
                return false
            }

            val computed = hashPin(pin, salt)
            val result = computed == storedHash
            Log.d(TAG, "verifyPin: computed=$computed result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "verifyPin error", e)
            false
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        val bytes = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun clearOldPinIfNeeded() {
        try {
            val sp = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            sp.edit().remove("app_lock_pin").apply()
        } catch (_: Exception) {}
    }

    suspend fun setGridView(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_GRID_VIEW] = enabled
        }
    }
}
