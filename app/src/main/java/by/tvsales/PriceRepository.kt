package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// Модель для поиска (легковесная)
@Serializable
data class OnlinerSearchResult(
    val products: List<OnlinerProductSummary> = emptyList()
)

@Serializable
data class OnlinerProductSummary(
    val id: Long = 0,
    val key: String = "",
    val name: String = "",
    val full_name: String = "",
    val prices: OnlinerPrices? = null
)

@Serializable
data class OnlinerPrices(
    val price_min: Double? = null,
    val price_max: Double? = null,
    val offers: List<Offer> = emptyList()
)

@Serializable
data class Offer(
    val price: Double = 0.0,
    val shop: Shop? = null
)

@Serializable
data class Shop(val name: String = "")

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // 1. Поиск моделей для автодополнения
    suspend fun searchModels(query: String): List<OnlinerProductSummary> = withContext(Dispatchers.IO) {
        if (query.length < 3) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // Запрос к API Onliner для поиска (неофициальный эндпоинт)
            val url = "https://catalog.onliner.by/sdapi/catalog.api/search/products?query=$encoded&limit=10"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<OnlinerSearchResult>(body)
            parsed.products
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. Поиск цены именно в 5 элементе
    suspend fun findPriceIn5Element(modelName: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(modelName, "UTF-8")
            val url = "https://catalog.onliner.by/sdapi/catalog.api/search/products?query=$encoded&limit=5"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android TV Sales)")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext null
            val parsed = json.decodeFromString<OnlinerSearchResult>(body)
            val product = parsed.products.firstOrNull() ?: return@withContext null

            val offers = product.prices?.offers ?: emptyList()
            // Ищем ТОЛЬКО 5 элемент
            val fiveElement = offers.firstOrNull {
                it.shop?.name?.contains("5 элемент", ignoreCase = true) == true
            }
            
            fiveElement?.let { it.price to (it.shop?.name ?: "5 элемент") }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
