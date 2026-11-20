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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import dev.fabik.bluetoothhid.data.MauliDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    batchId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToGeneratePdf: (batchId: Long) -> Unit,
    onNavigateToDirectSend: (batchId: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MauliDatabase.getInstance(context) }

    var currentBatchId by remember { mutableStateOf(batchId) }
    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val allBatches = remember { mutableStateListOf<BillBatch>() }
    val barcodes = remember { mutableStateListOf<BarcodeRecord>() }
    var duplicateValues by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDropdown by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newBarcodeValue by remember { mutableStateOf("") }

    var editingRecord by remember { mutableStateOf<BarcodeRecord?>(null) }
    var editedValue by remember { mutableStateOf("") }

    fun refreshData(targetId: Long = currentBatchId) {
        scope.launch {
            val batchList = db.getAllBatches()
            allBatches.clear()
            allBatches.addAll(batchList)

            val loadedBatch = if (targetId > 0) {
                db.getBatchById(targetId)
            } else {
                batchList.firstOrNull()
            }

            batch = loadedBatch
            val activeBatchId = loadedBatch?.id ?: 0L
            currentBatchId = activeBatchId

            if (activeBatchId > 0) {
                val records = db.getBarcodesForBatch(activeBatchId)
                barcodes.clear()
                barcodes.addAll(records)
                duplicateValues = db.findDuplicatesInBatch(activeBatchId)
            } else {
                barcodes.clear()
                duplicateValues = emptySet()
            }
        }
    }

    LaunchedEffect(batchId) {
        refreshData(batchId)
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
                                batch?.billNumber ?: "Verify Barcodes",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (allBatches.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.UnfoldMore, contentDescription = "Switch Batch", modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "${barcodes.size} Numbers • ${batch?.batchName ?: "Tap to switch batch"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Batch Switch Dropdown
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
                                        refreshData(b.id)
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
                    // Quick Approve All Button
                    if (barcodes.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                barcodes.forEach { rec ->
                                    if (rec.verificationStatus != "VERIFIED") {
                                        db.updateBarcode(rec.copy(verificationStatus = "VERIFIED"))
                                    }
                                }
                                Toast.makeText(context, "All barcodes approved!", Toast.LENGTH_SHORT).show()
                                refreshData()
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Approve All", tint = Color(0xFF2E7D32))
                        }
                    }

                    // Add Manual Barcode
                    IconButton(onClick = {
                        newBarcodeValue = ""
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Barcode")
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onNavigateToDirectSend(currentBatchId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Direct to Tally", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onNavigateToGeneratePdf(currentBatchId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (barcodes.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Text(
                        "No Barcode Numbers in this Batch",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "कॅमेऱ्याने बिलाचा फोटो काढा किंवा नवीन नंबर जोडा",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            newBarcodeValue = ""
                            showAddDialog = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Barcode Manually (नंबर टाका)")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(barcodes, key = { it.id }) { record ->
                    val isDuplicate = duplicateValues.contains(record.correctedValue)
                    val isVerified = record.verificationStatus == "VERIFIED"

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isDuplicate) Color(0xFFFFEBEE) else Color.White
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.correctedValue,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Status Badge
                                    val (badgeBg, badgeText, badgeColor) = when {
                                        isDuplicate -> Triple(Color(0xFFFFCDD2), "Duplicate", Color(0xFFC62828))
                                        isVerified -> Triple(Color(0xFFC8E6C9), "Verified", Color(0xFF2E7D32))
                                        else -> Triple(Color(0xFFFFE0B2), "Needs Review", Color(0xFFE65100))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            badgeText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        "Page ${record.pageNumber}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // Action Buttons
                            Row {
                                IconButton(onClick = {
                                    editingRecord = record
                                    editedValue = record.correctedValue
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.DarkGray)
                                }

                                IconButton(onClick = {
                                    scope.launch {
                                        db.deleteBarcode(record.id)
                                        refreshData()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }
        }
    }

    // Add Manual Barcode Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Barcode Number") },
            text = {
                Column {
                    Text("Enter barcode number preserving all leading zeros:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newBarcodeValue,
                        onValueChange = { newBarcodeValue = it },
                        placeholder = { Text("e.g. 8901234567890 or 17211-KUD-900") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newBarcodeValue.isNotBlank()) {
                        scope.launch {
                            var targetBatchId = currentBatchId
                            // If no batch exists, create one
                            if (targetBatchId == 0L || batch == null) {
                                val newId = db.insertBatch(
                                    BillBatch(
                                        batchName = "Manual Batch",
                                        billNumber = "Bill #${SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())}",
                                        pdfDocumentId = 0L
                                    )
                                )
                                targetBatchId = newId
                                currentBatchId = newId
                            }

                            db.insertBarcodeRecords(
                                listOf(
                                    BarcodeRecord(
                                        batchId = targetBatchId,
                                        originalOcrValue = newBarcodeValue.trim(),
                                        correctedValue = newBarcodeValue.trim(),
                                        pageNumber = 1,
                                        confidence = 1.0f,
                                        verificationStatus = "VERIFIED",
                                        barcodeFormat = "CODE_128",
                                        scanStatus = "PENDING"
                                    )
                                )
                            )
                            showAddDialog = false
                            refreshData(targetBatchId)
                        }
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Barcode Dialog
    editingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("Edit Barcode") },
            text = {
                Column {
                    Text("Correct the extracted number:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedValue,
                        onValueChange = { editedValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editedValue.isNotBlank()) {
                        scope.launch {
                            db.updateBarcode(
                                record.copy(
                                    correctedValue = editedValue.trim(),
                                    verificationStatus = "VERIFIED"
                                )
                            )
                            editingRecord = null
                            refreshData()
                        }
                    }
                }) {
                    Text("Save & Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
