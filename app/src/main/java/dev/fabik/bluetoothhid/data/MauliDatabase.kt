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
        const val DATABASE_VERSION = 3

        // Tables
        const val TABLE_PDF_DOCUMENTS = "pdf_documents"
        const val TABLE_BILL_BATCHES = "bill_batches"
        const val TABLE_BARCODE_RECORDS = "barcode_records"
        const val TABLE_SCAN_HISTORY = "scan_history"
        const val TABLE_MASTER_PARTS = "master_parts"
        const val TABLE_CUSTOMER_SCANS = "customer_scans"
        const val TABLE_PRODUCTS = "products"
        const val TABLE_PRODUCT_IMAGES = "product_images"

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

        // Create Complete Products and Dedicated Images tables
        createProductsAndImagesTables(db)
        seedCompleteCatalogue(db)
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

    private fun createProductsAndImagesTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PRODUCTS (
                id INTEGER PRIMARY KEY,
                product_name TEXT NOT NULL,
                product_description TEXT,
                manufacturer TEXT NOT NULL,
                brand TEXT NOT NULL,
                oem_part_number TEXT NOT NULL,
                alternative_part_number TEXT,
                superseded_part_number TEXT,
                original_barcode TEXT,
                barcode_type TEXT DEFAULT 'EAN_13',
                gtin TEXT,
                ean13 TEXT,
                internal_barcode TEXT,
                category TEXT NOT NULL,
                subcategory TEXT,
                vehicle_model TEXT NOT NULL,
                manufacturing_year TEXT,
                mrp REAL NOT NULL,
                selling_price REAL NOT NULL,
                currency TEXT DEFAULT 'INR',
                hsn_code TEXT,
                gst_rate REAL,
                unit TEXT DEFAULT 'PCS',
                image_url TEXT,
                thumbnail_url TEXT,
                local_image_path TEXT,
                official_product_url TEXT,
                image_verification_status TEXT,
                barcode_verification_status TEXT,
                data_source TEXT,
                last_verified_at TEXT,
                created_at TEXT,
                updated_at TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PRODUCT_IMAGES (
                image_id INTEGER PRIMARY KEY,
                product_id INTEGER NOT NULL,
                product_name TEXT NOT NULL,
                oem_part_number TEXT NOT NULL,
                barcode TEXT,
                brand TEXT NOT NULL,
                image_url TEXT NOT NULL,
                thumbnail_url TEXT,
                official_product_page_url TEXT,
                image_source_name TEXT,
                image_format TEXT,
                image_file_size INTEGER,
                is_primary_image INTEGER DEFAULT 1,
                is_verified INTEGER DEFAULT 1,
                verification_method TEXT,
                local_image_path TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prod_barcode ON $TABLE_PRODUCTS (original_barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prod_partno ON $TABLE_PRODUCTS (oem_part_number)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prod_brand ON $TABLE_PRODUCTS (brand)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prod_category ON $TABLE_PRODUCTS (category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_prod_model ON $TABLE_PRODUCTS (vehicle_model)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_img_prodid ON $TABLE_PRODUCT_IMAGES (product_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_img_partno ON $TABLE_PRODUCT_IMAGES (oem_part_number)")
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

    private fun seedCompleteCatalogue(db: SQLiteDatabase) {
        try {
            db.beginTransaction()
            for (p in CompleteCatalogueSeeds.PRODUCTS) {
                val cv = ContentValues().apply {
                    put("id", p.id)
                    put("product_name", p.productName)
                    put("product_description", p.productDescription)
                    put("manufacturer", p.manufacturer)
                    put("brand", p.brand)
                    put("oem_part_number", p.oemPartNumber)
                    put("alternative_part_number", p.alternativePartNumber)
                    put("superseded_part_number", p.supersededPartNumber)
                    put("original_barcode", p.originalBarcode)
                    put("barcode_type", p.barcodeType)
                    put("gtin", p.gtin)
                    put("ean13", p.ean13)
                    put("internal_barcode", p.internalBarcode)
                    put("category", p.category)
                    put("subcategory", p.subcategory)
                    put("vehicle_model", p.vehicleModel)
                    put("manufacturing_year", p.manufacturingYear)
                    put("mrp", p.mrp)
                    put("selling_price", p.sellingPrice)
                    put("currency", p.currency)
                    put("hsn_code", p.hsnCode)
                    put("gst_rate", p.gstRate)
                    put("unit", p.unit)
                    put("image_url", p.imageUrl)
                    put("thumbnail_url", p.thumbnailUrl)
                    put("local_image_path", p.localImagePath)
                    put("official_product_url", p.officialProductUrl)
                    put("image_verification_status", p.imageVerificationStatus)
                    put("barcode_verification_status", p.barcodeVerificationStatus)
                    put("data_source", p.dataSource)
                    put("last_verified_at", p.lastVerifiedAt)
                    put("created_at", System.currentTimeMillis().toString())
                    put("updated_at", System.currentTimeMillis().toString())
                }
                db.insertWithOnConflict(TABLE_PRODUCTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            for (img in CompleteCatalogueSeeds.IMAGES) {
                val cv = ContentValues().apply {
                    put("image_id", img.imageId)
                    put("product_id", img.productId)
                    put("product_name", img.productName)
                    put("oem_part_number", img.oemPartNumber)
                    put("barcode", img.barcode)
                    put("brand", img.brand)
                    put("image_url", img.imageUrl)
                    put("thumbnail_url", img.thumbnailUrl)
                    put("official_product_page_url", img.officialProductPageUrl)
                    put("image_source_name", img.imageSourceName)
                    put("image_format", img.imageFormat)
                    put("image_file_size", img.imageFileSize)
                    put("is_primary_image", if (img.isPrimaryImage) 1 else 0)
                    put("is_verified", if (img.isVerified) 1 else 0)
                    put("verification_method", img.verificationMethod)
                    put("local_image_path", img.localImagePath)
                }
                db.insertWithOnConflict(TABLE_PRODUCT_IMAGES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
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
        if (oldVersion < 3) {
            createProductsAndImagesTables(db)
            seedCompleteCatalogue(db)
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
                return@withContext MasterSparePart(
                    id = cursor.getLong(0),
                    brand = cursor.getString(1),
                    partNumber = cursor.getString(2),
                    productName = cursor.getString(3),
                    barcode = cursor.getString(4),
                    category = cursor.getString(5),
                    compatibleModels = cursor.getString(6),
                    mrp = cursor.getDouble(7)
                )
            }
        }

        // Check TABLE_PRODUCTS for complete catalogue
        readableDatabase.rawQuery(
            "SELECT id, brand, oem_part_number, product_name, original_barcode, category, vehicle_model, mrp FROM $TABLE_PRODUCTS WHERE original_barcode = ? OR gtin = ? OR ean13 = ? LIMIT 1",
            arrayOf(cleanBarcode, cleanBarcode, cleanBarcode)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext MasterSparePart(
                    id = cursor.getLong(0),
                    brand = cursor.getString(1),
                    partNumber = cursor.getString(2),
                    productName = cursor.getString(3),
                    barcode = cursor.getString(4),
                    category = cursor.getString(5),
                    compatibleModels = cursor.getString(6),
                    mrp = cursor.getDouble(7)
                )
            }
        }
        null
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

    /* -------------------- COMPLETE PRODUCTS & IMAGES CATALOGUE -------------------- */

    suspend fun ensureCompleteCatalogueSeeded() = withContext(Dispatchers.IO) {
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_PRODUCTS", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            seedCompleteCatalogue(writableDatabase)
        }
    }

    suspend fun findProductByBarcode(barcode: String): MasterProduct? = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        readableDatabase.rawQuery(
            """
            SELECT id, product_name, product_description, manufacturer, brand, oem_part_number, 
                   alternative_part_number, superseded_part_number, original_barcode, barcode_type, 
                   gtin, ean13, internal_barcode, category, subcategory, vehicle_model, manufacturing_year, 
                   mrp, selling_price, currency, hsn_code, gst_rate, unit, image_url, thumbnail_url, 
                   local_image_path, official_product_url, image_verification_status, barcode_verification_status, 
                   data_source, last_verified_at 
            FROM $TABLE_PRODUCTS 
            WHERE original_barcode = ? OR gtin = ? OR ean13 = ? OR internal_barcode = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(cleanBarcode, cleanBarcode, cleanBarcode, cleanBarcode)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToProduct(cursor) else null
        }
    }

    suspend fun findProductByPartNumber(partNo: String): MasterProduct? = withContext(Dispatchers.IO) {
        val cleanPartNo = partNo.trim().replace(" ", "").uppercase()
        readableDatabase.rawQuery(
            """
            SELECT id, product_name, product_description, manufacturer, brand, oem_part_number, 
                   alternative_part_number, superseded_part_number, original_barcode, barcode_type, 
                   gtin, ean13, internal_barcode, category, subcategory, vehicle_model, manufacturing_year, 
                   mrp, selling_price, currency, hsn_code, gst_rate, unit, image_url, thumbnail_url, 
                   local_image_path, official_product_url, image_verification_status, barcode_verification_status, 
                   data_source, last_verified_at 
            FROM $TABLE_PRODUCTS 
            WHERE UPPER(REPLACE(oem_part_number, ' ', '')) = ? 
            LIMIT 1
            """.trimIndent(),
            arrayOf(cleanPartNo)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursorToProduct(cursor) else null
        }
    }

    suspend fun searchProducts(
        query: String,
        brandFilter: String? = null,
        categoryFilter: String? = null,
        modelFilter: String? = null,
        withImagesOnly: Boolean = false
    ): List<MasterProduct> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MasterProduct>()
        val cleanQuery = "%${query.trim()}%"
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (query.isNotBlank()) {
            whereClauses.add("(product_name LIKE ? OR oem_part_number LIKE ? OR original_barcode LIKE ? OR vehicle_model LIKE ?)")
            args.add(cleanQuery)
            args.add(cleanQuery)
            args.add(cleanQuery)
            args.add(cleanQuery)
        }

        if (!brandFilter.isNullOrBlank() && brandFilter != "All") {
            whereClauses.add("brand = ?")
            args.add(brandFilter)
        }

        if (!categoryFilter.isNullOrBlank() && categoryFilter != "All") {
            whereClauses.add("category = ?")
            args.add(categoryFilter)
        }

        if (!modelFilter.isNullOrBlank() && modelFilter != "All") {
            whereClauses.add("vehicle_model LIKE ?")
            args.add("%$modelFilter%")
        }

        if (withImagesOnly) {
            whereClauses.add("(image_url IS NOT NULL AND image_url != '')")
        }

        val whereSql = if (whereClauses.isNotEmpty()) "WHERE " + whereClauses.joinToString(" AND ") else ""
        val sql = """
            SELECT id, product_name, product_description, manufacturer, brand, oem_part_number, 
                   alternative_part_number, superseded_part_number, original_barcode, barcode_type, 
                   gtin, ean13, internal_barcode, category, subcategory, vehicle_model, manufacturing_year, 
                   mrp, selling_price, currency, hsn_code, gst_rate, unit, image_url, thumbnail_url, 
                   local_image_path, official_product_url, image_verification_status, barcode_verification_status, 
                   data_source, last_verified_at 
            FROM $TABLE_PRODUCTS 
            $whereSql
            ORDER BY brand ASC, product_name ASC
            LIMIT 100
        """.trimIndent()

        readableDatabase.rawQuery(sql, if (args.isNotEmpty()) args.toTypedArray() else null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProduct(cursor))
            }
        }
        list
    }

    suspend fun getProductImages(productId: Long): List<ProductImageRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ProductImageRecord>()
        readableDatabase.rawQuery(
            """
            SELECT image_id, product_id, product_name, oem_part_number, barcode, brand, 
                   image_url, thumbnail_url, official_product_page_url, image_source_name, 
                   image_format, image_file_size, is_primary_image, is_verified, 
                   verification_method, local_image_path 
            FROM $TABLE_PRODUCT_IMAGES 
            WHERE product_id = ? 
            ORDER BY is_primary_image DESC, image_id ASC
            """.trimIndent(),
            arrayOf(productId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProductImage(cursor))
            }
        }
        list
    }

    suspend fun getAllProducts(limit: Int = 100, offset: Int = 0): List<MasterProduct> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MasterProduct>()
        readableDatabase.rawQuery(
            """
            SELECT id, product_name, product_description, manufacturer, brand, oem_part_number, 
                   alternative_part_number, superseded_part_number, original_barcode, barcode_type, 
                   gtin, ean13, internal_barcode, category, subcategory, vehicle_model, manufacturing_year, 
                   mrp, selling_price, currency, hsn_code, gst_rate, unit, image_url, thumbnail_url, 
                   local_image_path, official_product_url, image_verification_status, barcode_verification_status, 
                   data_source, last_verified_at 
            FROM $TABLE_PRODUCTS 
            ORDER BY brand ASC, product_name ASC 
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(limit.toString(), offset.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProduct(cursor))
            }
        }
        list
    }

    suspend fun getCatalogueStats(): CatalogueStats = withContext(Dispatchers.IO) {
        var total = 0
        var hero = 0
        var honda = 0
        var withImages = 0
        var verifiedBc = 0
        var categoriesCount = 0

        val db = readableDatabase
        db.rawQuery(
            """
            SELECT 
                COUNT(*),
                SUM(CASE WHEN brand LIKE '%Hero%' THEN 1 ELSE 0 END),
                SUM(CASE WHEN brand = 'Honda' THEN 1 ELSE 0 END),
                SUM(CASE WHEN image_url IS NOT NULL AND image_url != '' THEN 1 ELSE 0 END),
                SUM(CASE WHEN original_barcode IS NOT NULL AND original_barcode != '' THEN 1 ELSE 0 END),
                COUNT(DISTINCT category)
            FROM $TABLE_PRODUCTS
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0)
                hero = cursor.getInt(1)
                honda = cursor.getInt(2)
                withImages = cursor.getInt(3)
                verifiedBc = cursor.getInt(4)
                categoriesCount = cursor.getInt(5)
            }
        }

        CatalogueStats(
            totalProducts = total,
            heroProducts = hero,
            hondaProducts = honda,
            productsWithImages = withImages,
            verifiedBarcodes = verifiedBc,
            totalCategories = categoriesCount
        )
    }

    suspend fun insertProduct(p: MasterProduct): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("product_name", p.productName)
            put("product_description", p.productDescription)
            put("manufacturer", p.manufacturer)
            put("brand", p.brand)
            put("oem_part_number", p.oemPartNumber)
            put("alternative_part_number", p.alternativePartNumber)
            put("superseded_part_number", p.supersededPartNumber)
            put("original_barcode", p.originalBarcode)
            put("barcode_type", p.barcodeType)
            put("gtin", p.gtin)
            put("ean13", p.ean13)
            put("internal_barcode", p.internalBarcode)
            put("category", p.category)
            put("subcategory", p.subcategory)
            put("vehicle_model", p.vehicleModel)
            put("manufacturing_year", p.manufacturingYear)
            put("mrp", p.mrp)
            put("selling_price", p.sellingPrice)
            put("currency", p.currency)
            put("hsn_code", p.hsnCode)
            put("gst_rate", p.gstRate)
            put("unit", p.unit)
            put("image_url", p.imageUrl)
            put("thumbnail_url", p.thumbnailUrl)
            put("local_image_path", p.localImagePath)
            put("official_product_url", p.officialProductUrl)
            put("image_verification_status", p.imageVerificationStatus)
            put("barcode_verification_status", p.barcodeVerificationStatus)
            put("data_source", p.dataSource)
            put("last_verified_at", p.lastVerifiedAt)
            put("created_at", System.currentTimeMillis().toString())
            put("updated_at", System.currentTimeMillis().toString())
        }
        writableDatabase.insert(TABLE_PRODUCTS, null, cv)
    }

    suspend fun insertProductImage(img: ProductImageRecord): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("product_id", img.productId)
            put("product_name", img.productName)
            put("oem_part_number", img.oemPartNumber)
            put("barcode", img.barcode)
            put("brand", img.brand)
            put("image_url", img.imageUrl)
            put("thumbnail_url", img.thumbnailUrl)
            put("official_product_page_url", img.officialProductPageUrl)
            put("image_source_name", img.imageSourceName)
            put("image_format", img.imageFormat)
            put("image_file_size", img.imageFileSize)
            put("is_primary_image", if (img.isPrimaryImage) 1 else 0)
            put("is_verified", if (img.isVerified) 1 else 0)
            put("verification_method", img.verificationMethod)
            put("local_image_path", img.localImagePath)
        }
        writableDatabase.insert(TABLE_PRODUCT_IMAGES, null, cv)
    }

    private fun cursorToProduct(cursor: Cursor) = MasterProduct(
        id = cursor.getLong(0),
        productName = cursor.getString(1),
        productDescription = cursor.getString(2) ?: "",
        manufacturer = cursor.getString(3),
        brand = cursor.getString(4),
        oemPartNumber = cursor.getString(5),
        alternativePartNumber = cursor.getString(6) ?: "",
        supersededPartNumber = cursor.getString(7) ?: "",
        originalBarcode = cursor.getString(8) ?: "",
        barcodeType = cursor.getString(9) ?: "EAN_13",
        gtin = cursor.getString(10) ?: "",
        ean13 = cursor.getString(11) ?: "",
        internalBarcode = cursor.getString(12) ?: "",
        category = cursor.getString(13),
        subcategory = cursor.getString(14) ?: "",
        vehicleModel = cursor.getString(15),
        manufacturingYear = cursor.getString(16) ?: "",
        mrp = cursor.getDouble(17),
        sellingPrice = cursor.getDouble(18),
        currency = cursor.getString(19) ?: "INR",
        hsnCode = cursor.getString(20) ?: "",
        gstRate = cursor.getDouble(21),
        unit = cursor.getString(22) ?: "PCS",
        imageUrl = cursor.getString(23) ?: "",
        thumbnailUrl = cursor.getString(24) ?: "",
        localImagePath = cursor.getString(25) ?: "",
        officialProductUrl = cursor.getString(26) ?: "",
        imageVerificationStatus = cursor.getString(27) ?: "",
        barcodeVerificationStatus = cursor.getString(28) ?: "",
        dataSource = cursor.getString(29) ?: "",
        lastVerifiedAt = cursor.getString(30) ?: ""
    )

    private fun cursorToProductImage(cursor: Cursor) = ProductImageRecord(
        imageId = cursor.getLong(0),
        productId = cursor.getLong(1),
        productName = cursor.getString(2),
        oemPartNumber = cursor.getString(3),
        barcode = cursor.getString(4) ?: "",
        brand = cursor.getString(5),
        imageUrl = cursor.getString(6),
        thumbnailUrl = cursor.getString(7) ?: "",
        officialProductPageUrl = cursor.getString(8) ?: "",
        imageSourceName = cursor.getString(9) ?: "",
        imageFormat = cursor.getString(10) ?: "PNG",
        imageFileSize = cursor.getLong(11),
        isPrimaryImage = cursor.getInt(12) == 1,
        isVerified = cursor.getInt(13) == 1,
        verificationMethod = cursor.getString(14) ?: "",
        localImagePath = cursor.getString(15) ?: ""
    )
}
