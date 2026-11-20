package dev.fabik.bluetoothhid.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
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
        var name = "bill_${System.currentTimeMillis()}"
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
     * Copies any PDF or converts an image (JPG/PNG) into an internal A4 PDF file.
     */
    suspend fun copyOrConvertMediaToInternalPdf(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(context, uri).lowercase()

        val isImage = mimeType.startsWith("image/") ||
                fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") ||
                fileName.endsWith(".webp")

        if (isImage) {
            // Convert photo/image to single-page A4 PDF
            convertImageUriToPdf(context, uri)
        } else {
            // Copy PDF directly
            copyPdfToInternalStorage(context, uri)
        }
    }

    /**
     * Converts a Camera photo Bitmap directly into an internal PDF.
     */
    suspend fun convertBitmapToInternalPdf(context: Context, bitmap: Bitmap, baseName: String = "camera_bill"): File = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val targetFile = File(pdfsDir, "pdf_${System.currentTimeMillis()}_${baseName}.pdf")

        val pdfDoc = PdfDocument()
        val a4Width = 595
        val a4Height = 842

        val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        // Scale bitmap to fit A4 page preserving aspect ratio
        val scale = minOf(
            a4Width.toFloat() / bitmap.width.toFloat(),
            a4Height.toFloat() / bitmap.height.toFloat()
        )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = (a4Width - scaledWidth) / 2f
        val top = (a4Height - scaledHeight) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(left, top)
        }
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        pdfDoc.finishPage(page)

        FileOutputStream(targetFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        targetFile
    }

    private suspend fun convertImageUriToPdf(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalStateException("Could not open image stream")
        }

        convertBitmapToInternalPdf(context, bitmap, getFileName(context, uri).replace("[^a-zA-Z0-9]".toRegex(), "_"))
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
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
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
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)

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
