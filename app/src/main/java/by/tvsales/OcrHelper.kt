package by.tvsales

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrHelper {

    private val validDiagonals = setOf(
        24, 28, 32, 39, 40, 42, 43, 48, 50, 55, 58, 60, 65, 70, 75, 77, 83, 85, 98, 100, 115
    )

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

        // Шум (БЕЗ Quantum — он нужен как якорь)
        val noise = listOf(
            "S/N", "S/NO", "SERIAL", "W/O", "VOLTAGE", "WEIGHT", "DIMENSIONS",
            "ТУ BY", "TYBY", "ТУBY", "ИЗГОТОВИТЕЛЬ", "ИМПОРТЕР", "СДЕЛАНО",
            "АДРЕС", "НАПРЯЖЕНИЕ", "ПИТАНИЕ", "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ",
            "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР", "EAC", "HDMI", "USB", "WEBOS", "TIZEN",
            "ANDROID", "GOOGLE", "SMART"
        )

        fun isNoise(line: String): Boolean {
            val u = line.uppercase()
            return noise.any { u.contains(it) }
        }

        fun isSerial(line: String): Boolean {
            val u = line.uppercase().replace(" ", "")
            if (Regex("\\d{8,}").containsMatchIn(u)) return true
            if (u.startsWith("TY") || u.startsWith("ТУ")) return true
            if (u.length >= 15 && u.count { it.isDigit() } >= 6) return true
            if (Regex("[A-Z]{2,}\\d{6,}").containsMatchIn(u)) return true
            return false
        }

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            if (upper.length < 4 || upper.length > 15) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false

            // Диагональ реальная
            val diag = Regex("^(\\d{2,3})").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: return false
            if (diag !in validDiagonals) return false

            if (Regex("\\d{4,}").containsMatchIn(upper)) return false
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false
            if (upper.endsWith("MM") || upper.endsWith("KG") ||
                upper.endsWith("HZ") || upper.endsWith("V")) return false
            return true
        }

        fun fixOcr(s: String): String {
            var r = s.uppercase().replace(" ", "").substringBefore(".")
            val m = Regex("^(\\d{2})(0)([A-Z].*)$").find(r)
            if (m != null) r = m.groupValues[1] + "Q" + m.groupValues[3]
            return r
        }

        // === ПРИОРИТЕТ 1: QUANTUM ===
        // Ищем строку, где "Quantum" + пробел + код (например, "Quantum 65U6BQ")
        for (line in rawLines) {
            if (isSerial(line)) continue
            val match = Regex("(?i)quantum\\s+([0-9]{2}[A-Z0-9]{2,10})").find(line)
            if (match != null) {
                val candidate = fixOcr(match.groupValues[1])
                if (isCleanModel(candidate)) return candidate
            }
        }
        // Fallback: "Quantum65U6BQ" без пробела, но с коротким кодом
        for (line in rawLines) {
            if (isSerial(line)) continue
            val match = Regex("(?i)quantum([0-9]{2}[A-Z][A-Z0-9]{2,8})").find(line)
            if (match != null) {
                val candidate = fixOcr(match.groupValues[1])
                if (isCleanModel(candidate)) return candidate
            }
        }

        // === ПРИОРИТЕТ 2: LG ===
        val isLG = rawLines.any { it.contains("LG", ignoreCase = true) ||
                                  it.contains("S/NO", ignoreCase = true) }
        if (isLG) {
            for (i in rawLines.indices) {
                if (rawLines[i].uppercase().contains("W/O")) {
                    if (i + 1 < rawLines.size) {
                        val candidate = fixOcr(rawLines[i + 1].replace(" ", "").substringBefore("."))
                        if (isCleanModel(candidate)) return candidate
                    }
                }
            }
            for (line in rawLines) {
                if (isNoise(line)) continue
                if (isSerial(line)) continue
                val cleaned = fixOcr(line.substringBefore("."))
                val m = Regex("(\\d{2})([A-Z]{1,8}\\d{0,5}[A-Z0-9]{0,8})").find(cleaned)
                if (m != null && isCleanModel(m.value)) return m.value
            }
        }

        // === ПРИОРИТЕТ 3: Строка после MODEL/МОДЕЛЬ ===
        for (i in rawLines.indices) {
            val u = rawLines[i].uppercase()
            if (u.contains("MODEL") || u.contains("МОДЕЛЬ")) {
                val after = u.replace("MODEL", "").replace("МОДЕЛЬ", "").replace(":", "").trim()
                if (after.length >= 4) {
                    val c = fixOcr(after)
                    if (isCleanModel(c)) return c
                }
                if (i + 1 < rawLines.size) {
                    val c = fixOcr(rawLines[i + 1].substringBefore("."))
                    if (isCleanModel(c)) return c
                }
            }
        }

        // === ПРИОРИТЕТ 4: Первые 7 строк с паттерном модели ===
        for (i in 0 until minOf(7, rawLines.size)) {
            if (isNoise(rawLines[i])) continue
            if (isSerial(rawLines[i])) continue
            val cleaned = fixOcr(rawLines[i])
            val matches = Regex("(2[48]|3[29]|4[0238]|50|5[058]|60|65|70|75|77|83|85|98)([A-Z]{1,8}\\d{0,5}[A-Z0-9]{0,8})")
                .findAll(cleaned)
            for (m in matches) {
                if (isCleanModel(m.value)) return m.value
            }
        }

        // === ПРИОРИТЕТ 5: Любой паттерн, самый длинный ===
        val allText = rawLines
            .filter { !isNoise(it) && !isSerial(it) }
            .joinToString(" ")
            .uppercase()
        var best = ""
        for (m in Regex("(2[48]|3[29]|4[0238]|50|5[058]|60|65|70|75|77|83|85|98)([A-Z]{1,8}\\d{0,5}[A-Z0-9]{0,8})")
            .findAll(allText)) {
            val c = fixOcr(m.value)
            if (isCleanModel(c) && c.length > best.length) best = c
        }
        return best
    }
}
