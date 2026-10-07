package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

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
    val url: String = ""    // ← сюда сохраняем link_url, чтобы потом парсить гарантию
)

data class AccessoryItem(val name: String, val price: Double)
data class ServiceItem(val name: String, val price: Double)
data class WarrantyOption(val years: Int, val price: Double)

val videoServices = listOf(
    ServiceItem("iTV 6 мес", 89.90),
    ServiceItem("iTV 12 мес", 169.90),
    ServiceItem("iTV 24 мес", 349.90),
    ServiceItem("iTV 36 мес", 499.90),
    ServiceItem("Кинопоиск 6 мес", 99.90),
    ServiceItem("Кинопоиск 12 мес", 179.90),
    ServiceItem("Кинопоиск 24 мес", 329.90),
    ServiceItem("Кинопоиск 36 мес", 449.90),
    ServiceItem("Okko 6 мес", 94.90),
    ServiceItem("Okko 12 мес", 189.90),
    ServiceItem("Okko 24 мес", 324.90),
    ServiceItem("Okko 36 мес", 444.90),
    ServiceItem("VOKA 12 мес", 199.90),
    ServiceItem("VOKA 24 мес", 349.90),
    ServiceItem("VOKA 36 мес", 499.00)
)

object PriceRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val DIGINETICA_API_KEY = "08IE0509XQ"
    private const val DIGINETICA_URL = "https://autocomplete.diginetica.net/autocomplete"

    suspend fun searchTVs(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 1) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$DIGINETICA_URL?st=$encoded" +
                    "&apiKey=$DIGINETICA_API_KEY" +
                    "&strategy=advanced_xname%2Czero_queries" +
                    "&productsSize=50" +
                    "&regionId=global" +
                    "&forIs=true" +
                    "&showUnavailable=true" +
                    "&withContent=false" +
                    "&withSku=false"

            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json").build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<DigineticaResponse>(body)

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
                        url = "https://5element.by${product.link_url}"  // ← сохраняем URL
                    )
                }
                .filter { it.price > 50 }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchBrackets(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$DIGINETICA_URL?st=$encoded" +
                    "&apiKey=$DIGINETICA_API_KEY" +
                    "&strategy=advanced_xname%2Czero_queries" +
                    "&productsSize=50" +
                    "&regionId=global" +
                    "&forIs=true" +
                    "&showUnavailable=true" +
                    "&withContent=false" +
                    "&withSku=false"

            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json").build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val parsed = json.decodeFromString<DigineticaResponse>(body)

            parsed.products
                .filter { product ->
                    product.name.contains("кронштейн", ignoreCase = true) ||
                    product.name.contains("крепление", ignoreCase = true)
                }
                .map { product ->
                    FiveElementProduct(
                        id = product.id,
                        name = product.name,
                        price = product.price.toDoubleOrNull() ?: 0.0,
                        oldPrice = product.oldPrice?.toDoubleOrNull(),
                        diagonal = "",
                        url = "https://5element.by${product.link_url}"
                    )
                }
                .filter { it.price > 5 }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // 🛡️ Парсинг гарантии — ТЕПЕРЬ ПО URL КАРТОЧКИ (а не по ID)
    suspend fun fetchWarrantyByUrl(productUrl: String): List<WarrantyOption> = withContext(Dispatchers.IO) {
        if (productUrl.isEmpty()) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url(productUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val html = resp.body?.string() ?: return@withContext emptyList()

            // Ищем protection-plus data='[{...}]'
            val pattern = Pattern.compile("protection-plus\\s+data='(\\[.*?\\])'", Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            if (!matcher.find()) return@withContext emptyList()

            val jsonStr = matcher.group(1) ?: return@withContext emptyList()
            val result = mutableListOf<WarrantyOption>()

            // Парсим: {"title":"На N год X.XX ...","price":X}
            val itemPattern = Pattern.compile(
                "\\{[^}]*\"title\"\\s*:\\s*\"На\\s+(\\d+)\\s+год[^\"]*?\"[^}]*?\"price\"\\s*:\\s*([\\d.]+)",
                Pattern.DOTALL
            )
            val itemMatcher = itemPattern.matcher(jsonStr)
            while (itemMatcher.find()) {
                val years = itemMatcher.group(1)?.toIntOrNull() ?: continue
                val price = itemMatcher.group(2)?.toDoubleOrNull() ?: continue
                if (years > 0 && price > 0) result.add(WarrantyOption(years, price))
            }
            result.sortedBy { it.years }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractDiagonal(name: String): String {
        val pattern = Regex("(\\d{2,3})(?:[A-Z]|\\s|$)")
        val match = pattern.find(name.uppercase())
        return match?.groupValues?.get(1) ?: ""
    }
}
