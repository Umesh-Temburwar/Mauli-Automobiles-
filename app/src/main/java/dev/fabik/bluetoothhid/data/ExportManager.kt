package dev.fabik.bluetoothhid.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * Exports a batch's barcode records to a CSV file.
     */
    suspend fun exportBatchToCsv(
        context: Context,
        batch: BillBatch,
        records: List<BarcodeRecord>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val filename = "Batch_${batch.billNumber}_${dateFormat.format(Date())}.csv"
        val csvFile = File(exportDir, filename)

        FileWriter(csvFile).use { writer ->
            // Header
            writer.append("Barcode Value,Page Number,Status,Scan Status,Confidence,Original OCR\n")
            for (rec in records) {
                // Ensure value is treated as text by prepending tab or quoting to preserve leading zeros in Excel
                writer.append("\"${rec.correctedValue}\",")
                writer.append("${rec.pageNumber},")
                writer.append("${rec.verificationStatus},")
                writer.append("${rec.scanStatus},")
                writer.append("${(rec.confidence * 100).toInt()}%,")
                writer.append("\"${rec.originalOcrValue}\"\n")
            }
        }
        csvFile
    }

    /**
     * Shares an exported CSV file via Android share sheet.
     */
    fun shareCsvFile(context: Context, file: File, title: String = "Share Barcode Report") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
