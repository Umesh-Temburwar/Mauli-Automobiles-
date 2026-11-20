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
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val barcodes = remember { mutableStateListOf<BarcodeRecord>() }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var autoSendToTally by remember { mutableStateOf(true) }

    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var lastScanStatusMessage by remember { mutableStateOf<String?>(null) }
    var lastScanIsDuplicate by remember { mutableStateOf(false) }

    val scannedSet = remember { mutableSetOf<String>() }

    // Vibrator & Tone generator for audio feedback
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

    fun reloadBatch() {
        scope.launch {
            val loadedBatch = if (batchId > 0) db.getBatchById(batchId) else db.getAllBatches().firstOrNull()
            batch = loadedBatch
            val activeId = loadedBatch?.id ?: batchId
            val records = db.getBarcodesForBatch(activeId)
            barcodes.clear()
            barcodes.addAll(records)
            scannedSet.clear()
            records.filter { it.scanStatus == "SCANNED" }.forEach { scannedSet.add(it.correctedValue) }
        }
    }

    LaunchedEffect(batchId) {
        reloadBatch()
    }

    Box(Modifier.fillMaxSize()) {
        RequiresCameraPermission {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                batch?.billNumber ?: "Batch Scanner",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Scanned: ${scannedSet.size} / ${barcodes.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

                        val isDuplicate = scannedSet.contains(code)
                        lastScannedBarcode = code
                        lastScanIsDuplicate = isDuplicate

                        playFeedback(isDuplicate)

                        scope.launch {
                            val activeId = batch?.id ?: batchId
                            val updated = db.markBarcodeScanned(activeId, code)
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
                                        appendKey = ExtraKeys.NONE,
                                        locale = "US"
                                    )
                                    lastScanStatusMessage = "Typed into Tally: $code"
                                } else {
                                    lastScanStatusMessage = "HID Keyboard not ready"
                                }
                            } else {
                                lastScanStatusMessage = if (isDuplicate) "Duplicate Scan: $code" else "Recorded: $code"
                            }
                        }
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
                        containerColor = Color.Black.copy(alpha = 0.78f)
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
                                fontSize = 14.sp
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

                // 3. Bottom Last Scanned Result Banner
                AnimatedVisibility(
                    visible = lastScannedBarcode != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (lastScanIsDuplicate) Color(0xFFD32F2F) else Color(0xFF1B5E20)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (lastScanIsDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lastScannedBarcode ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = lastScanStatusMessage ?: "Scanned successfully",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

private var lastScanTime: Long? = null
