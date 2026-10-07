package by.tvsales

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
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

        // === 🚫 ЗАПРЕЩЁННЫЕ СЛОВА (строка точно не модель) ===
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
            "ULTRA", "FULL", "PIX", "ПИКС", "SMART", "TV",
            "ТУ BY", "ТУBY", "TYBY", "ТУ", "TY", "TU",
            "СВИДЕТЕЛЬСТВО", "ДЕКЛАРАЦИЯ", "СООТВЕТСТВИЕ"
        )

        // === 🔒 ЗАПРЕЩЁННЫЕ ПАТТЕРНЫ (разрешения, напряжения) ===
        val forbiddenPatterns = listOf(
            Regex("\\d{3,4}[XxNn]\\d{3,4}"),        // 3840x2160, 3840N2160
            Regex("\\b\\d{3,4}[Pp]\\b"),              // 1080P
            Regex("\\b(1080|720|2160|3840|4096|8K|4K|2K|UHD|FHD|HD|SD)\\b"),
            Regex("\\b(220|240|110|120)[-–]?\\d{2,3}V\\b"),
            Regex("\\b\\d{2,3}\\s?HZ\\b"),
            Regex("^\\d{4,}$"),                       // только цифры 4+
            Regex("^[A-Z]{2,5}\\d{8,}$"),             // длинные буквенно-цифровые (ТУ, S/N)
            Regex("\\d{8,}")                          // 8+ цифр подряд
        )

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()

            // 1. Длина: 4–15
            if (upper.length < 4 || upper.length > 15) return false

            // 2. Запрещённые слова
            if (forbiddenWords.any { upper.contains(it) }) return false

            // 3. Запрещённые паттерны
            if (forbiddenPatterns.any { it.containsMatchIn(upper) }) return false

            // 4. Должны быть и буквы, и цифры
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false

            // 5. ❗ МОДЕЛЬ НАЧИНАЕТСЯ С ЦИФР (диагональ 24–98)
            val diagMatch = Regex("^(\\d{2,3})").find(upper) ?: return false
            val diag = diagMatch.groupValues[1].toIntOrNull() ?: return false
            if (diag < 24 || diag > 98) return false

            // 6. Не должно быть 4+ цифр подряд (ТУ, S/N, разрешение)
            if (Regex("\\d{4,}").containsMatchIn(upper)) return false

            // 7. Не должно быть букв X или N между цифрами (это разрешение)
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false

            // 8. Не заканчивается на MM / KG / V / HZ / W
            if (upper.endsWith("MM") || upper.endsWith("KG") ||
                upper.endsWith("HZ") || upper.endsWith("V")) return false

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

        // Паттерн модели: 2 цифры (24-98) + 1-6 букв + цифры/буквы
        val modelPattern = Regex("\\b(2[4-9]|[3-8]\\d|9[0-8])([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})\\b")

        // === ПРИОРИТЕТ 1: QUANTUM — модель после слова ===
        for (i in rawLines.indices) {
            if (rawLines[i].contains("Quantum", ignoreCase = true)) {
                // Пробуем взять часть строки после "Quantum"
                val afterQuantum = rawLines[i].replace(Regex("(?i)quantum"), "").trim()
                val candidate = normalize(afterQuantum)
                if (isCleanModel(candidate)) return candidate

                // Или соседние строки
                if (i + 1 < rawLines.size) {
                    val next = normalize(rawLines[i + 1])
                    if (isCleanModel(next)) return next
                }
                if (i - 1 >= 0) {
                    val prev = normalize(rawLines[i - 1])
                    if (isCleanModel(prev)) return prev
                }
            }
        }

        // === ПРИОРИТЕТ 2: LG — только строки без S/N ===
        val isLG = upperLines.any { it.contains("LG") || it.contains("WEBOS") }
        if (isLG) {
            for (line in rawLines) {
                val upper = line.uppercase()
                if (upper.contains("S/N") || upper.contains("SERIAL") || upper.contains("W/O")) continue
                if (upper.contains("609") || upper.contains("RAD")) continue // типичный S/N LG

                val cleaned = normalize(upper)
                val match = modelPattern.find(cleaned)
                if (match != null) {
                    val candidate = match.value
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === ПРИОРИТЕТ 3: Строка после MODEL/МОДЕЛЬ ===
        for (i in rawLines.indices) {
            val upper = upperLines[i]
            if (upper == "MODEL" || upper.startsWith("MODEL ") ||
                upper == "МОДЕЛЬ" || upper.startsWith("МОДЕЛЬ ")) {

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
            "EVO", "MIDEA", "TOSHIBA", "PANASONIC"
        )
        for (i in rawLines.indices) {
            if (brands.any { upperLines[i] == it || upperLines[i].startsWith("$it ") }) {
                if (i + 1 < rawLines.size) {
                    val next = normalize(rawLines[i + 1])
                    if (isCleanModel(next)) return next
                }
            }
        }

        // === ПРИОРИТЕТ 5: Первые 7 строк с паттерном ===
        for (i in 0 until minOf(7, rawLines.size)) {
            val cleaned = normalize(upperLines[i])
            val matches = modelPattern.findAll(cleaned)
            for (m in matches) {
                if (isCleanModel(m.value)) return m.value
            }
        }

        // === ПРИОРИТЕТ 6: Любой паттерн в тексте, самый длинный ===
        val allText = rawLines.joinToString(" ").uppercase()
        var best = ""
        for (m in modelPattern.findAll(allText)) {
            if (isCleanModel(m.value) && m.value.length > best.length) best = m.value
        }
        if (best.isNotEmpty()) return best

        return ""
    }
}
