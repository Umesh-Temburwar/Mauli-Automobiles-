package dev.fabik.bluetoothhid.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.fabik.bluetoothhid.utils.ExtraKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectSendScreen(
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

    // Suffix selection: 0 = None, 1 = Enter, 2 = Tab
    var suffixOption by remember { mutableIntStateOf(0) }
    // Delay selection: 500ms, 1000ms, 2000ms
    var delayMs by remember { mutableStateOf(1000L) }

    var isSending by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var sendingJob by remember { mutableStateOf<Job?>(null) }

    fun reloadData() {
        scope.launch {
            val loadedBatch = if (batchId > 0) db.getBatchById(batchId) else db.getAllBatches().firstOrNull()
            batch = loadedBatch
            val activeBatchId = loadedBatch?.id ?: batchId
            val list = db.getBarcodesForBatch(activeBatchId)
            barcodes.clear()
            barcodes.addAll(list)
        }
    }

    LaunchedEffect(batchId) {
        reloadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Direct Send to Tally", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isSending) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isPaused = !isPaused
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPaused) "Resume" else "Pause")
                            }

                            Button(
                                onClick = {
                                    sendingJob?.cancel()
                                    isSending = false
                                    isPaused = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (currentDevice == null) {
                                    Toast.makeText(context, "Bluetooth to PC is not connected!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val sender = controller?.keyboardSender
                                if (sender == null) {
                                    Toast.makeText(context, "Keyboard HID sender not ready!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSending = true
                                isPaused = false
                                currentIndex = 0

                                sendingJob = scope.launch {
                                    val appendKey = when (suffixOption) {
                                        1 -> ExtraKeys.ENTER
                                        2 -> ExtraKeys.TAB
                                        else -> ExtraKeys.NONE
                                    }

                                    for ((idx, rec) in barcodes.withIndex()) {
                                        currentIndex = idx
                                        while (isPaused) {
                                            delay(200)
                                        }

                                        // Transmit barcode string via Bluetooth HID
                                        sender.sendProcessedString(
                                            processedString = rec.correctedValue,
                                            sendDelay = 50L,
                                            appendKey = appendKey,
                                            locale = "US"
                                        )

                                        // Mark as scanned/transmitted in database
                                        db.updateBarcode(rec.copy(scanStatus = "SCANNED"))

                                        // Delay between items so Tally can process field
                                        delay(delayMs)
                                    }

                                    isSending = false
                                    Toast.makeText(context, "All barcodes sent to Tally!", Toast.LENGTH_SHORT).show()
                                    reloadData()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD81B60)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = barcodes.isNotEmpty()
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Start Sending to Tally (Tally मध्ये पाठवा)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
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
                .padding(horizontal = 16.dp)
        ) {
            // Tally Guidance Banner
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xFFE3F2FD)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TallyPrime Focus Instructions:",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D47A1),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "1. Open Sales / Purchase Voucher in Tally.\n2. Keep keyboard cursor on the Item Name or Barcode field.\n3. Numbers will be typed automatically one-by-one via Bluetooth HID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            // Suffix and Delay Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "After Each Barcode Suffix:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = suffixOption == 0, onClick = { suffixOption = 0 })
                            Text("None", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = suffixOption == 1, onClick = { suffixOption = 1 })
                            Text("Enter", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = suffixOption == 2, onClick = { suffixOption = 2 })
                            Text("Tab", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Delay Between Items:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500L to "0.5s", 1000L to "1s", 2000L to "2s").forEach { (ms, label) ->
                                Text(
                                    text = label,
                                    color = if (delayMs == ms) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontWeight = if (delayMs == ms) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable { delayMs = ms }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Sending Progress
            AnimatedVisibility(visible = isSending) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    val progress = if (barcodes.isNotEmpty()) (currentIndex + 1).toFloat() / barcodes.size else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPaused) "PAUSED • ${currentIndex + 1}/${barcodes.size}" else "Sending item ${currentIndex + 1} of ${barcodes.size} to Tally...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // List of Barcodes to be Sent
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(barcodes) { idx, item ->
                    val isCurrent = isSending && currentIndex == idx
                    val isSent = item.scanStatus == "SCANNED"

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when {
                                isCurrent -> Color(0xFFFFF9C4)
                                isSent -> Color(0xFFE8F5E9)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = item.correctedValue,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Sent",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sent", color = Color(0xFF2E7D32), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
