package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Serializable
data class OnlinerProduct(
    val id: Long = 0,
    val full_name: String = "",
    val name: String = "",
    val prices: Map<String, PriceOffer> = emptyMap()
)

@Serializable
data class PriceOffer(
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

@Serializable
data class OnlinerSearchResponse(val products: List<OnlinerProduct> = emptyList())

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun findPrice(model: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(model, "UTF-8")
            val url = "https://catalog.onliner.by/sdapi/catalog.api/search/products?query=$query&limit=5"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android TV Sales)")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null

            val parsed = json.decodeFromString<OnlinerSearchResponse>(body)
            val product = parsed.products.firstOrNull() ?: return@withContext null

            val offers = product.prices.values.flatMap { it.offers }
            val fiveElement = offers.firstOrNull { it.shop?.name?.contains("5 элемент", true) == true }
            val chosen = fiveElement ?: offers.minByOrNull { it.price }

            chosen?.let { it.price to (it.shop?.name ?: "Onliner") }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
