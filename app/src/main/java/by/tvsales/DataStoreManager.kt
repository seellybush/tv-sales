package by.tvsales

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_stats_prefs")

object DataStoreManager {

    private val EMPLOYEES_KEY = stringPreferencesKey("employees")
    private val SALES_KEY = stringPreferencesKey("sales")
    private val PLANS_KEY = stringPreferencesKey("plans")

    val defaultEmployees = listOf("Дядя Жора", "Максимыч", "Дядя Володя")

    fun getEmployees(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[EMPLOYEES_KEY]?.split("|")?.filter { it.isNotBlank() } ?: defaultEmployees
        }

    suspend fun saveEmployees(context: Context, employees: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[EMPLOYEES_KEY] = employees.joinToString("|")
        }
    }

    fun getSales(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[SALES_KEY] ?: "" }

    suspend fun saveSales(context: Context, salesJson: String) {
        context.dataStore.edit { prefs -> prefs[SALES_KEY] = salesJson }
    }

    fun getPlans(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[PLANS_KEY] ?: "" }

    suspend fun savePlans(context: Context, plansJson: String) {
        context.dataStore.edit { prefs -> prefs[PLANS_KEY] = plansJson }
    }
}
