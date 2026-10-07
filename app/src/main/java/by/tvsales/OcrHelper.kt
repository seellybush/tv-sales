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

    private val brands = listOf(
        "SAMSUNG", "LG", "TCL", "SONY", "HISENSE", "PHILIPS", "XIAOMI",
        "HAIER", "SKYWORTH", "HORIZONT", "ГОРИЗОНТ", "ВИТЯЗЬ", "VITEBSK",
        "ASANO", "HYUNDAI", "HARPER", "LEFF", "BBK", "DIGMA", "POLARLINE",
        "SHIVAKI", "SUNWIND", "DREAME", "FOX", "EVO", "QUANTUM", "ROOME",
        "JVC", "THOMSON", "KIVI", "BQ", "ЯНДЕКС", "YANDEX", "POLAR"
    )

    private val noiseSubstrings = listOf(
        "CARTON", "VOLTAGE", "WEIGHT", "GROSS", "DIMENSIONS",
        "S/N", "S/NO", "SERIAL", "W/O", "DATE", "MODEL", "МОДЕЛЬ",
        "ТУ BY", "TYBY", "ТУBY", "EAC", "HDMI", "USB", "WEBOS", "TIZEN",
        "ANDROID", "GOOGLE", "SMART", "SERIES", "TEL", "КОД",
        "ЭЛЕКТРОНИКС", "МИНСК", "РОССИ", "БЕЛАРУ", "СДЕЛАНО",
        "ИЗГОТОВИТЕЛЬ", "ИМПОРТЕР", "ПРОИЗВОДИТЕЛЬ"
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
        val allWords = mutableListOf<String>()
        for (line in text.split("\n")) {
            val cleaned = line.replace(":", " ").replace("|", " ").replace(",", " ")
            allWords.addAll(cleaned.split(Regex("\\s+")).filter { it.isNotBlank() })
        }

        fun isSerial(s: String): Boolean {
            val u = s.uppercase().replace(" ", "")
            if (Regex("\\d{8,}").containsMatchIn(u)) return true
            if (u.startsWith("TY") || u.startsWith("ТУ")) return true
            if (u.length >= 15 && u.count { it.isDigit() } >= 6) return true
            return false
        }

        fun containsNoise(s: String): Boolean {
            val u = s.uppercase()
            return noiseSubstrings.any { u.contains(it) }
        }

        fun isCleanModel(s: String): Boolean {
            val upper = s.uppercase().trim()
            if (upper.length < 4 || upper.length > 15) return false
            if (!upper.any { it.isDigit() }) return false
            if (!upper.any { it.isLetter() }) return false

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

        val modelPattern = Regex(
            "(2[48]|3[29]|4[0238]|50|5[058]|60|65|70|75|77|83|85|98|100|115)([A-Z]{1,8}\\d{0,5}[A-Z0-9]{0,8})"
        )

        data class Candidate(val value: String, val priority: Int)
        val candidates = mutableListOf<Candidate>()

        for (word in allWords) {
            if (containsNoise(word)) continue
            if (isSerial(word)) continue

            val fixed = fixOcr(word)
            val matches = modelPattern.findAll(fixed)

            for (m in matches) {
                val c = m.value
                if (!isCleanModel(c)) continue

                var priority = 5
                val textUpper = text.uppercase()
                val wordPos = textUpper.indexOf(word.uppercase())
                val before = if (wordPos > 0) textUpper.substring(0, wordPos) else ""
                val lastBefore = before.split(Regex("\\s+")).lastOrNull { it.isNotBlank() }?.uppercase() ?: ""

                if (lastBefore.contains("QUANTUM") || word.uppercase().contains("QUANTUM")) priority = 1
                else if (lastBefore == "MODEL" || lastBefore == "МОДЕЛЬ") priority = 1
                else if (brands.any { lastBefore.contains(it) }) priority = 2
                else if (wordPos in 0..100) priority = 3

                if (c.length in 5..10) priority -= 1

                candidates.add(Candidate(c, priority))
            }
        }

        return candidates
            .sortedWith(compareBy({ it.priority }, { it.value.length }))
            .firstOrNull()?.value ?: ""
    }

    fun generateSearchVariants(model: String): List<String> {
        val variants = mutableListOf(model)
        if (model.contains("8")) variants.add(model.replace("8", "B"))
        if (model.contains("0")) variants.add(model.replace("0", "O"))
        if (model.contains("O")) variants.add(model.replace("O", "0"))
        if (model.endsWith("1")) variants.add(model.dropLast(1) + "L")
        if (model.contains("1")) variants.add(model.replace("1", "I"))
        if (model.contains("5")) variants.add(model.replace("5", "S"))
        if (model.contains("S")) variants.add(model.replace("S", "5"))
        if (model.contains("G")) variants.add(model.replace("G", "6"))
        if (model.contains("B")) variants.add(model.replace("B", "8"))
        if (model.endsWith("8")) variants.add(model.dropLast(1) + "B")
        if (model.length >= 6) variants.add(model.dropLast(1))
        if (model.length >= 7) variants.add(model.dropLast(2))
        return variants.distinct()
    }
}
