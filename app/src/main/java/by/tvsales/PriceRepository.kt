package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

// === Модели для Diginetica ===
@Serializable
data class DigineticaResponse(
    val query: String = "",
    val products: List<DigineticaProduct> = emptyList()
)

@Serializable
data class DigineticaProduct(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val price: String = "",
    val oldPrice: String? = null,
    val link_url: String = "",
    val categories: List<DigineticaCategory> = emptyList()
)

@Serializable
data class DigineticaCategory(
    val id: String = "",
    val name: String = "",
    val link_url: String = ""
)

data class FiveElementProduct(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val oldPrice: Double? = null,
    val diagonal: String = "",
    val url: String = ""
)

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // 🔑 Публичный API-ключ Diginetica (из запроса 5element.by)
    private const val DIGINETICA_API_KEY = "08IE0509XQ"
    private const val DIGINETICA_URL = "https://autocomplete.diginetica.net/autocomplete"

    // 🔍 Поиск телевизоров через Diginetica (тот же поиск, что на 5element.by)
    suspend fun searchTVs(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 1) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$DIGINETICA_URL?st=$encoded" +
                    "&apiKey=$DIGINETICA_API_KEY" +
                    "&strategy=advanced_xname%2Czero_queries" +
                    "&productsSize=50" +          // ← до 50 товаров
                    "&regionId=global" +
                    "&forIs=true" +
                    "&showUnavailable=true" +
                    "&withContent=false" +
                    "&withSku=false"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()

            println("DIGINETICA_RESPONSE: $body")

            val parsed = json.decodeFromString<DigineticaResponse>(body)

            // Фильтр: только телевизоры
            parsed.products
                .filter { product ->
                    product.categories.any { it.name.contains("Телевизор", ignoreCase = true) }
                }
                .map { product ->
                    FiveElementProduct(
                        id = product.id,
                        name = product.name,
                        price = product.price.toDoubleOrNull() ?: 0.0,
                        oldPrice = product.oldPrice?.toDoubleOrNull(),
                        diagonal = extractDiagonal(product.name),
                        url = "https://5element.by${product.link_url}"
                    )
                }
                .filter { it.price > 50 }  // отсеиваем мусор
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 💰 Поиск цены конкретной модели — первый результат
    suspend fun findPrice(modelName: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val products = searchTVs(modelName)
            val match = products.firstOrNull {
                it.name.contains(modelName, ignoreCase = true)
            } ?: products.firstOrNull()

            match?.let { it.price to "5element.by" }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 📐 Извлечение диагонали из названия
    private fun extractDiagonal(name: String): String {
        // Ищем паттерн: 2-3 цифры, опционально перед буквами
        // Примеры: "55QNED72B6B", "43QLED780K", "UE55M80HAUXPY" → 55
        val pattern = Regex("(\\d{2,3})(?:[A-Z]|\\s|$)")
        val match = pattern.find(name.uppercase())
        return match?.groupValues?.get(1) ?: ""
    }
}
