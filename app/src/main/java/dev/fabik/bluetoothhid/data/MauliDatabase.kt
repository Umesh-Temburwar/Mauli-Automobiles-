package dev.fabik.bluetoothhid.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MauliDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "mauli_barcode.db"
        const val DATABASE_VERSION = 1

        // Tables
        const val TABLE_PDF_DOCUMENTS = "pdf_documents"
        const val TABLE_BILL_BATCHES = "bill_batches"
        const val TABLE_BARCODE_RECORDS = "barcode_records"
        const val TABLE_SCAN_HISTORY = "scan_history"

        @Volatile
        private var instance: MauliDatabase? = null

        fun getInstance(context: Context): MauliDatabase =
            instance ?: synchronized(this) {
                instance ?: MauliDatabase(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PDF_DOCUMENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT NOT NULL,
                importDate INTEGER NOT NULL,
                totalPages INTEGER NOT NULL,
                processingStatus TEXT NOT NULL,
                filePath TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BILL_BATCHES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batchName TEXT NOT NULL,
                billNumber TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                pdfDocumentId INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BARCODE_RECORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batchId INTEGER NOT NULL,
                originalOcrValue TEXT NOT NULL,
                correctedValue TEXT NOT NULL,
                pageNumber INTEGER NOT NULL,
                confidence REAL NOT NULL,
                verificationStatus TEXT NOT NULL,
                barcodeFormat TEXT NOT NULL,
                scanStatus TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SCAN_HISTORY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcodeRecordId INTEGER NOT NULL,
                scannedValue TEXT NOT NULL,
                scanTime INTEGER NOT NULL,
                transmissionStatus TEXT NOT NULL,
                retryCount INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Indexes for lightning fast searching and joining
        db.execSQL("CREATE INDEX idx_barcode_batch ON $TABLE_BARCODE_RECORDS (batchId)")
        db.execSQL("CREATE INDEX idx_barcode_value ON $TABLE_BARCODE_RECORDS (correctedValue)")
        db.execSQL("CREATE INDEX idx_batch_pdf ON $TABLE_BILL_BATCHES (pdfDocumentId)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCAN_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BARCODE_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BILL_BATCHES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PDF_DOCUMENTS")
        onCreate(db)
    }

    /* -------------------- PDF DOCUMENTS -------------------- */

    suspend fun insertPdfDocument(doc: PdfDocumentRecord): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("filename", doc.filename)
            put("importDate", doc.importDate)
            put("totalPages", doc.totalPages)
            put("processingStatus", doc.processingStatus)
            put("filePath", doc.filePath)
        }
        writableDatabase.insert(TABLE_PDF_DOCUMENTS, null, values)
    }

    suspend fun getAllPdfDocuments(): List<PdfDocumentRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<PdfDocumentRecord>()
        readableDatabase.rawQuery(
            "SELECT id, filename, importDate, totalPages, processingStatus, filePath FROM $TABLE_PDF_DOCUMENTS ORDER BY importDate DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    PdfDocumentRecord(
                        id = cursor.getLong(0),
                        filename = cursor.getString(1),
                        importDate = cursor.getLong(2),
                        totalPages = cursor.getInt(3),
                        processingStatus = cursor.getString(4),
                        filePath = cursor.getString(5)
                    )
                )
            }
        }
        list
    }

    suspend fun deletePdfDocument(id: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Delete associated barcode records and batches
            val batches = getBatchesForPdf(id)
            for (b in batches) {
                db.delete(TABLE_BARCODE_RECORDS, "batchId = ?", arrayOf(b.id.toString()))
            }
            db.delete(TABLE_BILL_BATCHES, "pdfDocumentId = ?", arrayOf(id.toString()))
            db.delete(TABLE_PDF_DOCUMENTS, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* -------------------- BILL BATCHES -------------------- */

    suspend fun insertBatch(batch: BillBatch): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("batchName", batch.batchName)
            put("billNumber", batch.billNumber)
            put("createdAt", batch.createdAt)
            put("pdfDocumentId", batch.pdfDocumentId)
        }
        writableDatabase.insert(TABLE_BILL_BATCHES, null, values)
    }

    suspend fun getBatchesForPdf(pdfId: Long): List<BillBatch> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BillBatch>()
        readableDatabase.rawQuery(
            "SELECT id, batchName, billNumber, createdAt, pdfDocumentId FROM $TABLE_BILL_BATCHES WHERE pdfDocumentId = ? ORDER BY createdAt DESC",
            arrayOf(pdfId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    BillBatch(
                        id = cursor.getLong(0),
                        batchName = cursor.getString(1),
                        billNumber = cursor.getString(2),
                        createdAt = cursor.getLong(3),
                        pdfDocumentId = cursor.getLong(4)
                    )
                )
            }
        }
        list
    }

    suspend fun getAllBatches(): List<BillBatch> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BillBatch>()
        readableDatabase.rawQuery(
            "SELECT id, batchName, billNumber, createdAt, pdfDocumentId FROM $TABLE_BILL_BATCHES ORDER BY createdAt DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    BillBatch(
                        id = cursor.getLong(0),
                        batchName = cursor.getString(1),
                        billNumber = cursor.getString(2),
                        createdAt = cursor.getLong(3),
                        pdfDocumentId = cursor.getLong(4)
                    )
                )
            }
        }
        list
    }

    suspend fun getBatchById(id: Long): BillBatch? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT id, batchName, billNumber, createdAt, pdfDocumentId FROM $TABLE_BILL_BATCHES WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                BillBatch(
                    id = cursor.getLong(0),
                    batchName = cursor.getString(1),
                    billNumber = cursor.getString(2),
                    createdAt = cursor.getLong(3),
                    pdfDocumentId = cursor.getLong(4)
                )
            } else null
        }
    }

    suspend fun deleteBatch(id: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_BARCODE_RECORDS, "batchId = ?", arrayOf(id.toString()))
            db.delete(TABLE_BILL_BATCHES, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /* -------------------- BARCODE RECORDS -------------------- */

    suspend fun insertBarcodeRecords(records: List<BarcodeRecord>): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (record in records) {
                val values = ContentValues().apply {
                    put("batchId", record.batchId)
                    put("originalOcrValue", record.originalOcrValue)
                    put("correctedValue", record.correctedValue)
                    put("pageNumber", record.pageNumber)
                    put("confidence", record.confidence)
                    put("verificationStatus", record.verificationStatus)
                    put("barcodeFormat", record.barcodeFormat)
                    put("scanStatus", record.scanStatus)
                    put("createdAt", record.createdAt)
                }
                val id = db.insert(TABLE_BARCODE_RECORDS, null, values)
                ids.add(id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ids
    }

    suspend fun getBarcodesForBatch(batchId: Long): List<BarcodeRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BarcodeRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, batchId, originalOcrValue, correctedValue, pageNumber, confidence, verificationStatus, barcodeFormat, scanStatus, createdAt 
            FROM $TABLE_BARCODE_RECORDS 
            WHERE batchId = ? 
            ORDER BY pageNumber ASC, id ASC
            """.trimIndent(),
            arrayOf(batchId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToBarcodeRecord(cursor))
            }
        }
        list
    }

    suspend fun getAllBarcodes(): List<BarcodeRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BarcodeRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, batchId, originalOcrValue, correctedValue, pageNumber, confidence, verificationStatus, barcodeFormat, scanStatus, createdAt 
            FROM $TABLE_BARCODE_RECORDS 
            ORDER BY createdAt DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToBarcodeRecord(cursor))
            }
        }
        list
    }

    suspend fun updateBarcode(record: BarcodeRecord) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("correctedValue", record.correctedValue)
            put("verificationStatus", record.verificationStatus)
            put("scanStatus", record.scanStatus)
        }
        writableDatabase.update(
            TABLE_BARCODE_RECORDS,
            values,
            "id = ?",
            arrayOf(record.id.toString())
        )
    }

    suspend fun deleteBarcode(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BARCODE_RECORDS, "id = ?", arrayOf(id.toString()))
    }

    suspend fun markBarcodeScanned(batchId: Long, barcodeValue: String): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase
        // Find matching pending barcode
        val query = """
            SELECT id FROM $TABLE_BARCODE_RECORDS 
            WHERE batchId = ? AND correctedValue = ? 
            LIMIT 1
        """.trimIndent()

        var foundId: Long? = null
        db.rawQuery(query, arrayOf(batchId.toString(), barcodeValue.trim())).use { cursor ->
            if (cursor.moveToFirst()) {
                foundId = cursor.getLong(0)
            }
        }

        if (foundId != null) {
            val values = ContentValues().apply { put("scanStatus", "SCANNED") }
            db.update(TABLE_BARCODE_RECORDS, values, "id = ?", arrayOf(foundId.toString()))
            return@withContext true
        }
        return@withContext false
    }

    suspend fun findDuplicatesInBatch(batchId: Long): Set<String> = withContext(Dispatchers.IO) {
        val duplicates = mutableSetOf<String>()
        readableDatabase.rawQuery(
            """
            SELECT correctedValue, COUNT(*) as cnt 
            FROM $TABLE_BARCODE_RECORDS 
            WHERE batchId = ? 
            GROUP BY correctedValue 
            HAVING cnt > 1
            """.trimIndent(),
            arrayOf(batchId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                duplicates.add(cursor.getString(0))
            }
        }
        duplicates
    }

    /* -------------------- SCAN HISTORY -------------------- */

    suspend fun insertScanHistory(record: ScanHistoryRecord): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("barcodeRecordId", record.barcodeRecordId)
            put("scannedValue", record.scannedValue)
            put("scanTime", record.scanTime)
            put("transmissionStatus", record.transmissionStatus)
            put("retryCount", record.retryCount)
        }
        writableDatabase.insert(TABLE_SCAN_HISTORY, null, values)
    }

    suspend fun getAllScanHistory(): List<ScanHistoryRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ScanHistoryRecord>()
        readableDatabase.rawQuery(
            "SELECT id, barcodeRecordId, scannedValue, scanTime, transmissionStatus, retryCount FROM $TABLE_SCAN_HISTORY ORDER BY scanTime DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ScanHistoryRecord(
                        id = cursor.getLong(0),
                        barcodeRecordId = cursor.getLong(1),
                        scannedValue = cursor.getString(2),
                        scanTime = cursor.getLong(3),
                        transmissionStatus = cursor.getString(4),
                        retryCount = cursor.getInt(5)
                    )
                )
            }
        }
        list
    }

    /* -------------------- STATS & SUMMARY -------------------- */

    suspend fun getSummaryStats(): SummaryStats = withContext(Dispatchers.IO) {
        var totalBills = 0
        var totalBarcodes = 0
        var totalVerified = 0
        var totalNeedsReview = 0
        var totalScanned = 0
        var totalPending = 0

        val db = readableDatabase

        // Bills count
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_BILL_BATCHES", null).use {
            if (it.moveToFirst()) totalBills = it.getInt(0)
        }

        // Barcode counts
        db.rawQuery(
            """
            SELECT 
                COUNT(*),
                SUM(CASE WHEN verificationStatus = 'VERIFIED' THEN 1 ELSE 0 END),
                SUM(CASE WHEN verificationStatus = 'NEEDS_REVIEW' THEN 1 ELSE 0 END),
                SUM(CASE WHEN scanStatus = 'SCANNED' THEN 1 ELSE 0 END),
                SUM(CASE WHEN scanStatus = 'PENDING' THEN 1 ELSE 0 END)
            FROM $TABLE_BARCODE_RECORDS
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                totalBarcodes = it.getInt(0)
                totalVerified = it.getInt(1)
                totalNeedsReview = it.getInt(2)
                totalScanned = it.getInt(3)
                totalPending = it.getInt(4)
            }
        }

        // Duplicate count
        var totalDuplicates = 0
        db.rawQuery(
            """
            SELECT COUNT(*) FROM (
                SELECT correctedValue FROM $TABLE_BARCODE_RECORDS GROUP BY correctedValue HAVING COUNT(*) > 1
            )
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) totalDuplicates = it.getInt(0)
        }

        SummaryStats(
            totalBills = totalBills,
            totalBarcodes = totalBarcodes,
            totalVerified = totalVerified,
            totalNeedsReview = totalNeedsReview,
            totalScanned = totalScanned,
            totalPending = totalPending,
            totalDuplicates = totalDuplicates
        )
    }

    private fun cursorToBarcodeRecord(cursor: Cursor) = BarcodeRecord(
        id = cursor.getLong(0),
        batchId = cursor.getLong(1),
        originalOcrValue = cursor.getString(2),
        correctedValue = cursor.getString(3),
        pageNumber = cursor.getInt(4),
        confidence = cursor.getFloat(5),
        verificationStatus = cursor.getString(6),
        barcodeFormat = cursor.getString(7),
        scanStatus = cursor.getString(8),
        createdAt = cursor.getLong(9)
    )
}
