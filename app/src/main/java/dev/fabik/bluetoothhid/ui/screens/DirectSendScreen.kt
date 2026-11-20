package dev.fabik.bluetoothhid.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
    var currentBatchId by remember { mutableStateOf(batchId) }
    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val allBatches = remember { mutableStateListOf<BillBatch>() }
    var showBatchDropdown by remember { mutableStateOf(false) }

    val barcodes = remember { mutableStateListOf<BarcodeRecord>() }

    // Suffix selection: 0 = Enter (Tally default!), 1 = Tab, 2 = None
    var suffixOption by remember { mutableIntStateOf(0) }
    // Delay selection: 500ms, 1000ms, 2000ms
    var delayMs by remember { mutableStateOf(1000L) }

    var isSending by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var sendingJob by remember { mutableStateOf<Job?>(null) }

    fun reloadData(targetId: Long = currentBatchId) {
        scope.launch {
            val list = db.getAllBatches()
            allBatches.clear()
            allBatches.addAll(list)

            val loadedBatch = if (targetId > 0) db.getBatchById(targetId) else list.firstOrNull()
            batch = loadedBatch
            val activeBatchId = loadedBatch?.id ?: targetId
            currentBatchId = activeBatchId

            if (activeBatchId > 0) {
                val records = db.getBarcodesForBatch(activeBatchId)
                barcodes.clear()
                barcodes.addAll(records)
            } else {
                barcodes.clear()
            }
        }
    }

    LaunchedEffect(batchId) {
        reloadData(batchId)
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
                                "Direct Send to Tally",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (allBatches.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.UnfoldMore, contentDescription = "Switch", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "${batch?.billNumber ?: "Select Batch"} • ${barcodes.size} Numbers",
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
                                        reloadData(b.id)
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
                    // Reset Sent Status
                    if (barcodes.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                barcodes.forEach { rec ->
                                    db.updateBarcode(rec.copy(scanStatus = "PENDING"))
                                }
                                Toast.makeText(context, "Status reset for all numbers", Toast.LENGTH_SHORT).show()
                                reloadData()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Status")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (barcodes.isNotEmpty()) {
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
                                        Toast.makeText(context, "Bluetooth to PC is not connected! PC शी ब्लूटूथ जोडा", Toast.LENGTH_LONG).show()
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
                                            0 -> ExtraKeys.ENTER
                                            1 -> ExtraKeys.TAB
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
                                        Toast.makeText(context, "All numbers sent to Tally successfully!", Toast.LENGTH_SHORT).show()
                                        reloadData()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD81B60)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send All Numbers to Tally (थेट Tally मध्ये पाठवा)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                .padding(14.dp)
        ) {
            // Bluetooth Connection Status Warning
            if (currentDevice == null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Tally PC Not Connected", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), fontSize = 13.sp)
                            Text("Please connect Bluetooth to your Windows Tally PC first.", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Transmission Configuration Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Transmission Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Suffix selection
                    Text("Auto Key Press after each barcode:", fontSize = 11.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Enter (Tally Default)", "Tab", "None").forEachIndexed { idx, label ->
                            val isSel = suffixOption == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF1976D2) else Color(0xFFECEFF1))
                                    .clickable { suffixOption = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Delay selection
                    Text("Delay between numbers:", fontSize = 11.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(500L to "0.5 sec", 1000L to "1.0 sec", 2000L to "2.0 sec").forEach { (d, label) ->
                            val isSel = delayMs == d
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF2E7D32) else Color(0xFFECEFF1))
                                    .clickable { delayMs = d }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar if sending
            if (isSending) {
                LinearProgressIndicator(
                    progress = { if (barcodes.isNotEmpty()) currentIndex.toFloat() / barcodes.size else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFD81B60)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Sending item ${currentIndex + 1} of ${barcodes.size} to Tally...",
                    fontSize = 12.sp,
                    color = Color(0xFFD81B60),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Numbers list
            if (barcodes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No numbers found in this batch", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(barcodes) { idx, rec ->
                        val isCurrent = isSending && currentIndex == idx
                        val isScanned = rec.scanStatus == "SCANNED"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) Color(0xFFFFF9C4) else if (isScanned) Color(0xFFE8F5E9) else Color.White
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${idx + 1}.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(24.dp))
                                    Text(
                                        rec.correctedValue,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                if (isScanned) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF4CAF50))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Sent", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (isCurrent) {
                                    Text("Sending...", color = Color(0xFFE65100), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Pending", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}
