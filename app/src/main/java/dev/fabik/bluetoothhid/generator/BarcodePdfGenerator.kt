package dev.fabik.bluetoothhid.generator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BarcodePdfGenerator {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    private const val MARGIN_X = 35
    private const val MARGIN_TOP = 80
    private const val MARGIN_BOTTOM = 40

    private const val COLUMNS = 2
    private const val ROWS = 6
    private const val BARCODES_PER_PAGE = COLUMNS * ROWS // 12

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())

    /**
     * Generates a printable A4 PDF sheet containing all verified Code 128 barcodes.
     */
    suspend fun generateBarcodePdf(
        context: Context,
        batch: BillBatch,
        barcodes: List<BarcodeRecord>
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "generated_pdfs").apply { mkdirs() }
        val filename = "Barcodes_Bill_${batch.billNumber.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(outputDir, filename)

        val pdfDoc = PdfDocument()

        val totalPages = ((barcodes.size + BARCODES_PER_PAGE - 1) / BARCODES_PER_PAGE).coerceAtLeast(1)

        val cellWidth = (A4_WIDTH - (MARGIN_X * 2) - 20) / COLUMNS // ~252 pt
        val cellHeight = (A4_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - 20) / ROWS // ~117 pt

        val titlePaint = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            isAntiAlias = true
        }

        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val indexPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        for (pageIdx in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            // 1. Draw Page Header
            canvas.drawText("MAULI AUTOMOBILES (मौली ऑटोमोबाईल्स)", MARGIN_X.toFloat(), 32f, titlePaint)
            canvas.drawText("Honda & Hero Genuine Spares • Ambajogai | Ph: 098232 91444", MARGIN_X.toFloat(), 46f, subPaint)
            canvas.drawText(
                "Batch: ${batch.batchName} | Bill No: ${batch.billNumber} | Date: ${dateFormat.format(Date(batch.createdAt))}",
                MARGIN_X.toFloat(),
                60f,
                subPaint
            )

            // Divider line
            canvas.drawLine(
                MARGIN_X.toFloat(),
                68f,
                (A4_WIDTH - MARGIN_X).toFloat(),
                68f,
                borderPaint
            )

            // 2. Draw Barcode Cells
            val startIdx = pageIdx * BARCODES_PER_PAGE
            val endIdx = (startIdx + BARCODES_PER_PAGE).coerceAtMost(barcodes.size)

            for (i in startIdx until endIdx) {
                val itemIdxOnPage = i - startIdx
                val col = itemIdxOnPage % COLUMNS
                val row = itemIdxOnPage / COLUMNS

                val cellX = MARGIN_X + col * (cellWidth + 20)
                val cellY = MARGIN_TOP + row * cellHeight

                val rect = RectF(
                    cellX.toFloat(),
                    cellY.toFloat(),
                    (cellX + cellWidth).toFloat(),
                    (cellY + cellHeight - 6).toFloat()
                )

                // Cell box border
                canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

                // Item index #
                canvas.drawText("#${i + 1}", rect.left + 6, rect.top + 12, indexPaint)

                // Barcode bitmap
                val barcodeRecord = barcodes[i]
                val barcodeBitmap = BarcodeGenerator.generateCode128(
                    content = barcodeRecord.correctedValue,
                    width = 400,
                    height = 140,
                    showText = true
                )

                val destRect = Rect(
                    (rect.left + 8).toInt(),
                    (rect.top + 16).toInt(),
                    (rect.right - 8).toInt(),
                    (rect.bottom - 6).toInt()
                )
                canvas.drawBitmap(barcodeBitmap, null, destRect, null)
                barcodeBitmap.recycle()
            }

            // 3. Draw Page Footer
            val footerText = "Page ${pageIdx + 1} of $totalPages • Generated by MAULI BARCODE"
            canvas.drawText(
                footerText,
                (A4_WIDTH / 2).toFloat(),
                (A4_HEIGHT - 16).toFloat(),
                footerPaint
            )

            pdfDoc.finishPage(page)
        }

        FileOutputStream(pdfFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        pdfFile
    }

    /**
     * Prints the generated barcode PDF directly using Android's PrintManager.
     */
    fun printPdf(activity: Activity, pdfFile: File) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()

        printManager.print("Mauli_Barcode_${pdfFile.name}", printAdapter, printAttributes)
    }

    /**
     * Shares the generated barcode PDF via WhatsApp, Google Drive, Email, etc.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Mauli Barcode Sheet - ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Barcode PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
