package dev.fabik.bluetoothhid.ocr

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class GeminiOcrProvider(private val apiKey: String) : BarcodeOcrProvider {

    override val name: String = "Google Gemini AI Vision"

    override suspend fun extractBarcodesFromPage(
        pageBitmap: Bitmap,
        pageNumber: Int
    ): List<ExtractedBarcode> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            // Compress bitmap to JPEG Base64
            val baos = ByteArrayOutputStream()
            pageBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val prompt = """
                You are an OCR specialist for automobile spare parts bills.
                Look at this handwritten bill page.
                Extract ALL handwritten barcode numbers and product part numbers.
                Rules:
                1. Preserve all leading zeros exactly (e.g. 0012345).
                2. Do not omit any numbers.
                3. Return ONLY a raw JSON array of strings, for example: ["8901234567890", "0012345678901", "9876543210"]
                4. Output nothing else, no markdown, just JSON.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val jsonResponse = JSONObject(responseText)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "")
                        return@withContext parseGeminiJson(text, pageNumber)
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    private fun parseGeminiJson(rawText: String, pageNumber: Int): List<ExtractedBarcode> {
        val list = mutableListOf<ExtractedBarcode>()
        try {
            val cleanedText = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = JSONArray(cleanedText)
            for (i in 0 until jsonArray.length()) {
                val value = jsonArray.optString(i, "").trim()
                if (value.isNotBlank()) {
                    list.add(
                        ExtractedBarcode(
                            originalValue = value,
                            cleanedValue = value,
                            confidence = 0.98f,
                            pageNumber = pageNumber,
                            isPotentialBarcode = value.length in 8..16 && value.all { it.isDigit() },
                            warning = if (value.length < 8) "Short barcode length (< 8 digits)" else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
