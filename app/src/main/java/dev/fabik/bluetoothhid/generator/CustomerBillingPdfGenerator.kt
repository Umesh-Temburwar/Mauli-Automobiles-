package dev.fabik.bluetoothhid.generator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.fabik.bluetoothhid.data.CustomerScanRecord
import dev.fabik.bluetoothhid.data.CustomerScansSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CustomerBillingPdfGenerator {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    private const val MARGIN_X = 32f
    private const val MARGIN_TOP = 32f
    private const val MARGIN_BOTTOM = 36f

    private const val ITEM_CARD_HEIGHT = 82f
    private const val ITEMS_FIRST_PAGE = 7
    private const val ITEMS_SUBSEQUENT_PAGE = 8

    /**
     * Generates a complete daily customer spare parts scan and billing report in A4 PDF format.
     * Contains product names, OEM part numbers, vehicle compatibility, quantities, MRP amounts,
     * and visual scannable Code 128 barcodes for every item.
     */
    suspend fun generateCustomerReportPdf(
        context: Context,
        dateStr: String,
        records: List<CustomerScanRecord>,
        summary: CustomerScansSummary
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "customer_reports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())
        val fileName = "Mauli_Daily_Spares_${dateStr.replace("-", "")}_$timeStamp.pdf"
        val pdfFile = File(outputDir, fileName)

        val pdfDoc = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 30, 60)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 15f
            isAntiAlias = true
        }

        val subTitlePaint = Paint().apply {
            color = Color.rgb(80, 80, 90)
            textSize = 8.5f
            isAntiAlias = true
        }

        val reportBadgePaint = Paint().apply {
            color = Color.rgb(180, 40, 20)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10.5f
            isAntiAlias = true
        }

        val summaryBoxPaint = Paint().apply {
            color = Color.rgb(243, 245, 249)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val summaryTextPaint = Paint().apply {
            color = Color.rgb(30, 40, 60)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            color = Color.rgb(220, 224, 230)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }

        val cardFillPaint = Paint().apply {
            color = Color.rgb(255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val itemIndexPaint = Paint().apply {
            color = Color.rgb(100, 110, 125)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 8.5f
            isAntiAlias = true
        }

        val productNamePaint = Paint().apply {
            color = Color.rgb(15, 25, 45)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.8f
            isAntiAlias = true
        }

        val partNumberPaint = Paint().apply {
            color = Color.rgb(180, 50, 20)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 8.5f
            isAntiAlias = true
        }

        val detailsPaint = Paint().apply {
            color = Color.rgb(70, 75, 85)
            textSize = 8f
            isAntiAlias = true
        }

        val pricePaint = Paint().apply {
            color = Color.rgb(20, 110, 40)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9f
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(130, 135, 145)
            textSize = 7.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val totalRecords = records.size
        val totalPages = if (totalRecords <= ITEMS_FIRST_PAGE) 1
        else 1 + ((totalRecords - ITEMS_FIRST_PAGE + ITEMS_SUBSEQUENT_PAGE - 1) / ITEMS_SUBSEQUENT_PAGE)

        var recordIdx = 0
        val exportTimeStr = SimpleDateFormat("dd-MM-yyyy • HH:mm", Locale.getDefault()).format(Date())

        for (pageIdx in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageIdx + 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas = page.canvas

            var currentY = MARGIN_TOP

            if (pageIdx == 0) {
                // Main Header
                canvas.drawText("MAULI AUTOMOBILES (मौली ऑटोमोबाईल्स)", MARGIN_X, currentY + 12f, titlePaint)
                canvas.drawText("Hero MotoCorp & Honda Genuine Spares • Ambajogai | Ph: 098232 91444", MARGIN_X, currentY + 25f, subTitlePaint)
                canvas.drawText("DAILY CUSTOMER SPARES REPORT & BARCODES (दैनिक विक्री व बारकोड अहवाल)", MARGIN_X, currentY + 41f, reportBadgePaint)
                canvas.drawText("Report Date: $dateStr  |  Exported: $exportTimeStr", MARGIN_X, currentY + 54f, subTitlePaint)

                currentY += 62f

                // Summary Badge Bar
                val summaryRect = RectF(MARGIN_X, currentY, A4_WIDTH - MARGIN_X, currentY + 24f)
                canvas.drawRoundRect(summaryRect, 4f, 4f, summaryBoxPaint)
                canvas.drawRoundRect(summaryRect, 4f, 4f, cardBorderPaint)

                val summaryText = "Total Items: ${summary.totalRecords}    |    Total Qty: ${summary.totalQuantity} pcs    |    Unique Barcodes: ${summary.uniqueBarcodes}    |    Total Amount: ₹${"%.2f".format(summary.totalAmount)}"
                canvas.drawText(summaryText, MARGIN_X + 10f, currentY + 16f, summaryTextPaint)

                currentY += 34f
            } else {
                // Secondary Page Header
                canvas.drawText("MAULI AUTOMOBILES • DAILY SPARES & BARCODES • Date: $dateStr", MARGIN_X, currentY + 12f, titlePaint)
                canvas.drawLine(MARGIN_X, currentY + 18f, A4_WIDTH - MARGIN_X, currentY + 18f, cardBorderPaint)
                currentY += 28f
            }

            val maxItemsThisPage = if (pageIdx == 0) ITEMS_FIRST_PAGE else ITEMS_SUBSEQUENT_PAGE
            var itemsOnThisPage = 0

            while (recordIdx < totalRecords && itemsOnThisPage < maxItemsThisPage) {
                val record = records[recordIdx]
                val itemCardRect = RectF(MARGIN_X, currentY, A4_WIDTH - MARGIN_X, currentY + ITEM_CARD_HEIGHT)

                // Draw Item Card Background & Border
                canvas.drawRoundRect(itemCardRect, 4f, 4f, cardFillPaint)
                canvas.drawRoundRect(itemCardRect, 4f, 4f, cardBorderPaint)

                val leftX = itemCardRect.left + 8f
                var lineY = itemCardRect.top + 14f

                // #1 Index and Product Name
                val indexStr = "#${recordIdx + 1}"
                canvas.drawText(indexStr, leftX, lineY, itemIndexPaint)

                // Truncate product name if too long
                val maxNameLength = 36
                val displayName = if (record.productName.length > maxNameLength) {
                    record.productName.take(maxNameLength) + "..."
                } else {
                    record.productName
                }
                canvas.drawText(displayName, leftX + 22f, lineY, productNamePaint)

                lineY += 14f
                // OEM Part Number & Brand
                val partBrand = "Part No: ${record.partNumber}  [${record.brand}]"
                canvas.drawText(partBrand, leftX, lineY, partNumberPaint)

                lineY += 13f
                // Compatible models / Notes
                if (record.notes.isNotBlank()) {
                    val compText = "Fits: ${record.notes.take(42)}"
                    canvas.drawText(compText, leftX, lineY, detailsPaint)
                    lineY += 13f
                }

                // Quantity & Price
                val priceText = if (record.mrp > 0) {
                    "Qty: ${record.quantity} pcs  ×  ₹${"%.2f".format(record.mrp)}  =  ₹${"%.2f".format(record.quantity * record.mrp)}"
                } else {
                    "Qty: ${record.quantity} pcs"
                }
                canvas.drawText(priceText, leftX, lineY, pricePaint)

                if (record.customerName.isNotBlank()) {
                    val custText = "Cust: ${record.customerName}"
                    canvas.drawText(custText, leftX + 170f, lineY, detailsPaint)
                }

                // Barcode Column on the right
                val barcodeWidth = 180f
                val barcodeHeight = 56f
                val barcodeLeft = itemCardRect.right - barcodeWidth - 10f
                val barcodeTop = itemCardRect.top + (ITEM_CARD_HEIGHT - barcodeHeight) / 2f

                try {
                    val barcodeBitmap = BarcodeGenerator.generateCode128(
                        content = record.barcode,
                        width = 400,
                        height = 130,
                        showText = true
                    )
                    val destRect = RectF(
                        barcodeLeft,
                        barcodeTop,
                        barcodeLeft + barcodeWidth,
                        barcodeTop + barcodeHeight
                    )
                    canvas.drawBitmap(barcodeBitmap, null, destRect, null)
                    barcodeBitmap.recycle()
                } catch (e: Exception) {
                    // Fallback to text if barcode encoding fails
                    val errPaint = Paint().apply {
                        color = Color.DKGRAY
                        textSize = 9f
                        typeface = Typeface.MONOSPACE
                    }
                    canvas.drawText("Barcode: ${record.barcode}", barcodeLeft, barcodeTop + 30f, errPaint)
                }

                currentY += ITEM_CARD_HEIGHT + 6f
                recordIdx++
                itemsOnThisPage++
            }

            // Draw Page Footer
            val footerText = "MAULI AUTOMOBILES • Ambajogai • Barcode Scanner Report • Page ${pageIdx + 1} of $totalPages"
            canvas.drawText(footerText, (A4_WIDTH / 2).toFloat(), A4_HEIGHT - 16f, footerPaint)

            pdfDoc.finishPage(page)
        }

        FileOutputStream(pdfFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        pdfFile
    }

    /**
     * Shares the generated PDF file via WhatsApp, Gmail, Bluetooth, or any available app.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "dev.fabik.bluetoothhid.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mauli Automobiles - Daily Spares & Barcode Report")
                putExtra(Intent.EXTRA_TEXT, "Mauli Automobiles Ambajogai - Daily Customer Spare Parts & Barcode Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Daily Spares PDF Share Karein"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share karne me error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens the generated PDF in the device's default PDF viewer or print service.
     */
    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "dev.fabik.bluetoothhid.fileprovider",
                pdfFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "PDF open karne ke liye koi PDF viewer nahi mila", Toast.LENGTH_LONG).show()
        }
    }
}
