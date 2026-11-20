package dev.fabik.bluetoothhid.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfImporter {

    /**
     * Resolves the filename of a content URI.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "bill_${System.currentTimeMillis()}.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    /**
     * Copies a PDF from SAF URI into internal app storage safely.
     */
    suspend fun copyPdfToInternalStorage(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val filename = "pdf_${System.currentTimeMillis()}_${getFileName(context, uri).replace(" ", "_")}"
        val targetFile = File(pdfsDir, filename)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream from PDF URI")

        targetFile
    }

    /**
     * Gets the total page count of a PDF file.
     */
    fun getPageCount(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        renderer.close()
        pfd.close()
        return count
    }

    /**
     * Renders a specific page to a thumbnail Bitmap.
     */
    fun renderPageThumbnail(file: File, pageIndex: Int, targetWidth: Int = 240): Bitmap {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // Fill background white before rendering
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    /**
     * Renders a specific page at high resolution (for AI OCR analysis).
     */
    fun renderPageHighRes(file: File, pageIndex: Int, scale: Float = 2.0f): Bitmap {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val page = renderer.openPage(pageIndex)
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            pfd.close()
        }
    }
}
