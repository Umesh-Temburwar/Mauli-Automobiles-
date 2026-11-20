package dev.fabik.bluetoothhid.generator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.Code128Writer

object BarcodeGenerator {

    private val writer = Code128Writer()
    private val reader = MultiFormatReader()

    /**
     * Generates a valid Code 128 barcode bitmap with human-readable text below.
     * Preserves exact original string (including leading zeros).
     */
    fun generateCode128(
        content: String,
        width: Int = 600,
        height: Int = 200,
        showText: Boolean = true
    ): Bitmap {
        require(content.isNotBlank()) { "Barcode content cannot be blank" }

        val barcodeHeight = if (showText) (height * 0.72f).toInt() else height
        val textHeight = height - barcodeHeight

        // Generate Code 128 BitMatrix
        val bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, width, barcodeHeight)

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.WHITE)

        val barPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false // Crisp edges for barcode scanners
        }

        // Draw barcode bars
        for (x in 0 until width) {
            for (y in 0 until barcodeHeight) {
                if (bitMatrix.get(x, y)) {
                    outputBitmap.setPixel(x, y, Color.BLACK)
                }
            }
        }

        // Draw human readable text below barcode
        if (showText) {
            val textPaint = Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = (textHeight * 0.52f).coerceIn(16f, 32f)
                textAlign = Paint.Align.CENTER
            }

            val textY = barcodeHeight + (textHeight * 0.68f)
            canvas.drawText(content, (width / 2).toFloat(), textY, textPaint)
        }

        return outputBitmap
    }

    /**
     * Validates that the generated barcode decodes back to the exact expected content.
     * Guarantees 100% round-trip accuracy.
     */
    fun validateBarcode(bitmap: Bitmap, expectedContent: String): Boolean {
        return try {
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)

            result.text == expectedContent
        } catch (e: Exception) {
            // ZXing decode failed or mismatch
            false
        }
    }
}
