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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
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

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    isProcessing = true
                    processingStatusText = "Loading PDF document..."
                    thumbnails.clear()
                    selectedPages.clear()

                    val file = PdfImporter.copyPdfToInternalStorage(context, uri)
                    selectedPdfFile = file
                    pdfFileName = PdfImporter.getFileName(context, uri)
                    totalPages = PdfImporter.getPageCount(file)

                    processingStatusText = "Rendering thumbnails..."
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
                    Toast.makeText(context, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Handwritten Bill PDF", fontWeight = FontWeight.Bold) },
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
                // Empty state: Upload prompt
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Select Handwritten Bill PDF",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "ग्राहक बिल PDF निवडा (Single or Multi-Page)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Browse PDF Files")
                            }
                        }
                    }
                }
            } else {
                // PDF Loaded State
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pdfFileName,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "$totalPages Pages • ${selectedPages.size} Selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bill details inputs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billNumber,
                        onValueChange = { billNumber = it },
                        label = { Text("Bill Number") },
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

                Spacer(modifier = Modifier.height(12.dp))

                // Page selection toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Pages to Extract (${selectedPages.size}/$totalPages):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (selectedPages.size == totalPages) "Deselect All" else "Select All",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            if (selectedPages.size == totalPages) {
                                selectedPages.clear()
                            } else {
                                selectedPages.clear()
                                for (i in 0 until totalPages) selectedPages.add(i)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Thumbnails Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(thumbnails.size) { pageIdx ->
                        val isSelected = selectedPages.contains(pageIdx)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (isSelected) selectedPages.remove(pageIdx)
                                    else selectedPages.add(pageIdx)
                                }
                        ) {
                            Image(
                                bitmap = thumbnails[pageIdx].asImageBitmap(),
                                contentDescription = "Page ${pageIdx + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                contentScale = ContentScale.Crop
                            )

                            // Page number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "P.${pageIdx + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Selection Checkbox Badge
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Indicator when OCR is running
                AnimatedVisibility(visible = isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                                    "Extracted ${extractedRecords.size} barcode numbers!",
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
