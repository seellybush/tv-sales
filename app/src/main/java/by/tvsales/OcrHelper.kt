package by.tvsales

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.ImageInput
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

        // === 🚫 СТРОКИ-ШУМ ===
        fun isNoiseLine(line: String): Boolean {
            val u = line.uppercase()
            return u.contains("S/NO") || u.contains("S/N") ||
                   u.contains("SERIAL") || u.contains("W/O") ||
                   u.contains("VOLTAGE") || u.contains("WEIGHT") ||
                   u.contains("DIMENSIONS") || u.contains("DATE") ||
                   u.contains("КГ") || u.contains("ММ") || u.contains("ВТ") ||
                   u.contains("ИЗГОТОВИТЕЛЬ") || u.contains("ИМПОРТЕР") ||
                   u.contains("ПРОИЗВОДИТЕЛЬ") || u.contains("СДЕЛАНО") ||
                   u.contains("АДРЕС") || u.contains("ТЕЛ.") ||
                   u.contains("ТЕЛЕВИЗОР") || u.contains("ТЕЛЕДИДАР") ||
                   u.contains("НАПРЯЖЕНИЕ") || u.contains("ПИТАНИЕ") ||
                   u.contains("ГАРАНТИЙНЫЙ") || u.contains("СЕРВИСНЫЙ") ||
                   u.contains("ТУ BY") || u.contains("ТУBY") ||
                   u.contains("TYBY") || u.contains("TY BY") ||
                   u.contains("СВИДЕТЕЛЬСТВО") || u.contains("ДЕКЛАРАЦИЯ")
        }

        // === 🚫 ЯВНЫЙ S/N ===
        // Например: Quantum202670010652, 609RADC65786, 3TE55G25361JRU71VT10330
        fun isSerialNumber(line: String): Boolean {
            val u = line.uppercase().replace(" ", "")
            // S/N обычно: буквы + 8+ цифр подряд, или 10+ символов без пробелов
            if (Regex("[A-Z]{2,}\\d{8,}").containsMatchIn(u)) return true
            if (Regex("\\d{8,}").containsMatchIn(u)) return true
            if (u.length >= 15 && u.count { it.isDigit() } >= 8) return true
            return false
        }

        // === 🔧 ИСПРАВЛЕНИЕ OCR-ОШИБОК ===
        fun fixOcrMistakes(s: String): String {
            var result = s.uppercase().replace(" ", "")
            // 550NED → 55QNED (OCR путает Q с 0)
            val pattern = Regex("^(\\d{2})(0)([A-Z].*)$")
            val match = pattern.find(result)
            if (match != null) {
                result = match.groupValues[1] + "Q" + match.groupValues[3]
            }
            return result
        }

        // === ✅ ПРОВЕРКА МОДЕЛИ ===
        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            if (upper.length < 4 || upper.length > 20) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false

            // Начинается с диагонали 24-98
            val diagMatch = Regex("^(\\d{2,3})").find(upper) ?: return false
            val diag = diagMatch.groupValues[1].toIntOrNull() ?: return false
            if (diag < 24 || diag > 98) return false

            // Нет 4+ цифр подряд
            if (Regex("\\d{4,}").containsMatchIn(upper)) return false

            // Нет X/N между цифрами (это разрешение)
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false

            return true
        }

        // === 🎯 ПРИОРИТЕТ 1: QUANTUM ===
        // Ищем строку, где "Quantum" отделено пробелом от модели (короткого кода)
        for (line in rawLines) {
            if (isNoiseLine(line)) continue
            if (isSerialNumber(line)) continue

            // Строка типа "Quantum 24H6BQ" — есть пробел после Quantum
            val match = Regex("(?i)quantum\\s+([0-9]{2}[A-Z0-9]{2,10})").find(line)
            if (match != null) {
                val candidate = fixOcrMistakes(match.groupValues[1])
                if (isCleanModel(candidate)) return candidate
            }
        }
        // Если не нашли с пробелом — ищем "Quantum24H6BQ" (без пробела, но с коротким кодом)
        for (line in rawLines) {
            if (isNoiseLine(line)) continue
            if (isSerialNumber(line)) continue
            val match = Regex("(?i)quantum([0-9]{2}[A-Z][A-Z0-9]{2,8})").find(line)
            if (match != null) {
                val candidate = fixOcrMistakes(match.groupValues[1])
                if (isCleanModel(candidate)) return candidate
            }
        }

        // === 🎯 ПРИОРИТЕТ 2: LG — модель после W/O или на отдельной строке ===
        val isLG = rawLines.any { it.contains("LG", ignoreCase = true) ||
                                  it.contains("S/NO", ignoreCase = true) }
        if (isLG) {
            // После W/O
            for (i in rawLines.indices) {
                if (rawLines[i].uppercase().contains("W/O")) {
                    if (i + 1 < rawLines.size) {
                        val candidate = fixOcrMistakes(
                            rawLines[i + 1].replace(" ", "").substringBefore(".")
                        )
                        if (isCleanModel(candidate)) return candidate
                    }
                }
            }
            // Или строка с паттерном LG (без S/N)
            for (line in rawLines) {
                if (isNoiseLine(line)) continue
                if (isSerialNumber(line)) continue
                val cleaned = fixOcrMistakes(line.substringBefore("."))
                val m = Regex("(\\d{2,3})([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})").find(cleaned)
                if (m != null && isCleanModel(m.value)) return m.value
            }
        }

        // === 🎯 ПРИОРИТЕТ 3: Строка после MODEL/МОДЕЛЬ ===
        for (i in rawLines.indices) {
            val u = rawLines[i].uppercase()
            if (u.contains("MODEL") || u.contains("МОДЕЛЬ")) {
                val afterModel = u.replace("MODEL", "").replace("МОДЕЛЬ", "")
                    .replace(":", "").trim()
                if (afterModel.length >= 4) {
                    val candidate = fixOcrMistakes(afterModel)
                    if (isCleanModel(candidate)) return candidate
                }
                if (i + 1 < rawLines.size) {
                    val candidate = fixOcrMistakes(rawLines[i + 1].substringBefore("."))
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === 🎯 ПРИОРИТЕТ 4: Первые 7 строк с паттерном модели ===
        for (i in 0 until minOf(7, rawLines.size)) {
            if (isNoiseLine(rawLines[i])) continue
            if (isSerialNumber(rawLines[i])) continue
            val cleaned = fixOcrMistakes(rawLines[i])
            for (m in Regex("(\\d{2,3})([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})").findAll(cleaned)) {
                if (isCleanModel(m.value)) return m.value
            }
        }

        // === 🎯 ПРИОРИТЕТ 5: Любой паттерн в тексте ===
        val allText = rawLines
            .filter { !isNoiseLine(it) && !isSerialNumber(it) }
            .joinToString(" ")
            .uppercase()
        var best = ""
        for (m in Regex("(\\d{2,3})([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})").findAll(allText)) {
            val candidate = fixOcrMistakes(m.value)
            if (isCleanModel(candidate) && candidate.length > best.length) best = candidate
        }
        return best
    }
}
