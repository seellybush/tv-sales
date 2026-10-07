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

        // Строки-шум (S/N, ТУ, напряжение и т.д.)
        val noise = listOf(
            "S/N", "S/NO", "SERIAL", "W/O", "VOLTAGE", "WEIGHT", "DIMENSIONS",
            "ТУ BY", "TYBY", "ТУBY", "ИЗГОТОВИТЕЛЬ", "ИМПОРТЕР", "СДЕЛАНО",
            "АДРЕС", "НАПРЯЖЕНИЕ", "ПИТАНИЕ", "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ",
            "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР", "EAC", "HDMI", "USB", "WEBOS", "TIZEN"
        )

        fun isNoise(line: String): Boolean {
            val u = line.uppercase()
            return noise.any { u.contains(it) }
        }

        fun isSerial(line: String): Boolean {
            val u = line.uppercase().replace(" ", "")
            // 8+ цифр подряд — серийник
            if (Regex("\\d{8,}").containsMatchIn(u)) return true
            // Начинается с TY/TУ — это ТУ
            if (u.startsWith("TY") || u.startsWith("ТУ")) return true
            // 15+ символов с 6+ цифрами
            if (u.length >= 15 && u.count { it.isDigit() } >= 6) return true
            // Буквы + 6+ цифр
            if (Regex("[A-Z]{2,}\\d{6,}").containsMatchIn(u)) return true
            return false
        }

        // Модель: 2 цифры (24-98) + буквы/цифры, длина 4-15, без 4+ цифр подряд
        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            if (upper.length < 4 || upper.length > 15) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false
            val diag = Regex("^(\\d{2,3})").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: return false
            if (diag < 24 || diag > 98) return false
            if (Regex("\\d{4,}").containsMatchIn(upper)) return false
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false
            if (upper.endsWith("MM") || upper.endsWith("KG") ||
                upper.endsWith("HZ") || upper.endsWith("V")) return false
            return true
        }

        // Исправление OCR: 550NED → 55QNED
        fun fixOcr(s: String): String {
            var r = s.uppercase().replace(" ", "").substringBefore(".")
            val m = Regex("^(\\d{2})(0)([A-Z].*)$").find(r)
            if (m != null) r = m.groupValues[1] + "Q" + m.groupValues[3]
            return r
        }

        data class Candidate(val value: String, val priority: Int)
        val candidates = mutableListOf<Candidate>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (isNoise(line)) continue
            if (isSerial(line)) continue

            val matches = Regex("\\b(2[4-9]|[3-8]\\d|9[0-8])([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})\\b")
                .findAll(fixOcr(line))

            for (m in matches) {
                val c = m.value
                if (!isCleanModel(c)) continue

                val prev = if (i > 0) rawLines[i - 1].uppercase() else ""
                val curr = line.uppercase()

                var priority = 5
                if (curr.contains("QUANTUM") && c.length in 5..10) priority = 1
                else if (prev.contains("QUANTUM")) priority = 1
                else if (curr.contains("MODEL") || prev.contains("MODEL") ||
                         curr.contains("МОДЕЛЬ") || prev.contains("МОДЕЛЬ")) priority = 2
                else if (prev.contains("W/O")) priority = 3
                else if (i < 5) priority = 4

                if (c.length in 5..10) priority -= 1
                candidates.add(Candidate(c, priority))
            }
        }

        return candidates.sortedWith(compareBy({ it.priority }, { it.value.length }))
            .firstOrNull()?.value ?: ""
    }
}
