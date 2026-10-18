package it.cartella.tcg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val langKey = stringPreferencesKey("lang")

    private fun factorKey(c: Condition) = doublePreferencesKey("factor_${c.code}")

    val language: Flow<String> = context.dataStore.data.map { it[langKey] ?: "it" }

    val factors: Flow<ValueFactors> = context.dataStore.data.map { prefs ->
        ValueFactors(condition = Condition.entries.associateWith { prefs[factorKey(it)] ?: it.defaultFactor })
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[langKey] = code }
    }

    suspend fun setFactor(c: Condition, value: Double) {
        context.dataStore.edit { it[factorKey(c)] = value }
    }

    suspend fun resetFactors() {
        context.dataStore.edit { prefs -> Condition.entries.forEach { prefs.remove(factorKey(it)) } }
    }
}
