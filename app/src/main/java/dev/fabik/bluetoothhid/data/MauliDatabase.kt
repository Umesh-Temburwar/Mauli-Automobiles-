package dev.fabik.bluetoothhid.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MauliDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "mauli_barcode.db"
        const val DATABASE_VERSION = 2

        // Tables
        const val TABLE_PDF_DOCUMENTS = "pdf_documents"
        const val TABLE_BILL_BATCHES = "bill_batches"
        const val TABLE_BARCODE_RECORDS = "barcode_records"
        const val TABLE_SCAN_HISTORY = "scan_history"
        const val TABLE_MASTER_PARTS = "master_parts"
        const val TABLE_CUSTOMER_SCANS = "customer_scans"

        fun getTodayDateString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

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

        // Create Master Parts and Customer Scans tables
        createMasterAndCustomerTables(db)
        seedMasterPartsInternal(db)
    }

    private fun createMasterAndCustomerTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MASTER_PARTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                brand TEXT NOT NULL,
                partNumber TEXT NOT NULL,
                productName TEXT NOT NULL,
                barcode TEXT NOT NULL,
                category TEXT NOT NULL,
                compatibleModels TEXT NOT NULL,
                mrp REAL NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CUSTOMER_SCANS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT NOT NULL,
                productName TEXT NOT NULL,
                partNumber TEXT NOT NULL,
                brand TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 1,
                mrp REAL NOT NULL DEFAULT 0.0,
                scanTime INTEGER NOT NULL,
                dateStr TEXT NOT NULL,
                customerName TEXT,
                notes TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_master_barcode ON $TABLE_MASTER_PARTS (barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_master_part_num ON $TABLE_MASTER_PARTS (partNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cust_scans_date ON $TABLE_CUSTOMER_SCANS (dateStr)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cust_scans_barcode ON $TABLE_CUSTOMER_SCANS (barcode)")
    }

    private fun seedMasterPartsInternal(db: SQLiteDatabase) {
        try {
            db.beginTransaction()
            for (part in MasterCatalogueSeeds.SEED_PARTS) {
                val cv = ContentValues().apply {
                    put("brand", part.brand)
                    put("partNumber", part.partNumber)
                    put("productName", part.productName)
                    put("barcode", part.barcode)
                    put("category", part.category)
                    put("compatibleModels", part.compatibleModels)
                    put("mrp", part.mrp)
                }
                db.insertWithOnConflict(TABLE_MASTER_PARTS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createMasterAndCustomerTables(db)
            seedMasterPartsInternal(db)
        }
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

    suspend fun insertBarcodeRecord(record: BarcodeRecord): Long =
        insertBarcodeRecords(listOf(record)).firstOrNull() ?: -1L

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

    /* -------------------- MASTER SPARE PARTS -------------------- */

    suspend fun ensureMasterCatalogueSeeded() = withContext(Dispatchers.IO) {
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_MASTER_PARTS", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            seedMasterPartsInternal(writableDatabase)
        }
    }

    suspend fun findMasterPartByBarcode(barcode: String): MasterSparePart? = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        readableDatabase.rawQuery(
            "SELECT id, brand, partNumber, productName, barcode, category, compatibleModels, mrp FROM $TABLE_MASTER_PARTS WHERE barcode = ? LIMIT 1",
            arrayOf(cleanBarcode)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                MasterSparePart(
                    id = cursor.getLong(0),
                    brand = cursor.getString(1),
                    partNumber = cursor.getString(2),
                    productName = cursor.getString(3),
                    barcode = cursor.getString(4),
                    category = cursor.getString(5),
                    compatibleModels = cursor.getString(6),
                    mrp = cursor.getDouble(7)
                )
            } else null
        }
    }

    suspend fun searchMasterParts(query: String): List<MasterSparePart> = withContext(Dispatchers.IO) {
        val cleanQuery = "%${query.trim()}%"
        val list = mutableListOf<MasterSparePart>()
        readableDatabase.rawQuery(
            """
            SELECT id, brand, partNumber, productName, barcode, category, compatibleModels, mrp 
            FROM $TABLE_MASTER_PARTS 
            WHERE productName LIKE ? OR partNumber LIKE ? OR barcode LIKE ? OR compatibleModels LIKE ?
            ORDER BY brand ASC, productName ASC
            LIMIT 50
            """.trimIndent(),
            arrayOf(cleanQuery, cleanQuery, cleanQuery, cleanQuery)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    MasterSparePart(
                        id = cursor.getLong(0),
                        brand = cursor.getString(1),
                        partNumber = cursor.getString(2),
                        productName = cursor.getString(3),
                        barcode = cursor.getString(4),
                        category = cursor.getString(5),
                        compatibleModels = cursor.getString(6),
                        mrp = cursor.getDouble(7)
                    )
                )
            }
        }
        list
    }

    suspend fun getAllMasterParts(): List<MasterSparePart> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MasterSparePart>()
        readableDatabase.rawQuery(
            "SELECT id, brand, partNumber, productName, barcode, category, compatibleModels, mrp FROM $TABLE_MASTER_PARTS ORDER BY brand ASC, productName ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    MasterSparePart(
                        id = cursor.getLong(0),
                        brand = cursor.getString(1),
                        partNumber = cursor.getString(2),
                        productName = cursor.getString(3),
                        barcode = cursor.getString(4),
                        category = cursor.getString(5),
                        compatibleModels = cursor.getString(6),
                        mrp = cursor.getDouble(7)
                    )
                )
            }
        }
        list
    }

    suspend fun insertMasterPart(part: MasterSparePart): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("brand", part.brand)
            put("partNumber", part.partNumber)
            put("productName", part.productName)
            put("barcode", part.barcode)
            put("category", part.category)
            put("compatibleModels", part.compatibleModels)
            put("mrp", part.mrp)
        }
        writableDatabase.insert(TABLE_MASTER_PARTS, null, values)
    }

    /* -------------------- CUSTOMER SCANS & BILLING -------------------- */

    suspend fun addCustomerScan(
        barcode: String,
        customerName: String = "",
        quantityToAdd: Int = 1
    ): CustomerScanRecord = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        val todayStr = getTodayDateString()
        val db = writableDatabase

        // Check if item already exists for today's scans -> If yes, increment quantity!
        var existingRecord: CustomerScanRecord? = null
        db.rawQuery(
            "SELECT id, barcode, productName, partNumber, brand, quantity, mrp, scanTime, dateStr, customerName, notes FROM $TABLE_CUSTOMER_SCANS WHERE barcode = ? AND dateStr = ? LIMIT 1",
            arrayOf(cleanBarcode, todayStr)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                existingRecord = CustomerScanRecord(
                    id = cursor.getLong(0),
                    barcode = cursor.getString(1),
                    productName = cursor.getString(2),
                    partNumber = cursor.getString(3),
                    brand = cursor.getString(4),
                    quantity = cursor.getInt(5),
                    mrp = cursor.getDouble(6),
                    scanTime = cursor.getLong(7),
                    dateStr = cursor.getString(8),
                    customerName = cursor.getString(9) ?: "",
                    notes = cursor.getString(10) ?: ""
                )
            }
        }

        if (existingRecord != null) {
            val updatedQty = existingRecord!!.quantity + quantityToAdd
            val values = ContentValues().apply {
                put("quantity", updatedQty)
                put("scanTime", System.currentTimeMillis())
                if (customerName.isNotBlank()) put("customerName", customerName)
            }
            db.update(TABLE_CUSTOMER_SCANS, values, "id = ?", arrayOf(existingRecord!!.id.toString()))
            return@withContext existingRecord!!.copy(
                quantity = updatedQty,
                scanTime = System.currentTimeMillis(),
                customerName = if (customerName.isNotBlank()) customerName else existingRecord!!.customerName
            )
        }

        // New item scan -> lookup in Master Spare Parts database
        val masterPart = findMasterPartByBarcode(cleanBarcode)
        val prodName = masterPart?.productName ?: "Spare Part ($cleanBarcode)"
        val partNum = masterPart?.partNumber ?: cleanBarcode
        val brandName = masterPart?.brand ?: "Hero / Honda"
        val partMrp = masterPart?.mrp ?: 0.0

        val newValues = ContentValues().apply {
            put("barcode", cleanBarcode)
            put("productName", prodName)
            put("partNumber", partNum)
            put("brand", brandName)
            put("quantity", quantityToAdd)
            put("mrp", partMrp)
            put("scanTime", System.currentTimeMillis())
            put("dateStr", todayStr)
            put("customerName", customerName)
            put("notes", masterPart?.compatibleModels ?: "")
        }

        val newId = db.insert(TABLE_CUSTOMER_SCANS, null, newValues)
        CustomerScanRecord(
            id = newId,
            barcode = cleanBarcode,
            productName = prodName,
            partNumber = partNum,
            brand = brandName,
            quantity = quantityToAdd,
            mrp = partMrp,
            scanTime = System.currentTimeMillis(),
            dateStr = todayStr,
            customerName = customerName,
            notes = masterPart?.compatibleModels ?: ""
        )
    }

    suspend fun getTodayCustomerScans(dateStr: String = getTodayDateString()): List<CustomerScanRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CustomerScanRecord>()
        readableDatabase.rawQuery(
            """
            SELECT id, barcode, productName, partNumber, brand, quantity, mrp, scanTime, dateStr, customerName, notes 
            FROM $TABLE_CUSTOMER_SCANS 
            WHERE dateStr = ? 
            ORDER BY scanTime DESC
            """.trimIndent(),
            arrayOf(dateStr)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    CustomerScanRecord(
                        id = cursor.getLong(0),
                        barcode = cursor.getString(1),
                        productName = cursor.getString(2),
                        partNumber = cursor.getString(3),
                        brand = cursor.getString(4),
                        quantity = cursor.getInt(5),
                        mrp = cursor.getDouble(6),
                        scanTime = cursor.getLong(7),
                        dateStr = cursor.getString(8),
                        customerName = cursor.getString(9) ?: "",
                        notes = cursor.getString(10) ?: ""
                    )
                )
            }
        }
        list
    }

    suspend fun updateCustomerScanQty(id: Long, newQty: Int) = withContext(Dispatchers.IO) {
        if (newQty <= 0) {
            deleteCustomerScan(id)
        } else {
            val values = ContentValues().apply {
                put("quantity", newQty)
            }
            writableDatabase.update(TABLE_CUSTOMER_SCANS, values, "id = ?", arrayOf(id.toString()))
        }
    }

    suspend fun deleteCustomerScan(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_CUSTOMER_SCANS, "id = ?", arrayOf(id.toString()))
    }

    suspend fun clearCustomerScans(dateStr: String = getTodayDateString()) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_CUSTOMER_SCANS, "dateStr = ?", arrayOf(dateStr))
    }

    suspend fun getCustomerScansSummary(dateStr: String = getTodayDateString()): CustomerScansSummary = withContext(Dispatchers.IO) {
        var totalRecords = 0
        var totalQuantity = 0
        var totalAmount = 0.0
        var uniqueBarcodes = 0

        readableDatabase.rawQuery(
            """
            SELECT 
                COUNT(*), 
                COALESCE(SUM(quantity), 0), 
                COALESCE(SUM(quantity * mrp), 0.0), 
                COUNT(DISTINCT barcode) 
            FROM $TABLE_CUSTOMER_SCANS 
            WHERE dateStr = ?
            """.trimIndent(),
            arrayOf(dateStr)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                totalRecords = cursor.getInt(0)
                totalQuantity = cursor.getInt(1)
                totalAmount = cursor.getDouble(2)
                uniqueBarcodes = cursor.getInt(3)
            }
        }

        CustomerScansSummary(
            totalRecords = totalRecords,
            totalQuantity = totalQuantity,
            totalAmount = totalAmount,
            uniqueBarcodes = uniqueBarcodes
        )
    }
}
