package dev.fabik.bluetoothhid.ocr

import android.graphics.Bitmap

data class ExtractedBarcode(
    val originalValue: String,
    val cleanedValue: String,
    val confidence: Float,
    val pageNumber: Int,
    val isPotentialBarcode: Boolean,
    val warning: String? = null
)

interface BarcodeOcrProvider {
    val name: String
    suspend fun extractBarcodesFromPage(pageBitmap: Bitmap, pageNumber: Int): List<ExtractedBarcode>
}
