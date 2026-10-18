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
    private fun langFactorKey(code: String) = doublePreferencesKey("langfactor_$code")

    private val ctTokenKey = stringPreferencesKey("cardtrader_token")

    val language: Flow<String> = context.dataStore.data.map { it[langKey] ?: "it" }

    /** Token personale CardTrader (vuoto = prezzi per lingua solo stimati). */
    val cardTraderToken: Flow<String> = context.dataStore.data.map { it[ctTokenKey].orEmpty() }

    suspend fun setCardTraderToken(token: String) {
        context.dataStore.edit {
            if (token.isBlank()) it.remove(ctTokenKey) else it[ctTokenKey] = token.trim()
        }
    }

    val factors: Flow<ValueFactors> = context.dataStore.data.map { prefs ->
        ValueFactors(
            condition = Condition.entries.associateWith { prefs[factorKey(it)] ?: it.defaultFactor },
            language = CARD_LANGUAGES.mapNotNull { l -> prefs[langFactorKey(l.code)]?.let { l.code to it } }.toMap(),
        )
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[langKey] = code }
    }

    suspend fun setFactor(c: Condition, value: Double) {
        context.dataStore.edit { it[factorKey(c)] = value }
    }

    suspend fun setLanguageFactor(code: String, value: Double) {
        context.dataStore.edit { it[langFactorKey(code)] = value }
    }

    suspend fun resetFactors() {
        context.dataStore.edit { prefs ->
            Condition.entries.forEach { prefs.remove(factorKey(it)) }
            CARD_LANGUAGES.forEach { prefs.remove(langFactorKey(it.code)) }
        }
    }
}
