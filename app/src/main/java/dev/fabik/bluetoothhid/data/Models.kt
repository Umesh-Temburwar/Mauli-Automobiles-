package dev.fabik.bluetoothhid.data

data class PdfDocumentRecord(
    val id: Long = 0,
    val filename: String,
    val importDate: Long = System.currentTimeMillis(),
    val totalPages: Int,
    val processingStatus: String = "IMPORTED",
    val filePath: String = ""
)

data class BillBatch(
    val id: Long = 0,
    val batchName: String,
    val billNumber: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pdfDocumentId: Long = 0
)

data class BarcodeRecord(
    val id: Long = 0,
    val batchId: Long,
    val originalOcrValue: String, // String preserves leading zeros like "00123..."
    val correctedValue: String,  // String preserves leading zeros
    val pageNumber: Int,
    val confidence: Float = 1.0f,
    val verificationStatus: String = "VERIFIED", // "VERIFIED" or "NEEDS_REVIEW"
    val barcodeFormat: String = "CODE_128",
    val scanStatus: String = "PENDING",          // "PENDING" or "SCANNED"
    val createdAt: Long = System.currentTimeMillis()
)

data class ScanHistoryRecord(
    val id: Long = 0,
    val barcodeRecordId: Long = 0,
    val scannedValue: String,
    val scanTime: Long = System.currentTimeMillis(),
    val transmissionStatus: String = "SENT",     // "SENT", "PENDING", "FAILED"
    val retryCount: Int = 0
)

data class SummaryStats(
    val totalBills: Int = 0,
    val totalBarcodes: Int = 0,
    val totalVerified: Int = 0,
    val totalNeedsReview: Int = 0,
    val totalScanned: Int = 0,
    val totalPending: Int = 0,
    val totalDuplicates: Int = 0
)

data class MasterSparePart(
    val id: Long = 0,
    val brand: String,
    val partNumber: String,
    val productName: String,
    val barcode: String,
    val category: String,
    val compatibleModels: String,
    val mrp: Double = 0.0
)

data class CustomerScanRecord(
    val id: Long = 0,
    val barcode: String,
    val productName: String,
    val partNumber: String,
    val brand: String,
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    val scanTime: Long = System.currentTimeMillis(),
    val dateStr: String, // "yyyy-MM-dd"
    val customerName: String = "",
    val notes: String = ""
)

data class CustomerScansSummary(
    val totalRecords: Int = 0,
    val totalQuantity: Int = 0,
    val totalAmount: Double = 0.0,
    val uniqueBarcodes: Int = 0
)

data class MasterProduct(
    val id: Long = 0,
    val productName: String,
    val productDescription: String = "",
    val manufacturer: String,
    val brand: String,
    val oemPartNumber: String,
    val alternativePartNumber: String = "",
    val supersededPartNumber: String = "",
    val originalBarcode: String,
    val barcodeType: String = "EAN_13",
    val gtin: String = "",
    val ean13: String = "",
    val internalBarcode: String = "",
    val category: String,
    val subcategory: String = "",
    val vehicleModel: String,
    val manufacturingYear: String = "",
    val mrp: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val currency: String = "INR",
    val hsnCode: String = "",
    val gstRate: Double = 28.0,
    val unit: String = "PCS",
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val localImagePath: String = "",
    val officialProductUrl: String = "",
    val imageVerificationStatus: String = "Official Verified",
    val barcodeVerificationStatus: String = "Verified EAN-13",
    val dataSource: String = "",
    val lastVerifiedAt: String = ""
)

data class ProductImageRecord(
    val imageId: Long = 0,
    val productId: Long,
    val productName: String,
    val oemPartNumber: String,
    val barcode: String,
    val brand: String,
    val imageUrl: String,
    val thumbnailUrl: String = "",
    val officialProductPageUrl: String = "",
    val imageSourceName: String = "",
    val imageFormat: String = "PNG",
    val imageFileSize: Long = 0,
    val isPrimaryImage: Boolean = true,
    val isVerified: Boolean = true,
    val verificationMethod: String = "EXACT_OEM_MATCH",
    val localImagePath: String = ""
)

data class CatalogueStats(
    val totalProducts: Int = 0,
    val heroProducts: Int = 0,
    val hondaProducts: Int = 0,
    val productsWithImages: Int = 0,
    val verifiedBarcodes: Int = 0,
    val totalCategories: Int = 0
)
