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
     * Умное извлечение модели телевизора.
     * Учитывает специфику этикеток LG, Quantum, Samsung, TCL, Hisense.
     */
    fun extractModel(text: String): String {
        val rawLines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val upperLines = rawLines.map { it.uppercase() }

        // === ШАГ 1: Ищем бренд, чтобы понять логику ===
        val isLG = upperLines.any { it.contains("LG") || it.contains("S/N : 609") || it.contains("ABYGLJU") }
        val isQuantum = upperLines.any { it.contains("QUANTUM") }
        val isSamsung = upperLines.any { it.contains("SAMSUNG") || it.contains("S/N:") }
        val isTCL = upperLines.any { it.contains("TCL") }
        val isHisense = upperLines.any { it.contains("HISENSE") }

        // === ШАГ 2: ЖЕСТКИЙ ЧЁРНЫЙ СПИСОК (игнорируем эти строки) ===
        val blacklist = listOf(
            "S/N", "SERIAL", "W/O", "DATE", "VOLTAGE", "WEIGHT", "DIMENSIONS",
            "КГ", "ММ", "ВТ", "HZ", "ГАРАНТИЙНЫЙ", "СЕРВИСНЫЙ", "АДРЕС",
            "ТЕЛ", "EAC", "TPB", "HDMI", "USB", "HDCP", "WEBOS", "TIZEN",
            "ANDROID", "СМАРТ", "ТЕЛЕВИЗОР", "ТЕЛЕДИДАР", "ИЗГОТОВИТЕЛЬ",
            "ПРОИЗВОДИТЕЛЬ", "ИМПОРТЕР", "СДЕЛАНО", "РОССИЯ", "БЕЛАРУСЬ",
            "ПРОИЗВОДСТВЕННАЯ", "ПЛОЩАДКА", "ООО", "ГРУППА", "КОМПАНИЙ",
            "ИНФОРМАЦИОННЫЙ", "ЦЕНТР", "БЕСПЛАТНЫЙ", "ЗВОНОК", "СВЯЗЬ"
        )

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase()
            if (upper.length < 4 || upper.length > 25) return false
            if (blacklist.any { upper.contains(it) }) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false
            // Отсекаем длинные строки, похожие на S/N (15+ символов с хаотичным набором)
            if (upper.length > 15 && upper.count { it.isDigit() } > 10) return false
            return true
        }

        // === ШАГ 3: ОСОБЫЙ ПОИСК ДЛЯ QUANTUM ===
        if (isQuantum) {
            for (i in rawLines.indices) {
                if (rawLines[i].contains("Quantum", ignoreCase = true)) {
                    // Модель может быть на этой же строке или на следующей
                    val sameLine = rawLines[i].replace("Quantum", "", ignoreCase = true).trim()
                    if (sameLine.isNotEmpty() && isCleanModel(sameLine)) return sameLine.uppercase()
                    if (i + 1 < rawLines.size && isCleanModel(rawLines[i + 1])) return rawLines[i + 1].uppercase()
                }
            }
            // Если не нашли рядом с Quantum, ищем паттерн 24H6BQ
            val quantumPattern = Pattern.compile("(\\d{2}[A-Z]\\d[A-Z]{2})")
            val m = quantumPattern.matcher(text.uppercase().replace(" ", ""))
            if (m.find()) return m.group(0)
        }

        // === ШАГ 4: ОСОБЫЙ ПОИСК ДЛЯ LG ===
        if (isLG) {
            // LG: модель обычно 55QNED72B6B или 55UR91006LA
            // S/N выглядит как 609RADC65786 — игнорируем длинные строки без букв перед цифрами
            for (line in rawLines) {
                val upper = line.uppercase()
                // Пропускаем S/N
                if (upper.contains("S/N") || upper.contains("SERIAL")) continue
                // Ищем паттерн: цифры + буквы + цифры/буквы
                val lgPattern = Pattern.compile("(\\d{2})([A-Z]{2,5}\\d{0,5}[A-Z0-9]{0,5})")
                val matcher = lgPattern.matcher(upper.replace(" ", ""))
                if (matcher.find()) {
                    val candidate = matcher.group(0)
                    if (isCleanModel(candidate)) return candidate
                }
            }
        }

        // === ШАГ 5: СТАНДАРТНЫЙ ПОИСК (Samsung, TCL, Hisense) ===
        // 1. Строка после слова MODEL
        for (i in rawLines.indices) {
            if (upperLines[i] == "MODEL" || upperLines[i].startsWith("MODEL")) {
                val sameLine = upperLines[i].replace("MODEL", "").trim()
                if (sameLine.isNotEmpty() && isCleanModel(sameLine)) return sameLine
                if (i + 1 < rawLines.size && isCleanModel(rawLines[i + 1])) return rawLines[i + 1].uppercase()
            }
        }

        // 2. Строка после бренда
        val brands = listOf("TCL", "SAMSUNG", "HISENSE", "SONY", "PHILIPS", "XIAOMI")
        for (i in rawLines.indices) {
            if (brands.any { upperLines[i].startsWith(it) }) {
                if (i + 1 < rawLines.size && isCleanModel(rawLines[i + 1])) return rawLines[i + 1].uppercase()
            }
        }

        // 3. Первые 5 строк с паттерном модели
        val modelPattern = Pattern.compile("(?<![A-Z0-9])(\\d{2})([A-Z]{0,5}\\d{0,5}[A-Z0-9]{0,10})(?![A-Z0-9])")
        for (i in 0 until minOf(5, rawLines.size)) {
            val m = modelPattern.matcher(upperLines[i])
            while (m.find()) {
                val candidate = m.group(0)
                if (isCleanModel(candidate)) return candidate
            }
        }

        // 4. Любой паттерн в тексте
        val allText = upperLines.joinToString(" ")
        val m = modelPattern.matcher(allText)
        while (m.find()) {
            val candidate = m.group(0)
            if (isCleanModel(candidate)) return candidate
        }

        return ""
    }
}
