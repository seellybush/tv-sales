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

        val isLG = upperLines.any { it.contains("LG") || it.contains("ABYGLJU") || it.contains("WEBOS") }
        val isQuantum = upperLines.any { it.contains("QUANTUM") }
        val isSamsung = upperLines.any { it.contains("SAMSUNG") || it.contains("TIZEN") }
        val isHisense = upperLines.any { it.contains("HISENSE") || it.contains("VIDAA") }

        // 🚫 ЖЁСТКИЙ ЧЁРНЫЙ СПИСОК (игнорируем эти строки полностью)
        val blacklist = listOf(
            "S/N", "SERIAL", "W/O", "DATE", "VOLTAGE", "WEIGHT", "DIMENSIONS",
            "КГ", "ММ", "ВТ", "HZ", "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ", "АДРЕС",
            "ТЕЛ", "EAC", "TPB", "HDMI", "USB", "HDCP", "WEBOS", "TIZEN",
            "ANDROID", "СМАРТ", "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР", "ИЗГОТОВИТЕЛЬ",
            "ПРОИЗВОДИТЕЛЬ", "ИМПОРТЕР", "СДЕЛАНО", "РОССИЯ", "БЕЛАРУСЬ",
            "ПРОИЗВОДСТВЕННАЯ", "ПЛОЩАДКА", "ООО", "ГРУППА", "КОМПАНИЙ",
            "ИНФОРМАЦИОННЫЙ", "ЦЕНТР", "БЕСПЛАТНЫЙ", "ЗВОНОК", "СВЯЗЬ",
            "РАЗРЕШЕНИЕ", "ДИАГОНАЛЬ", "ПИТАНИЕ", "НАПРЯЖЕНИЕ",
            "ЭКРАН", "ТИП", "МОЩНОСТЬ", "ВЕС", "ГАБАРИТ", "СТРАНА",
            "СЕРТИФИКАТ", "СТАНДАРТ", "ПАМЯТЬ", "ПРОЦЕССОР", "ЧАСТОТА"
        )

        // Запрещённые паттерны (разрешения, напряжения, стандарты)
        val forbiddenPatterns = listOf(
            Regex("\\d{3,4}[Xx]\\d{3,4}"),         // 3840x2160, 1920x1080
            Regex("\\b\\d{3,4}P\\b"),               // 1080P, 720P
            Regex("\\b(1080|720|2160|3840|4096|8K|4K|2K|UHD|FHD|HD|SD)\\b"),
            Regex("\\b(220|240|110|120)[-–]?\\d{2,3}V\\b"), // 220-240V
            Regex("\\b\\d{2,3}\\s?HZ\\b"),          // 50HZ, 60HZ
            Regex("\\b\\d{3,4}[Xx]\\d{3,4}[A-Z]{0,3}\\b"),
            Regex("^\\d{4,}$")                       // только цифры 4+
        )

        // Модель: начинается с 2-3 цифр (24-115), затем буквы/цифры
        // НЕ содержит X между группами цифр
        val modelPattern = Pattern.compile(
            "\\b(2[4-9]|[3-9]\\d|1[01]\\d)([A-Z]{1,6}\\d{0,5}[A-Z0-9]{0,8})\\b"
        )

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            if (upper.length < 4 || upper.length > 20) return false
            if (blacklist.any { upper.contains(it) }) return false
            if (forbiddenPatterns.any { it.containsMatchIn(upper) }) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false

            // Диагональ должна быть 24-115
            val diagMatch = Regex("^(\\d{2,3})").find(upper)
            val diag = diagMatch?.groupValues?.get(1)?.toIntOrNull() ?: return false
            if (diag < 24 || diag > 115) return false

            // Не должно быть X между цифрами
            if (Regex("\\d+[Xx]\\d+").containsMatchIn(upper)) return false

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

        // === ПРИОРИТЕТ 1: QUANTUM ===
        if (isQuantum) {
            for (i in rawLines.indices) {
                if (rawLines[i].contains("Quantum", ignoreCase = true)) {
                    val sameLine = rawLines[i].replace("Quantum", "", ignoreCase = true)
                        .replace("Квантум", "", ignoreCase = true).trim()
                    val candidate = normalize(sameLine)
                    if (isCleanModel(candidate)) return candidate

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
        }

        // === ПРИОРИТЕТ 2: LG ===
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

        // === ПРИОРИТЕТ 3: Строка после MODEL ===
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
            "EVO", "H", "MIDEA", "BQ", "TELEFUNKEN", "TOSHIBA", "PANASONIC"
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

        // === ПРИОРИТЕТ 6: Любой паттерн в тексте ===
        val allText = rawLines.joinToString(" ").uppercase()
        val m = modelPattern.matcher(allText)
        var bestCandidate = ""
        while (m.find()) {
            val candidate = m.group(0)
            if (isCleanModel(candidate)) {
                if (candidate.length > bestCandidate.length) bestCandidate = candidate
            }
        }
        if (bestCandidate.isNotEmpty()) return bestCandidate

        return ""
    }
}
