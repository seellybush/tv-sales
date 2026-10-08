package by.tvsales

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_stats_prefs")

@Serializable
data class SaleData(
    val id: Long = 0L,
    val model: String = "",
    val price: Double = 0.0,
    val employee: String = "",
    val category: String = "tovar",
    val productId: String = "",
    val productUrl: String = "",
    val accessories: List<AccessoryItem> = emptyList(),
    val services: List<ServiceItem> = emptyList()
)

@Serializable
data class PlanData(
    val product: Double = 0.0,
    val accessories: Double = 0.0,
    val service: Double = 0.0
)

@Serializable
data class AppState(
    val employees: List<String> = listOf("Дядя Жора", "Максимыч", "Дядя Володя"),
    val sales: List<SaleData> = emptyList(),
    val plans: Map<String, PlanData> = emptyMap(),
    val employeeBrands: Map<String, String> = mapOf(
        "Дядя Жора" to "TCL",
        "Максимыч" to "Quantum",
        "Дядя Володя" to "LG"
    )
)

object DataStoreManager {

    private val STATE_KEY = stringPreferencesKey("app_state")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }

    val defaultState = AppState()

    fun getState(context: Context): Flow<AppState> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[STATE_KEY] ?: return@map defaultState
            try {
                json.decodeFromString(AppState.serializer(), raw)
            } catch (e: Exception) {
                e.printStackTrace()
                defaultState
            }
        }

    suspend fun saveState(context: Context, state: AppState) {
        try {
            val raw = json.encodeToString(AppState.serializer(), state)
            context.dataStore.edit { prefs ->
                prefs[STATE_KEY] = raw
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
