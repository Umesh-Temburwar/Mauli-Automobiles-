package dev.fabik.bluetoothhid.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.fabik.bluetoothhid.generator.BarcodePdfGenerator
import dev.fabik.bluetoothhid.pdf.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodePdfScreen(
    batchId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToBatchScanner: (batchId: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val db = remember { MauliDatabase.getInstance(context) }

    var currentBatchId by remember { mutableStateOf(batchId) }
    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val allBatches = remember { mutableStateListOf<BillBatch>() }
    var showBatchDropdown by remember { mutableStateOf(false) }

    var generatedPdfFile by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    val pagePreviews = remember { mutableStateListOf<Bitmap>() }

    fun generatePdfForBatch(targetId: Long = currentBatchId) {
        scope.launch {
            try {
                isGenerating = true
                val batchList = db.getAllBatches()
                allBatches.clear()
                allBatches.addAll(batchList)

                val loadedBatch = if (targetId > 0) db.getBatchById(targetId) else batchList.firstOrNull()
                batch = loadedBatch
                val activeBatchId = loadedBatch?.id ?: 0L
                currentBatchId = activeBatchId

                if (activeBatchId > 0 && loadedBatch != null) {
                    val records = db.getBarcodesForBatch(activeBatchId)
                    if (records.isNotEmpty()) {
                        val pdfFile = BarcodePdfGenerator.generateBarcodePdf(context, loadedBatch, records)
                        generatedPdfFile = pdfFile

                        val thumbs = withContext(Dispatchers.IO) {
                            val count = PdfImporter.getPageCount(pdfFile)
                            (0 until count).map { PdfImporter.renderPageThumbnail(pdfFile, it, targetWidth = 500) }
                        }
                        pagePreviews.clear()
                        pagePreviews.addAll(thumbs)
                    } else {
                        generatedPdfFile = null
                        pagePreviews.clear()
                    }
                } else {
                    generatedPdfFile = null
                    pagePreviews.clear()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isGenerating = false
            }
        }
    }

    LaunchedEffect(batchId) {
        generatePdfForBatch(batchId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable {
                            if (allBatches.size > 1) showBatchDropdown = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Barcode Sheet (${batch?.billNumber ?: "None"})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (allBatches.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.UnfoldMore, contentDescription = "Switch", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "A4 Printable Code 128 • ${pagePreviews.size} Page(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        DropdownMenu(
                            expanded = showBatchDropdown,
                            onDismissRequest = { showBatchDropdown = false }
                        ) {
                            allBatches.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("${b.billNumber} (${b.batchName})") },
                                    onClick = {
                                        showBatchDropdown = false
                                        currentBatchId = b.id
                                        generatePdfForBatch(b.id)
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Share PDF (WhatsApp / Drive)
                    IconButton(
                        onClick = {
                            val file = generatedPdfFile
                            if (file != null) {
                                BarcodePdfGenerator.sharePdf(context, file)
                            } else {
                                Toast.makeText(context, "No PDF generated yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = generatedPdfFile != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = if (generatedPdfFile != null) Color(0xFF1976D2) else Color.Gray)
                    }
                }
            )
        },
        bottomBar = {
            if (generatedPdfFile != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onNavigateToBatchScanner(currentBatchId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📱 Scan 1-by-1 on Screen (दुसऱ्या मोबाईलची गरज नाही)", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = generatedPdfFile
                                    if (file != null && activity != null) {
                                        BarcodePdfGenerator.printPdf(activity, file)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val file = generatedPdfFile
                                    if (file != null) {
                                        BarcodePdfGenerator.sharePdf(context, file)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Generating Code 128 Barcode Sheet...",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "A4 प्रिंट करण्यायोग्य शीट तयार होत आहे...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (pagePreviews.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Barcodes Available to Generate PDF", fontWeight = FontWeight.Bold)
                        Text("प्रथम बिल अपलोड करून नंबर व्हेरिफाय करा", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                Text(
                    text = "${batch?.billNumber ?: ""} • ${pagePreviews.size} Page(s) Generated",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(pagePreviews) { index, bitmap ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "Sheet Page ${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(70.dp))
                    }
                }
            }
        }
    }
}
