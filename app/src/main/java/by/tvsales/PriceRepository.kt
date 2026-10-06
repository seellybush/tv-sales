package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

data class FiveElementProduct(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val url: String = ""
)

object PriceRepository {
    private val client = OkHttpClient()

    suspend fun searchTVs(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // Поиск в разделе телевизоров (1403)
            val url = "https://5element.by/catalog/1403-televizory?q=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            parseProducts(html)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun findPrice(modelName: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val products = searchTVs(modelName)
            val match = products.firstOrNull {
                it.name.contains(modelName, ignoreCase = true)
            }
            match?.let { it.price to "5element.by" }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseProducts(html: String): List<FiveElementProduct> {
        val products = mutableListOf<FiveElementProduct>()

        // Главная регулярка: вытаскиваем JSON из data-product='{...}'
        // Пример: data-product='{"id":2054461,"name":"Телевизор Samsung UE43M70HAUXPY","category_name":"Телевизоры","price":1399}'
        val dataProductPattern = Pattern.compile(
            "data-product='(\\{[^']+\\})'"
        )

        val matcher = dataProductPattern.matcher(html)
        while (matcher.find()) {
            val json = matcher.group(1) ?: continue

            // Простейший парсинг JSON вручную (без библиотек)
            val id = extractJsonValue(json, "id")
            val name = extractJsonValue(json, "name")
            val priceStr = extractJsonValue(json, "price")
            val category = extractJsonValue(json, "category_name")

            // Фильтр: только телевизоры
            if (!name.contains("Телевизор", ignoreCase = true)) continue
            if (category.isNotEmpty() && !category.contains("Телевизор", ignoreCase = true)) continue

            val price = priceStr.toDoubleOrNull() ?: continue
            if (price < 50) continue

            // URL формируем из id (упрощённо) — или можно вытащить href отдельно
            products.add(
                FiveElementProduct(
                    id = id,
                    name = name,
                    price = price,
                    url = "https://5element.by/products/$id"
                )
            )
        }

        return products.distinctBy { it.id }
    }

    // Простой парсер значений из JSON-строки
    private fun extractJsonValue(json: String, key: String): String {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*(\"([^\"]*)\"|([\\d.]+))")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) {
            matcher.group(2) ?: matcher.group(3) ?: ""
        } else {
            ""
        }
    }
}
