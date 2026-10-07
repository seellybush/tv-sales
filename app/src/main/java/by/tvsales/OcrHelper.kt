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

    /**
     * Распознаёт текст с изображения по URI.
     */
    suspend fun recognizeText(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume("")
            }
        }

    /**
     * Ищет модель телевизора в распознанном тексте.
     * Паттерны: 55QLED780K, UE55M80HAUXPY, 43QLED780K и т.д.
     */
    fun extractModel(recognizedText: String): String {
        val cleanText = recognizedText.uppercase().replace(" ", "").replace("\n", "")

        // Паттерн: буквы (опционально) + 2-3 цифры (диагональ) + буквы/цифры (модель)
        val patterns = listOf(
            // Samsung/LG: UE55M80HAUXPY, 55QNED72B6B
            Pattern.compile("(?:UE|UN|QN|OLED|QNED)?(\\d{2,3})([A-Z0-9]{3,15})"),
            // TCL/Hisense: 55QLED780K, 50P79L
            Pattern.compile("(\\d{2,3})(QLED|QLED|P|C|MQLED|M|U|S)([A-Z0-9]{2,10})"),
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(cleanText)
            while (matcher.find()) {
                val match = matcher.group(0)
                if (match.length in 5..25 && match.any { it.isDigit() }) {
                    return match
                }
            }
        }

        return ""
    }
}
