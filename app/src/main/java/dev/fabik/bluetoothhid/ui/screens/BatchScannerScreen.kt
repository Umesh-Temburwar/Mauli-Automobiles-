package dev.fabik.bluetoothhid.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.ScanHistoryRecord
import dev.fabik.bluetoothhid.ui.CameraPreviewContent
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import dev.fabik.bluetoothhid.ui.model.BarcodeResult
import dev.fabik.bluetoothhid.utils.ExtraKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    Box(Modifier.fillMaxSize()) {
        RequiresCameraPermission {
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
                                        batch?.billNumber ?: "Batch Scanner",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (allBatches.size > 1) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.UnfoldMore, contentDescription = "Switch", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(
                                    "Scanned: ${scannedSet.size} / ${barcodes.size} • Suffix: ${if (suffixIndex == 0) "Enter" else if (suffixIndex == 1) "Tab" else "None"}",
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
                            // Flash toggle
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 1. Live Camera Preview
                    CameraPreviewContent(
                        onCameraReady = { control, _, _ ->
                            cameraControl = control
                        },
                        onBarcodeDetected = { result: BarcodeResult? ->
                            val code = result?.text?.trim()
                            if (code.isNullOrBlank()) return@CameraPreviewContent

                            // Prevent rapid multi-detection within 1.5 seconds
                            if (lastScannedBarcode == code && System.currentTimeMillis() - (lastScanTime ?: 0L) < 1500L) {
                                return@CameraPreviewContent
                            }
                            lastScanTime = System.currentTimeMillis()

                            transmitBarcode(code)
                        }
                    )

                    // 2. Top Batch Progress Overlay
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

                            // Suffix Selector row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Key Suffix:", color = Color.LightGray, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Enter", "Tab", "None").forEachIndexed { idx, label ->
                                        val isSel = suffixIndex == idx
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) Color(0xFF1976D2) else Color.DarkGray)
                                                .clickable { suffixIndex = idx }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Bottom Last Scanned Result & Manual Input
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
