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

    // Pattern to identify candidate barcode numeric strings (6 to 20 characters)
    private val barcodeCandidatePattern = Pattern.compile("[0-9A-Za-z\\-]{6,20}")

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

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()

                // Split line by spaces, commas, slashes commonly used in bills
                val tokens = lineText.split(Regex("[\\s,/;|]+"))

                for (rawToken in tokens) {
                    val token = rawToken.trim()
                    if (token.length in 6..20 && barcodeCandidatePattern.matcher(token).find()) {
                        val cleaned = cleanHandwrittenBarcode(token)
                        if (cleaned.isNotBlank()) {
                            val confidence = calculateConfidence(line.confidence ?: 0.85f, token, cleaned)
                            val isPotential = cleaned.length in 8..16 && cleaned.all { it.isDigit() }
                            val warning = when {
                                !cleaned.all { it.isDigit() } -> "Contains non-numeric characters"
                                cleaned.length < 8 -> "Short barcode length (< 8 digits)"
                                token != cleaned -> "Auto-corrected handwriting characters"
                                else -> null
                            }

                            results.add(
                                ExtractedBarcode(
                                    originalValue = token,
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
            // High contrast grayscale
            setSaturation(0.0f)
            val contrast = 1.6f
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
                    '-' -> sb.append('-') // Preserve dashes in part numbers if present
                    else -> sb.append(c)
                }
            }
        }

        val cleaned = sb.toString().trim()
        // If more than 75% of characters are digits, return cleaned string
        return if (cleaned.length >= 6 && digitCount.toFloat() / cleaned.length >= 0.70f) {
            cleaned
        } else {
            ""
        }
    }

    private fun calculateConfidence(baseConfidence: Float, raw: String, cleaned: String): Float {
        var conf = baseConfidence
        if (raw != cleaned) conf *= 0.90f
        if (!cleaned.all { it.isDigit() }) conf *= 0.85f
        if (cleaned.length < 8) conf *= 0.80f
        return conf.coerceIn(0.1f, 1.0f)
    }
}
