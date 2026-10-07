package by.tvsales

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

object OcrHelper {

    suspend fun recognizeText(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it.text) }
                    .addOnFailureListener { continuation.resume("") }
            } catch (e: Exception) {
                continuation.resume("")
            }
        }

    fun extractModel(text: String): String {
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val upperLines = rawLines.map { it.uppercase() }

        // === 🚫 ЗАПРЕЩЁННЫЕ СЛОВА (если есть — строка мусорная) ===
        val forbiddenWords = listOf(
            "S/N", "SERIAL", "W/O", "DATE", "VOLTAGE", "WEIGHT", "DIMENSIONS",
            "КГ", "ММ", "ВТ", "HZ", "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ", "АДРЕС",
            "ТЕЛ", "EAC", "TPB", "HDMI", "USB", "HDCP", "WEBOS", "TIZEN",
            "ANDROID", "СМАРТ", "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР", "ИЗГОТОВИТЕЛЬ",
            "ПРОИЗВОДИТЕЛЬ", "ИМПОРТЕР", "СДЕЛАНО", "РОССИЯ", "БЕЛАРУСЬ",
            "ПРОИЗВОДСТВЕННАЯ", "ПЛОЩАДКА", "ООО", "ГРУППА", "КОМПАНИЙ",
            "ИНФОРМАЦИОННЫЙ", "ЦЕНТР", "БЕСПЛАТНЫЙ", "ЗВОНОК", "СВЯЗЬ",
            "РАЗРЕШЕНИЕ", "ДИАГОНАЛЬ", "ПИТАНИЕ", "НАПРЯЖЕНИЕ",
            "ЭКРАН", "ТИП", "МОЩНОСТЬ", "ВЕС", "ГАБАРИТ", "СТРАНА",
            "СЕРТИФИКАТ", "СТАНДАРТ", "ПАМЯТЬ", "ПРОЦЕССОР", "ЧАСТОТА",
            "ULTRA", "FULL", "PIX", "ПИКС", "SMART", "TV"
        )

        // === 🔒 СТРОГИЙ ЗАПРЕТ: разрешения и напряжения ===
        val resolutionNumbers = listOf(
            "3840", "2160", "1920", "1080", "1366", "768", "720", "4096",
            "2560", "1440", "1600", "900", "1200", "800"
        )

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            
            // 1. Длина 4–15 символов
            if (upper.length < 4 || upper.length > 15) return false
            
            // 2. Нет запрещённых слов
            if (forbiddenWords.any { upper.contains(it) }) return false
            
            // 3. Нет разрешений внутри строки
            if (resolutionNumbers.any { upper.contains(it) }) return false
            
            // 4. Есть и буквы, и цифры
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false
            
            // 5. ❗ КЛЮЧЕВОЕ: НЕТ буквы X или N между цифрами (это разрешение 3840x2160)
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false
            
            // 6. ❗ КЛЮЧЕВОЕ: диагональ в начале должна быть 24–98
            val diagMatch = Regex("^(\\d{2,3})").find(upper)
            val diag = diagMatch?.groupValues?.get(1)?.toIntOrNull() ?: return false
            if (diag < 24 || diag > 98) return false
            
            // 7. Нет подряд 4+ цифр (это серийник или разрешение)
            if (Regex("\\d{4,}").containsMatchIn(upper)) return false
            
            // 8. Не заканчивается на "MM" или "KG"
            if (upper.endsWith("MM") || upper.endsWith("KG")) return false
            
            return true
        }

        fun normalize(s: String): String {
            return s.uppercase()
                .replace(" ", "")
                .replace(".", "")
                .replace("-", "")
                .replace("Х", "X")
                .trim()
        }

        // Паттерн модели: 2-3 цифры (24-98) + буквы/цифры
        val modelPattern = Pattern.compile(
            "\\b(2[4-9]|[3-8]\\d|9[0-8])([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})\\b"
        )

        // === ПРИОРИТЕТ 1: QUANTUM ===
        for (i in rawLines.indices) {
            if (rawLines[i].contains("Quantum", ignoreCase = true)) {
                val sameLine = normalize(rawLines[i].replace("Quantum", "", ignoreCase = true))
                if (isCleanModel(sameLine)) return sameLine
                if (i + 1 < rawLines.size) {
                    val next = normalize(rawLines[i + 1])
                    if (isCleanModel(next)) return next
                }
            }
        }

        // === ПРИОРИТЕТ 2: LG (игнорируем S/N) ===
        val isLG = upperLines.any { it.contains("LG") || it.contains("ABYGLJU") }
        if (isLG) {
            for (line in rawLines) {
                val upper = line.uppercase()
                if (upper.contains("S/N") || upper.contains("SERIAL") || upper.contains("W/O")) continue
                val cleaned = normalize(upper)
                val m = modelPattern.matcher(cleaned)
                if (m.find()) {
                    val candidate = m.group(0)
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === ПРИОРИТЕТ 3: Строка после MODEL/МОДЕЛЬ ===
        for (i in rawLines.indices) {
            val upper = upperLines[i]
            if (upper == "MODEL" || upper.startsWith("MODEL ") || upper == "МОДЕЛЬ" || upper.startsWith("МОДЕЛЬ ")) {
                val sameLine = normalize(upper.replace("MODEL", "").replace("МОДЕЛЬ", ""))
                if (isCleanModel(sameLine)) return sameLine
                if (i + 1 < rawLines.size) {
                    val next = normalize(rawLines[i + 1])
                    if (isCleanModel(next)) return next
                }
            }
        }

        // === ПРИОРИТЕТ 4: Строка после бренда ===
        val brands = listOf(
            "TCL", "SAMSUNG", "HISENSE", "SONY", "PHILIPS", "XIAOMI", "LG",
            "ROOME", "HAIER", "QUANTUM", "HORIZONT", "ВИТЯЗЬ", "ASANO",
            "HYUNDAI", "HARPER", "LEFF", "DREAME", "ЯНДЕКС", "KIVI", "FOX",
            "EVO", "MIDEA", "TOSHIBA", "PANASONIC", "H"
        )
        for (i in rawLines.indices) {
            if (brands.any { upperLines[i] == it || upperLines[i].startsWith("$it ") }) {
                if (i + 1 < rawLines.size) {
                    val next = normalize(rawLines[i + 1])
                    if (isCleanModel(next)) return next
                }
            }
        }

        // === ПРИОРИТЕТ 5: Первые 7 строк с паттерном модели ===
        for (i in 0 until minOf(7, rawLines.size)) {
            val cleaned = normalize(upperLines[i])
            val m = modelPattern.matcher(cleaned)
            while (m.find()) {
                val candidate = m.group(0)
                if (isCleanModel(candidate)) return candidate
            }
        }

        // === ПРИОРИТЕТ 6: Любой паттерн в тексте, самый длинный ===
        val allText = rawLines.joinToString(" ").uppercase()
        val m = modelPattern.matcher(allText)
        var best = ""
        while (m.find()) {
            val candidate = m.group(0)
            if (isCleanModel(candidate) && candidate.length > best.length) best = candidate
        }
        if (best.isNotEmpty()) return best

        return ""
    }
}
