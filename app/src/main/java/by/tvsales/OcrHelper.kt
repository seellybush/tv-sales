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

    /**
     * Умное извлечение модели телевизора из распознанного текста.
     * Алгоритм:
     * 1. Убираем мусорные строки (VOLTAGE, WEIGHT, ART, S/NO, W/O и т.д.)
     * 2. Приоритет 1: строка сразу ПОСЛЕ слова "MODEL"
     * 3. Приоритет 2: строка сразу ПОСЛЕ бренда (TCL, LG, Hisense, Samsung, Quantum)
     * 4. Приоритет 3: первые 5 строк с паттерном модели
     * 5. Приоритет 4: любой паттерн модели в тексте
     */
    fun extractModel(text: String): String {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        // Чёрный список слов-мусора
        val blacklist = listOf(
            "VOLTAGE", "WEIGHT", "DIMENSIONS", "ART", "S/NO", "W/O", "DATE",
            "SERIAL", "MODEL", "SERIES", "NET", "GROSS", "NET/GROSS",
            "КГ", "ММ", "ВТ", "HZ", "MM", "KG", "SERIES", "СЕРИЯ",
            "СДЕЛАНО", "ИЗГОТОВИТЕЛЬ", "ПРОИЗВОДИТЕЛЬ", "ИМПОРТЕР",
            "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ", "АДРЕС", "ТЕЛ", "EAC", "TPB",
            "HDMI", "USB", "HDCP", "WEBOS", "TIZEN", "ANDROID",
            "СМАРТ", "СМАРТ", "ТЕЛЕВИЗОР", "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР"
        )

        // Паттерн модели: [диагональ][буквы][цифры/буквы]
        // Примеры: 55P79L, 55E7QRU, 55QNED72B6B, 32S59K, 75MQLED70K, 24H6BQ
        val modelPattern = Pattern.compile(
            "(?<![A-Z0-9])(\\d{2})([A-Z]{0,5}\\d{0,5}[A-Z0-9]{0,10})(?![A-Z0-9])"
        )

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase()
            if (upper.length < 4 || upper.length > 25) return false
            if (blacklist.any { upper.contains(it) }) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false
            return true
        }

        fun cleanToModel(raw: String): String {
            // Убираем точки и суффиксы типа .ABYGLJU
            var cleaned = raw.trim().uppercase()
                .replace(" ", "")
                .replace(".", "")
                .replace("-", "")
            // Отсекаем всё после последнего разумного блока (модели обычно до 15 символов)
            if (cleaned.length > 15) {
                // Пытаемся взять начало до 15 символов, но с проверкой паттерна
                val m = modelPattern.matcher(cleaned)
                if (m.find()) cleaned = m.group(0)
            }
            return cleaned
        }

        // === ПРИОРИТЕТ 1: строка после слова MODEL ===
        for (i in lines.indices) {
            if (lines[i].uppercase().trim() == "MODEL" || lines[i].uppercase().startsWith("MODEL")) {
                if (i + 1 < lines.size) {
                    val candidate = cleanToModel(lines[i + 1])
                    if (isCleanModel(candidate)) return candidate
                }
                // Если "MODEL" и модель на одной строке
                val afterModel = lines[i].uppercase().replace("MODEL", "").trim()
                if (afterModel.isNotEmpty()) {
                    val candidate = cleanToModel(afterModel)
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === ПРИОРИТЕТ 2: строка после бренда ===
        val brands = listOf("TCL", "LG", "SAMSUNG", "HISENSE", "QUANTUM", "SONY", "PHILIPS", "XIAOMI")
        for (i in lines.indices) {
            val upperLine = lines[i].uppercase()
            if (brands.any { upperLine == it || upperLine.startsWith("$it ") }) {
                if (i + 1 < lines.size) {
                    val candidate = cleanToModel(lines[i + 1])
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === ПРИОРИТЕТ 3: первые 5 строк с паттерном ===
        for (i in 0 until minOf(5, lines.size)) {
            val m = modelPattern.matcher(lines[i].uppercase())
            while (m.find()) {
                val candidate = m.group(0)
                if (isCleanModel(candidate)) return candidate
            }
        }

        // === ПРИОРИТЕТ 4: любой паттерн в тексте ===
        val allText = lines.joinToString(" ").uppercase()
        val m = modelPattern.matcher(allText)
        while (m.find()) {
            val candidate = m.group(0)
            if (isCleanModel(candidate)) return candidate
        }

        return ""
    }
}
