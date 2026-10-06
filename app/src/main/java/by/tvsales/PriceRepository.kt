package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

data class FiveElementProduct(
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val diagonal: String = "",
    val url: String = ""
)

object PriceRepository {
    private val client = OkHttpClient()

    // 🔍 Поиск по 5element.by — прямой парсинг HTML
    suspend fun searchTVs(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 1) return@withContext emptyList()
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

    // 💰 Поиск цены конкретной модели — первый результат
    suspend fun findPrice(modelName: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val products = searchTVs(modelName)
            // Ищем точное совпадение по модели (без "Телевизор")
            val match = products.firstOrNull {
                it.name.contains(modelName, ignoreCase = true)
            } ?: products.firstOrNull()
            
            match?.let { it.price to "5element.by" }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 🧠 Парсинг HTML — вытаскиваем JSON из data-product='{...}'
    private fun parseProducts(html: String): List<FiveElementProduct> {
        val products = mutableListOf<FiveElementProduct>()

        // Главная регулярка: JSON внутри data-product='{...}'
        val dataProductPattern = Pattern.compile(
            "data-product='(\\{[^']+\\})'"
        )

        val matcher = dataProductPattern.matcher(html)
        while (matcher.find()) {
            val json = matcher.group(1) ?: continue

            val id = extractJsonValue(json, "id")
            val name = extractJsonValue(json, "name")
            val priceStr = extractJsonValue(json, "price")
            val category = extractJsonValue(json, "category_name")

            // Фильтр: только телевизоры
            if (!name.contains("Телевизор", ignoreCase = true)) continue
            if (category.isNotEmpty() && !category.contains("Телевизор", ignoreCase = true)) continue

            val price = priceStr.toDoubleOrNull() ?: continue
            if (price < 50) continue

            // Извлекаем диагональ из названия: "Телевизор Samsung UE55M80HAUXPY" → "55"
            val diagonal = extractDiagonal(name)

            products.add(
                FiveElementProduct(
                    code = id,
                    name = name,
                    price = price,
                    diagonal = diagonal,
                    url = "https://5element.by/products/$id"
                )
            )
        }

        return products.distinctBy { it.code }
    }

    // 📐 Извлечение диагонали из названия
    private fun extractDiagonal(name: String): String {
        // Ищем паттерн: цифры 2-3 знака, опционально перед " или после букв
        // Примеры: "55QNED72", "43QLED780K", "UE55M80HAUXPY" → 55
        val pattern = Pattern.compile("(?:UE|QN|OLED|QLED|NU|U|M|F|G|C|P|H|B|A|KU|KS|MU|KU|K|J|S|X|W|V|T|R|L|E|D|X|W|V|T|R|L|E|D|[A-Z])?(\\d{2,3})(?:[A-Z]|$)")
        val matcher = pattern.matcher(name.uppercase())
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    // 🔧 Простой парсер значений из JSON-строки
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
