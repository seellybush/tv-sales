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

        // === 🚫 СТРОКИ-ШУМ ===
        fun isNoise(line: String): Boolean {
            val u = line.uppercase()
            return u.contains("S/N") || u.contains("S/NO") ||
                   u.contains("SERIAL") || u.contains("W/O") ||
                   u.contains("VOLTAGE") || u.contains("WEIGHT") ||
                   u.contains("DIMENSIONS") || u.contains("DATE") ||
                   u.contains("ТЕЛЕВИЗОР") || u.contains("ТЕЛЕДИДАР") ||
                   u.contains("ИЗГОТОВИТЕЛЬ") || u.contains("ИМПОРТЕР") ||
                   u.contains("ПРОИЗВОДИТЕЛЬ") || u.contains("СДЕЛАНО") ||
                   u.contains("АДРЕС") || u.contains("ТЕЛ.") ||
                   u.contains("НАПРЯЖЕНИЕ") || u.contains("ПИТАНИЕ") ||
                   u.contains("ГАРАНТИЙНЫЙ") || u.contains("СЕРВИСНЫЙ") ||
                   u.contains("КЛАСС") || u.contains("ЭНЕРГО") ||
                   u.contains("СООТВЕТСТВИЕ") || u.contains("СЕРТИФИКАТ") ||
                   u.contains("ДЕКЛАРАЦИЯ") || u.contains("СВИДЕТЕЛЬСТВО")
        }

        // === 🚫 СЕРИЙНИК / ТУ ===
        fun isSerial(line: String): Boolean {
            val u = line.uppercase().replace(" ", "")
            // ТУ / TY
            if (u.startsWith("TYBY") || u.startsWith("TY") || u.startsWith("ТУ")) return true
            // 8+ цифр подряд
            if (Regex("\\d{8,}").containsMatchIn(u)) return true
            // Длина 15+ с 6+ цифрами
            if (u.length >= 15 && u.count { it.isDigit() } >= 6) return true
            // Буквы + 6+ цифр
            if (Regex("[A-Z]{2,}\\d{6,}").containsMatchIn(u)) return true
            // LG-стиль: буквы+цифры без чёткой диагонали в начале
            // Например: 609RADC65786 — начинается с 60, но 60 — не диагональ LG
            return false
        }

        // === 🔧 Исправление OCR ===
        fun fixOcr(s: String): String {
            var r = s.uppercase().replace(" ", "").substringBefore(".")
            // 550NED → 55QNED
            val m = Regex("^(\\d{2})(0)([A-Z].*)$").find(r)
            if (m != null) r = m.groupValues[1] + "Q" + m.groupValues[3]
            return r
        }

        // === ✅ ПРОВЕРКА МОДЕЛИ ===
        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            // Длина 4–15
            if (upper.length < 4 || upper.length > 15) return false
            // Буквы и цифры
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false
            // Начинается с диагонали 24–98
            val diag = Regex("^(\\d{2,3})").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: return false
            if (diag < 24 || diag > 98) return false
            // Нет 4+ цифр подряд
            if (Regex("\\d{4,}").containsMatchIn(upper)) return false
            // Нет X/N между цифрами
            if (Regex("\\d[XxNn]\\d").containsMatchIn(upper)) return false
            // Не заканчивается на MM/KG/HZ/V
            if (upper.endsWith("MM") || upper.endsWith("KG") ||
                upper.endsWith("HZ") || upper.endsWith("V")) return false
            return true
        }

        // === 🎯 СБОР КАНДИДАТОВ С ПРИОРИТЕТОМ ===
        data class Candidate(val value: String, val priority: Int)
        val candidates = mutableListOf<Candidate>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (isNoise(line)) continue
            if (isSerial(line)) continue

            val matches = Regex("\\b(2[4-9]|[3-8]\\d|9[0-8])([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})\\b")
                .findAll(fixOcr(line))

            for (m in matches) {
                val candidate = m.value
                if (!isCleanModel(candidate)) continue

                val prevLine = if (i > 0) rawLines[i - 1].uppercase() else ""
                val currUpper = line.uppercase()

                var priority = 5
                if (currUpper.contains("QUANTUM") && candidate.length in 5..10) priority = 1
                else if (prevLine.contains("QUANTUM")) priority = 1
                else if (currUpper.contains("MODEL") || prevLine.contains("MODEL") ||
                         currUpper.contains("МОДЕЛЬ") || prevLine.contains("МОДЕЛЬ")) priority = 2
                else if (prevLine.contains("W/O")) priority = 3
                else if (i < 5) priority = 4

                if (candidate.length in 5..10) priority -= 1

                candidates.add(Candidate(candidate, priority))
            }
        }

        return candidates
            .sortedWith(compareBy({ it.priority }, { it.value.length }))
            .firstOrNull()?.value ?: ""
    }
}
