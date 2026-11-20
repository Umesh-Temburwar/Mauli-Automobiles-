package dev.fabik.bluetoothhid.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrProvider : BarcodeOcrProvider {

    override val name: String = "Google On-Device ML Kit"

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Pattern to identify candidate barcode numeric strings or part numbers (4 to 25 characters)
    private val candidatePattern = Pattern.compile("[0-9A-Za-z\\-]{4,25}")

    override suspend fun extractBarcodesFromPage(
        pageBitmap: Bitmap,
        pageNumber: Int
    ): List<ExtractedBarcode> = withContext(Dispatchers.Default) {
        val preprocessed = preprocessForOcr(pageBitmap)
        val inputImage = InputImage.fromBitmap(preprocessed, 0)

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val results = mutableListOf<ExtractedBarcode>()
        val seenCleaned = mutableSetOf<String>()

        fun processCandidate(raw: String, baseConfidence: Float) {
            val trimmed = raw.trim()
            if (trimmed.length in 4..25 && candidatePattern.matcher(trimmed).find()) {
                val cleaned = cleanHandwrittenBarcode(trimmed)
                if (cleaned.isNotBlank() && !seenCleaned.contains(cleaned)) {
                    seenCleaned.add(cleaned)
                    val confidence = calculateConfidence(baseConfidence, trimmed, cleaned)
                    val isPotential = cleaned.length in 6..18
                    val warning = when {
                        !cleaned.all { it.isDigit() || it == '-' } -> "Contains non-numeric characters"
                        cleaned.length < 6 -> "Short part code (< 6 chars)"
                        trimmed != cleaned -> "Handwriting corrected"
                        else -> null
                    }

                    results.add(
                        ExtractedBarcode(
                            originalValue = trimmed,
                            cleanedValue = cleaned,
                            confidence = confidence,
                            pageNumber = pageNumber,
                            isPotentialBarcode = isPotential,
                            warning = warning
                        )
                    )
                }
            }
        }

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                val lineConfidence = line.confidence ?: 0.88f

                // Strategy 1: Check entire line without spaces (e.g. "8901 2345 6789" -> "890123456789")
                val noSpace = lineText.replace(" ", "")
                if (noSpace.length in 6..20 && noSpace.any { it.isDigit() }) {
                    processCandidate(noSpace, lineConfidence)
                }

                // Strategy 2: Check consecutive numeric blocks in the line
                val matcher = Pattern.compile("\\b[0-9A-Za-z\\-]{4,25}\\b").matcher(lineText)
                while (matcher.find()) {
                    processCandidate(matcher.group(), lineConfidence)
                }

                // Strategy 3: Split line by common delimiters (tabs, commas, slashes, pipes)
                val tokens = lineText.split(Regex("[\\t,/;|]+"))
                for (token in tokens) {
                    processCandidate(token, lineConfidence)
                }
            }
        }

        // Avoid memory leaks
        if (preprocessed != pageBitmap) {
            preprocessed.recycle()
        }

        results
    }

    /**
     * Enhances contrast and converts to high-contrast grayscale to make handwritten
     * pen/pencil strokes on shop bills stand out sharply against paper.
     */
    private fun preprocessForOcr(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cm = ColorMatrix().apply {
            setSaturation(0.0f)
            val contrast = 1.65f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val contrastMatrix = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(contrastMatrix))
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isAntiAlias = true
        }

        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
    }

    /**
     * Cleans common handwritten barcode character confusions (e.g. O -> 0, I/l -> 1).
     * Preserves exact leading zeros like "00123...".
     * Preserves hyphens for Hero/Honda part numbers (e.g. 17211-KUD-900).
     */
    private fun cleanHandwrittenBarcode(raw: String): String {
        val sb = StringBuilder()
        var digitCount = 0

        for (c in raw) {
            if (c.isDigit()) {
                sb.append(c)
                digitCount++
            } else {
                when (c) {
                    'O', 'o' -> { sb.append('0'); digitCount++ }
                    'I', 'l', '|' -> { sb.append('1'); digitCount++ }
                    'S', 's' -> { sb.append('5'); digitCount++ }
                    'Z', 'z' -> { sb.append('2'); digitCount++ }
                    'B' -> { sb.append('8'); digitCount++ }
                    '-' -> sb.append('-') // Preserve dashes in Honda/Hero part numbers
                    else -> sb.append(c)
                }
            }
        }

        val cleaned = sb.toString().trim()
        // If more than 60% of characters are digits or part numbers, accept it
        val validChars = cleaned.count { it.isDigit() || it == '-' }
        return if (cleaned.length >= 4 && validChars.toFloat() / cleaned.length >= 0.60f) {
            cleaned
        } else {
            ""
        }
    }

    private fun calculateConfidence(baseConfidence: Float, raw: String, cleaned: String): Float {
        var conf = baseConfidence
        if (raw != cleaned) conf *= 0.90f
        if (!cleaned.all { it.isDigit() || it == '-' }) conf *= 0.85f
        if (cleaned.length < 6) conf *= 0.80f
        return conf.coerceIn(0.1f, 1.0f)
    }
}
