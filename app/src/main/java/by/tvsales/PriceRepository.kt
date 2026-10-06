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

// === Модели для get_product_offers ===
@Serializable
data class OffersResponse(
    val status: String = "",
    val data: OffersData = OffersData()
)

@Serializable
data class OffersData(
    val product_key: String = "",
    val offers: List<ShopOffer> = emptyList(),
    val offers_count: Int = 0,
    val min_price: PriceAmount? = null
)

@Serializable
data class ShopOffer(
    val offer_id: String = "",
    val shop_id: Int = 0,
    val shop_name: String = "",
    val shop_url: String = "",
    val price: PriceAmount = PriceAmount(),
    val warranty_months: Int = 0
)

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // 🔑 Твой API-ключ и ID скрипта
    private const val API_KEY = "pmx_3c60c7a62efd2d9954d003e44e87fe31"
    private const val SCRAPER_ID = "2c3e3cc7-b56c-4867-b591-78b13607cfc1"

    // 1. Поиск моделей
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

    // 2. Поиск цены именно в 5element по ключу товара
    suspend fun findPriceIn5Element(productKey: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(productKey, "UTF-8")
            val url = "https://api.parse.bot/scraper/$SCRAPER_ID/get_product_offers?id=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("X-API-Key", API_KEY)
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null

            println("OFFERS_RESPONSE: $body")

            val parsed = json.decodeFromString<OffersResponse>(body)

            // Ищем ТОЛЬКО 5 элемент
            val fiveElement = parsed.data.offers.firstOrNull {
                it.shop_name.contains("5 элемент", ignoreCase = true) ||
                it.shop_name.contains("5элемент", ignoreCase = true) ||
                it.shop_name.contains("5element", ignoreCase = true) ||
                it.shop_name.contains("5 element", ignoreCase = true)
            }

            if (fiveElement == null) {
                return@withContext null
            }

            fiveElement.price.amount to fiveElement.shop_name
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
