package by.tvsales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

data class FiveElementProduct(
    val name: String = "",
    val price: Double = 0.0,
    val url: String = ""
)

object PriceRepository {
    private val client = OkHttpClient()

    // Поиск телевизоров на 5element.by
    suspend fun searchTVs(query: String): List<FiveElementProduct> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // Поиск по каталогу телевизоров (раздел 1403)
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

    // Поиск цены конкретной модели
    suspend fun findPrice(modelName: String): Pair<Double, String>? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(modelName, "UTF-8")
            val url = "https://5element.by/catalog/1403-televizory?q=$encoded"
            
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null
            
            val products = parseProducts(html)
            val match = products.firstOrNull { 
                it.name.contains(modelName, ignoreCase = true) ||
                modelName.contains(it.name, ignoreCase = true)
            }
            
            match?.let { it.price to "5element.by" }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Парсинг HTML — вытаскиваем названия и цены
    private fun parseProducts(html: String): List<FiveElementProduct> {
        val products = mutableListOf<FiveElementProduct>()
        
        // Регулярка для карточек товаров (упрощённая, но рабочая)
        // Ищем блоки с названием "Телевизор ..." и ценой "1 234.56"
        val productPattern = Pattern.compile(
            "Телевизор\\s+([^<]{5,80}?)\\s*</[^>]+>.*?([\\d\\s]+\\.[\\d]{2})\\s*",
            Pattern.DOTALL
        )
        
        val matcher = productPattern.matcher(html)
        while (matcher.find()) {
            val name = matcher.group(1).trim()
            val priceStr = matcher.group(2).replace(" ", "").replace(",", ".")
            val price = priceStr.toDoubleOrNull() ?: continue
            
            // Фильтр: только телевизоры, цена > 100 BYN
            if (price > 100 && name.length > 3) {
                products.add(FiveElementProduct(name = name, price = price))
            }
        }
        
        return products.distinctBy { it.name }
    }
}
