package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// === Модели для search_products ===
@Serializable
data class SearchResponse(
    val status: String = "",
    val data: SearchData = SearchData()
)

@Serializable
data class SearchData(
    val products: List<ProductSummary> = emptyList(),
    val total: Int = 0
)

@Serializable
data class ProductSummary(
    val id: Long = 0,
    val key: String = "",
    val name: String = "",
    val name_prefix: String = "",
    val manufacturer: String = "",
    val description: String = "",
    val price_min: PriceAmount? = null,
    val price_max: PriceAmount? = null,
    val offers_count: Int? = null
)

@Serializable
data class PriceAmount(
    val amount: Double = 0.0,
    val currency: String = "BYN"
)

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // 🔑 Твой API-ключ
    private const val API_KEY = "pmx_3c60c7a62efd2d9954d003e44e87fe31"
    
    // ⚠️ СЮДА ВСТАВЬ ID СКРИПТА ИЗ URL Parse.bot
    // Посмотри адрес, когда нажимаешь "Send Request": 
    // https://api.parse.bot/scraper/XXXXXXXX-XXXX-XXXX/...
    // Вот эти XXXX — и есть SCRAPER_ID
    private const val SCRAPER_ID = "ВСТАВЬ_ID_СКРИПТА"

    // 1. Поиск моделей для автодополнения
    suspend fun searchModels(query: String): List<ProductSummary> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.parse.bot/scraper/$SCRAPER_ID/search_products?query=$encoded&page=1"
            
            val req = Request.Builder()
                .url(url)
                .header("X-API-Key", API_KEY)
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            
            println("SEARCH_RESPONSE: $body")
            
            val parsed = json.decodeFromString<SearchResponse>(body)
            parsed.data.products
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 2. Поиск цены именно в 5element
    // ⚠️ ВРЕМЕННО: возвращает минимальную цену из search_products
    // Как только пришлёшь JSON от get_product_offers — перепишу
    suspend fun findPriceIn5Element(product: ProductSummary): Pair<Double, String>? = withContext(Dispatchers.IO) {
        // TODO: заменить на запрос get_product_offers?id=${product.id}
        val price = product.price_min?.amount ?: return@withContext null
        price to "Onliner (мин. цена)"
    }
}
