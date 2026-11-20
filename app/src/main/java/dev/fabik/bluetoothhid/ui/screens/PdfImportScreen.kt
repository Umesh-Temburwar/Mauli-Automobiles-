package dev.fabik.bluetoothhid.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.PdfDocumentRecord
import dev.fabik.bluetoothhid.ocr.MlKitOcrProvider
import dev.fabik.bluetoothhid.pdf.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVerify: (batchId: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPdfFile by remember { mutableStateOf<File?>(null) }
    var pdfFileName by remember { mutableStateOf("") }
    var totalPages by remember { mutableStateOf(0) }
    val thumbnails = remember { mutableStateListOf<Bitmap>() }
    val selectedPages = remember { mutableStateListOf<Int>() }

    var billNumber by remember {
        mutableStateOf("Bill #${SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())}")
    }
    var batchName by remember {
        mutableStateOf("Batch ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())}")
    }

    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingStatusText by remember { mutableStateOf("") }

    fun processLoadedFile(file: File, originalName: String) {
        scope.launch {
            try {
                isProcessing = true
                processingStatusText = "Loading document pages..."
                thumbnails.clear()
                selectedPages.clear()

                selectedPdfFile = file
                pdfFileName = originalName
                totalPages = PdfImporter.getPageCount(file)

                processingStatusText = "Rendering preview..."
                withContext(Dispatchers.IO) {
                    for (i in 0 until totalPages) {
                        val thumb = PdfImporter.renderPageThumbnail(file, i, targetWidth = 260)
                        withContext(Dispatchers.Main) {
                            thumbnails.add(thumb)
                            selectedPages.add(i) // select all pages by default
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error loading document: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // 1. PDF File Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    isProcessing = true
                    processingStatusText = "Reading document..."
                    val file = PdfImporter.copyOrConvertMediaToInternalPdf(context, uri)
                    val name = PdfImporter.getFileName(context, uri)
                    processLoadedFile(file, name)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
                    isProcessing = false
                }
            }
        }
    }

    // 2. Image Picker Launcher (From Gallery / WhatsApp)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    isProcessing = true
                    processingStatusText = "Converting photo to document..."
                    val file = PdfImporter.copyOrConvertMediaToInternalPdf(context, uri)
                    val name = PdfImporter.getFileName(context, uri)
                    processLoadedFile(file, name)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error loading photo: ${e.message}", Toast.LENGTH_LONG).show()
                    isProcessing = false
                }
            }
        }
    }

    // 3. Camera Capture Launcher (Take Photo of Paper Bill)
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                try {
                    isProcessing = true
                    processingStatusText = "Processing camera bill photo..."
                    val file = PdfImporter.convertBitmapToInternalPdf(context, bitmap, "camera_bill_${System.currentTimeMillis()}")
                    processLoadedFile(file, "Camera_Bill_${SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())}.pdf")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error processing camera photo: ${e.message}", Toast.LENGTH_LONG).show()
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Customer Bill (बिल अपलोड)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (selectedPdfFile == null) {
                // Empty state: Upload options (PDF, Gallery, Camera)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "ग्राहक बिल अपलोड करा (Import Customer Bill)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "PDF, गॅलरीतील फोटो किंवा थेट कॅमेऱ्याने फोटो काढा",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Option 1: Take Photo with Camera
                        Button(
                            onClick = { cameraCaptureLauncher.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Photo of Bill (कॅमेऱ्याने फोटो काढा)", fontWeight = FontWeight.Bold)
                        }

                        // Option 2: Select PDF Document
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1976D2)
                            )
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF File (PDF फाइल निवडा)", fontWeight = FontWeight.Bold)
                        }

                        // Option 3: Choose Image from Gallery
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Image from Gallery (गॅलरी फोटो)", fontWeight = FontWeight.Bold)
                        }

                        if (isProcessing) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator()
                            Text(
                                processingStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                // Document Loaded: Metadata & Thumbnail Selection
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pdfFileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                Text(
                                    "$totalPages Page(s) • ${selectedPages.size} Selected for AI Extraction",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedPdfFile = null
                                    thumbnails.clear()
                                    selectedPages.clear()
                                }
                            ) {
                                Text("Change File")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = billNumber,
                                onValueChange = { billNumber = it },
                                label = { Text("Bill / Invoice No") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = batchName,
                                onValueChange = { batchName = it },
                                label = { Text("Batch Name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page Selection Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select Pages to Process:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row {
                        IconButton(onClick = {
                            if (selectedPages.size == totalPages) {
                                selectedPages.clear()
                            } else {
                                selectedPages.clear()
                                selectedPages.addAll(0 until totalPages)
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    }
                }

                // Thumbnail Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(thumbnails.size) { index ->
                        val isSelected = selectedPages.contains(index)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF1976D2) else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        selectedPages.remove(index)
                                    } else {
                                        selectedPages.add(index)
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = thumbnails[index].asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPages.add(index) else selectedPages.remove(index)
                                        }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Page ${index + 1}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Indicator when AI OCR is running
                AnimatedVisibility(visible = isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { processingProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = processingStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Action Button: Extract Numbers
                Button(
                    onClick = {
                        if (selectedPages.isEmpty()) {
                            Toast.makeText(context, "Please select at least one page", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val pdfFile = selectedPdfFile ?: return@Button

                        scope.launch {
                            try {
                                isProcessing = true
                                val db = MauliDatabase.getInstance(context)

                                // 1. Save PDF Document Record
                                val pdfDocId = db.insertPdfDocument(
                                    PdfDocumentRecord(
                                        filename = pdfFileName,
                                        totalPages = totalPages,
                                        processingStatus = "PROCESSING",
                                        filePath = pdfFile.absolutePath
                                    )
                                )

                                // 2. Save Bill Batch Record
                                val batchId = db.insertBatch(
                                    BillBatch(
                                        batchName = batchName.ifBlank { "Batch $pdfDocId" },
                                        billNumber = billNumber.ifBlank { "Bill #$pdfDocId" },
                                        pdfDocumentId = pdfDocId
                                    )
                                )

                                // 3. Run OCR on each selected page
                                val ocrProvider = MlKitOcrProvider()
                                val extractedRecords = mutableListOf<BarcodeRecord>()

                                for ((index, pageIdx) in selectedPages.withIndex()) {
                                    processingProgress = (index.toFloat() / selectedPages.size)
                                    processingStatusText = "AI reading page ${pageIdx + 1} of $totalPages..."

                                    val highResBitmap = withContext(Dispatchers.IO) {
                                        PdfImporter.renderPageHighRes(pdfFile, pageIdx, scale = 2.0f)
                                    }

                                    val pageResults = ocrProvider.extractBarcodesFromPage(
                                        highResBitmap,
                                        pageNumber = pageIdx + 1
                                    )

                                    for (item in pageResults) {
                                        extractedRecords.add(
                                            BarcodeRecord(
                                                batchId = batchId,
                                                originalOcrValue = item.originalValue,
                                                correctedValue = item.cleanedValue,
                                                pageNumber = item.pageNumber,
                                                confidence = item.confidence,
                                                verificationStatus = if (item.warning == null) "VERIFIED" else "NEEDS_REVIEW",
                                                barcodeFormat = "CODE_128",
                                                scanStatus = "PENDING"
                                            )
                                        )
                                    }

                                    highResBitmap.recycle()
                                }

                                // 4. Save Extracted Barcodes into SQLite DB
                                db.insertBarcodeRecords(extractedRecords)

                                isProcessing = false
                                Toast.makeText(
                                    context,
                                    if (extractedRecords.isNotEmpty())
                                        "Extracted ${extractedRecords.size} barcode numbers!"
                                    else
                                        "Batch created. You can add or verify numbers.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // 5. Navigate to Verification Screen
                                onNavigateToVerify(batchId)

                            } catch (e: Exception) {
                                e.printStackTrace()
                                isProcessing = false
                                Toast.makeText(context, "OCR Extraction error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isProcessing && selectedPages.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isProcessing) "AI Extracting Barcodes..." else "Extract Barcodes with AI (AI ने नंबर काढा)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
