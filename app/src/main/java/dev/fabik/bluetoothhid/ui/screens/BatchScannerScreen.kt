package dev.fabik.bluetoothhid.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.PdfDocumentRecord
import dev.fabik.bluetoothhid.data.ScanHistoryRecord
import dev.fabik.bluetoothhid.generator.BarcodeGenerator
import dev.fabik.bluetoothhid.ocr.MlKitOcrProvider
import dev.fabik.bluetoothhid.pdf.PdfImporter
import dev.fabik.bluetoothhid.ui.CameraPreviewContent
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import dev.fabik.bluetoothhid.ui.model.BarcodeResult
import dev.fabik.bluetoothhid.utils.ExtraKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScannerScreen(
    batchId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = LocalController.current
    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    val db = remember { MauliDatabase.getInstance(context) }
    var currentBatchId by remember { mutableStateOf(batchId) }
    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val allBatches = remember { mutableStateListOf<BillBatch>() }
    var showBatchDropdown by remember { mutableStateOf(false) }

    val barcodes = remember { mutableStateListOf<BarcodeRecord>() }

    // Mode: 0 = On-Screen PDF 1-by-1 Barcode Reader (No 2nd phone required!), 1 = Camera Scanner
    var selectedModeTab by remember { mutableIntStateOf(0) }

    // Screen Reader State
    var currentScreenIndex by remember { mutableIntStateOf(0) }
    var isAutoSending by remember { mutableStateOf(false) }
    var autoSendDelayMs by remember { mutableStateOf(1500L) }
    var autoSendJob by remember { mutableStateOf<Job?>(null) }
    var isImportingPdf by remember { mutableStateOf(false) }

    // Camera Mode State
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var autoSendToTally by remember { mutableStateOf(true) }

    // Suffix: 0 = Enter (Tally default!), 1 = Tab, 2 = None
    var suffixIndex by remember { mutableIntStateOf(0) }

    var manualCodeInput by remember { mutableStateOf("") }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanStatusMessage by remember { mutableStateOf<String?>(null) }
    var lastScanIsDuplicate by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf<Long?>(null) }

    val scannedSet = remember { mutableSetOf<String>() }

    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (e: Exception) { null }
    }

    fun playFeedback(isDuplicate: Boolean) {
        try {
            if (isDuplicate) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            }

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(if (isDuplicate) 150 else 70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(if (isDuplicate) 150 else 70)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reloadBatch(targetId: Long = currentBatchId) {
        scope.launch {
            val list = db.getAllBatches()
            allBatches.clear()
            allBatches.addAll(list)

            val loadedBatch = if (targetId > 0) db.getBatchById(targetId) else list.firstOrNull()
            batch = loadedBatch
            val activeId = loadedBatch?.id ?: targetId
            currentBatchId = activeId

            if (activeId > 0) {
                val records = db.getBarcodesForBatch(activeId)
                barcodes.clear()
                barcodes.addAll(records)
                scannedSet.clear()
                records.filter { it.scanStatus == "SCANNED" }.forEach { scannedSet.add(it.correctedValue) }
                if (currentScreenIndex >= records.size && records.isNotEmpty()) {
                    currentScreenIndex = 0
                }
            } else {
                barcodes.clear()
                scannedSet.clear()
            }
        }
    }

    LaunchedEffect(batchId) {
        reloadBatch(batchId)
    }

    fun transmitBarcode(code: String) {
        val appendKey = when (suffixIndex) {
            0 -> ExtraKeys.ENTER // Default for Tally!
            1 -> ExtraKeys.TAB
            else -> ExtraKeys.NONE
        }

        val isDuplicate = scannedSet.contains(code)
        lastScannedBarcode = code
        lastScanIsDuplicate = isDuplicate

        playFeedback(isDuplicate)

        scope.launch {
            val activeId = batch?.id ?: currentBatchId
            if (activeId > 0) {
                db.markBarcodeScanned(activeId, code)
            }
            scannedSet.add(code)

            // Save to scan history
            db.insertScanHistory(
                ScanHistoryRecord(
                    barcodeRecordId = 0,
                    scannedValue = code,
                    transmissionStatus = if (currentDevice != null && autoSendToTally) "SENT" else "SCANNED"
                )
            )

            // Send via Bluetooth HID to Windows Tally
            if (currentDevice != null && autoSendToTally) {
                val sender = controller?.keyboardSender
                if (sender != null) {
                    sender.sendProcessedString(
                        processedString = code,
                        sendDelay = 40L,
                        appendKey = appendKey,
                        locale = "US"
                    )
                    lastScanStatusMessage = "Sent to Tally with Enter: $code"
                } else {
                    lastScanStatusMessage = "Bluetooth sender not ready"
                }
            } else {
                lastScanStatusMessage = if (isDuplicate) "Duplicate: $code" else "Recorded: $code"
            }
        }
    }

    // PDF Import Launcher for on-the-fly PDF loading inside scanner
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                isImportingPdf = true
                Toast.makeText(context, "Reading barcodes from PDF...", Toast.LENGTH_SHORT).show()
                val internalFile = PdfImporter.copyPdfToInternalStorage(context, uri)
                val docId = db.insertPdfDocument(
                    PdfDocumentRecord(
                        filename = internalFile.name,
                        importDate = System.currentTimeMillis(),
                        totalPages = PdfImporter.getPageCount(internalFile),
                        processingStatus = "EXTRACTED",
                        filePath = internalFile.absolutePath
                    )
                )

                val ocrProvider = MlKitOcrProvider()
                val totalP = PdfImporter.getPageCount(internalFile)
                val newBatchId = db.insertBatch(
                    BillBatch(
                        batchName = "PDF Scan ${SimpleDateFormat("dd-MM HH:mm", Locale.getDefault()).format(Date())}",
                        billNumber = "Bill #${SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())}",
                        pdfDocumentId = docId
                    )
                )

                val records = mutableListOf<BarcodeRecord>()
                for (p in 0 until totalP) {
                    val bmp = withContext(Dispatchers.IO) {
                        PdfImporter.renderPageHighRes(internalFile, p, scale = 2.0f)
                    }
                    val pageResults = ocrProvider.extractBarcodesFromPage(bmp, pageNumber = p + 1)
                    for (item in pageResults) {
                        records.add(
                            BarcodeRecord(
                                batchId = newBatchId,
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
                    bmp.recycle()
                }

                if (records.isNotEmpty()) {
                    db.insertBarcodeRecords(records)
                }

                currentBatchId = newBatchId
                reloadBatch(newBatchId)
                Toast.makeText(context, "Loaded ${records.size} barcodes from PDF!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error reading PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isImportingPdf = false
            }
        }
    }

    fun sendAndNext() {
        if (barcodes.isEmpty()) return
        val record = barcodes.getOrNull(currentScreenIndex) ?: return
        transmitBarcode(record.correctedValue)
        if (currentScreenIndex < barcodes.size - 1) {
            currentScreenIndex++
        } else {
            Toast.makeText(context, "All barcodes sent to Tally!", Toast.LENGTH_SHORT).show()
        }
    }

    fun startAutoSend() {
        if (isAutoSending) {
            autoSendJob?.cancel()
            isAutoSending = false
            return
        }
        if (barcodes.isEmpty()) return
        isAutoSending = true
        autoSendJob = scope.launch {
            while (isAutoSending && currentScreenIndex < barcodes.size) {
                val record = barcodes[currentScreenIndex]
                transmitBarcode(record.correctedValue)
                delay(autoSendDelayMs)
                if (currentScreenIndex < barcodes.size - 1) {
                    currentScreenIndex++
                } else {
                    isAutoSending = false
                    Toast.makeText(context, "Finished sending all items!", Toast.LENGTH_LONG).show()
                    break
                }
            }
            isAutoSending = false
        }
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
                                batch?.billNumber ?: "Barcode Scanner",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (allBatches.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.UnfoldMore, contentDescription = "Switch", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "Done: ${scannedSet.size} / ${barcodes.size} • Suffix: ${if (suffixIndex == 0) "Enter" else if (suffixIndex == 1) "Tab" else "None"}",
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
                                        reloadBatch(b.id)
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
                    // Quick import PDF button
                    IconButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import PDF", tint = Color(0xFF1976D2))
                    }
                    if (selectedModeTab == 1) {
                        // Flash toggle for camera mode
                        IconButton(onClick = {
                            isFlashOn = !isFlashOn
                            cameraControl?.enableTorch(isFlashOn)
                        }) {
                            Icon(
                                if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (isFlashOn) Color.Yellow else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mode Tabs: On-Screen PDF Reader vs Camera Scanner
            TabRow(selectedTabIndex = selectedModeTab) {
                Tab(
                    selected = selectedModeTab == 0,
                    onClick = {
                        selectedModeTab = 0
                        autoSendToTally = true
                    },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Screen Scan (१-by-१)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedModeTab == 1,
                    onClick = {
                        selectedModeTab = 1
                        autoSendToTally = true
                    },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Camera Scan", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            if (selectedModeTab == 0) {
                /* ========================================================
                 * MODE 0: ON-SCREEN PDF 1-BY-1 BARCODE READER
                 * (No 2nd phone required! Renders barcodes right on phone!)
                 * ======================================================== */
                if (isImportingPdf) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Reading barcodes from PDF...", fontWeight = FontWeight.Bold)
                            Text("एक-एक बारकोड तयार होत आहेत...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                } else if (barcodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Barcodes in this Batch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "दुसऱ्या मोबाईलची गरज नाही! खालील बटनावर क्लिक करून PDF सिलेक्ट करा किंवा डॅशबोर्डवरून बिल तयार करा.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Bill PDF (PDF निवडा)")
                            }
                        }
                    }
                } else {
                    val currentRecord = barcodes.getOrNull(currentScreenIndex) ?: barcodes.first()
                    val isCurrentScanned = scannedSet.contains(currentRecord.correctedValue)
                    val progress = scannedSet.size.toFloat() / barcodes.size

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Progress & Suffix settings
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Item ${currentScreenIndex + 1} of ${barcodes.size} (${(progress * 100).toInt()}% Done)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Enter", "Tab", "None").forEachIndexed { idx, label ->
                                            val isSel = suffixIndex == idx
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFF1976D2) else Color.LightGray.copy(alpha = 0.5f))
                                                    .clickable { suffixIndex = idx }
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(label, color = if (isSel) Color.White else Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }

                        // Main On-Screen Barcode Card
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isCurrentScanned) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isCurrentScanned) Color(0xFF4CAF50) else Color.LightGray.copy(alpha = 0.4f))
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            if (isCurrentScanned) "✓ SENT TO TALLY" else "PENDING",
                                            color = if (isCurrentScanned) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        "Page ${currentRecord.pageNumber}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Rendered Code 128 Barcode Image
                                val barcodeBitmap = remember(currentRecord.correctedValue) {
                                    BarcodeGenerator.generateCode128Safe(currentRecord.correctedValue, width = 560, height = 140, showText = false)
                                }

                                if (barcodeBitmap != null) {
                                    Image(
                                        bitmap = barcodeBitmap.asImageBitmap(),
                                        contentDescription = "Barcode",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(85.dp)
                                            .background(Color.White)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .background(Color.LightGray.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Barcode Preview", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Large Bold Part Code / Barcode Value
                                Text(
                                    text = currentRecord.correctedValue,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = if (isCurrentScanned) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                if (currentDevice != null) {
                                    Text(
                                        "🟢 Connected to: ${currentDevice?.name ?: "Windows PC"}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Text(
                                        "⚠️ Bluetooth PC not connected",
                                        fontSize = 11.sp,
                                        color = Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // Big Send to Tally & Navigation Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Giant Main Action Button: Send & Next
                            Button(
                                onClick = { sendAndNext() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCurrentScanned) Color(0xFF2E7D32) else Color(0xFF1976D2)
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isCurrentScanned) "Re-Send & Next (पुन्हा पाठवा)" else "SEND TO TALLY & NEXT (Enter)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }

                            // Navigation Row: Prev • Auto-Send • Next
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (currentScreenIndex > 0) currentScreenIndex--
                                    },
                                    enabled = currentScreenIndex > 0,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBackIos, contentDescription = "Prev", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Prev")
                                }

                                Button(
                                    onClick = { startAutoSend() },
                                    modifier = Modifier.weight(1.3f).height(46.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAutoSending) Color(0xFFE65100) else Color(0xFF4527A0)
                                    )
                                ) {
                                    Icon(
                                        if (isAutoSending) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isAutoSending) "Stop Auto" else "Auto-Send All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (currentScreenIndex < barcodes.size - 1) currentScreenIndex++
                                    },
                                    enabled = currentScreenIndex < barcodes.size - 1,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Next")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next", modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Bottom Quick Jump Item Strip
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("All Barcodes (${barcodes.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Tap to jump & send", fontSize = 10.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(barcodes) { idx, item ->
                                    val isItemScanned = scannedSet.contains(item.correctedValue)
                                    val isSelected = currentScreenIndex == idx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) Color(0xFF1976D2).copy(alpha = 0.2f)
                                                else if (isItemScanned) Color(0xFF4CAF50).copy(alpha = 0.1f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                currentScreenIndex = idx
                                                transmitBarcode(item.correctedValue)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${idx + 1}.",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF1976D2) else Color.Gray,
                                                modifier = Modifier.width(24.dp)
                                            )
                                            Text(
                                                item.correctedValue,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isItemScanned) {
                                            Icon(Icons.Default.Check, contentDescription = "Sent", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("Pending", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                /* ========================================================
                 * MODE 1: CAMERA SCANNER (For physical paper/boxes)
                 * ======================================================== */
                Box(modifier = Modifier.fillMaxSize()) {
                    RequiresCameraPermission {
                        CameraPreviewContent(
                            onCameraReady = { control, _, _ ->
                                cameraControl = control
                            },
                            onBarcodeDetected = { result: BarcodeResult? ->
                                val code = result?.text?.trim()
                                if (code.isNullOrBlank()) return@CameraPreviewContent

                                if (lastScannedBarcode == code && System.currentTimeMillis() - (lastScanTime ?: 0L) < 1500L) {
                                    return@CameraPreviewContent
                                }
                                lastScanTime = System.currentTimeMillis()
                                transmitBarcode(code)
                            }
                        )

                        // Top Batch Progress Overlay
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .align(Alignment.TopCenter),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = Color.Black.copy(alpha = 0.82f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val progress = if (barcodes.isNotEmpty()) scannedSet.size.toFloat() / barcodes.size else 0f

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Progress: ${scannedSet.size} / ${barcodes.size} (${(progress * 100).toInt()}%)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Auto-Send:", color = Color.LightGray, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = autoSendToTally,
                                            onCheckedChange = { autoSendToTally = it },
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = Color(0xFF4CAF50),
                                    trackColor = Color.DarkGray
                                )
                            }
                        }

                        // Bottom Last Scanned Result & Manual Input
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedVisibility(visible = lastScannedBarcode != null) {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = if (lastScanIsDuplicate) Color(0xFFD32F2F) else Color(0xFF1B5E20)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (lastScanIsDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = lastScannedBarcode ?: "",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = lastScanStatusMessage ?: "",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Manual Barcode Input Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualCodeInput,
                                    onValueChange = { manualCodeInput = it },
                                    placeholder = { Text("Manual code...", color = Color.Gray, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (manualCodeInput.isNotBlank()) {
                                            transmitBarcode(manualCodeInput.trim())
                                            manualCodeInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF4CAF50))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
