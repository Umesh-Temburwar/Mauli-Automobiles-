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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    var batch by remember { mutableStateOf<BillBatch?>(null) }
    val barcodes = remember { mutableStateListOf<BarcodeRecord>() }
    var duplicateValues by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newBarcodeValue by remember { mutableStateOf("") }

    fun refreshData() {
        scope.launch {
            val loadedBatch = if (batchId > 0) db.getBatchById(batchId) else db.getAllBatches().firstOrNull()
            batch = loadedBatch
            val activeBatchId = loadedBatch?.id ?: batchId
            val records = db.getBarcodesForBatch(activeBatchId)
            barcodes.clear()
            barcodes.addAll(records)
            duplicateValues = db.findDuplicatesInBatch(activeBatchId)
        }
    }

    LaunchedEffect(batchId) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            batch?.billNumber ?: "Verify Barcodes",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${barcodes.size} Numbers Extracted",
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Barcode")
                    }
                }
            )
        },
        bottomBar = {
            // Action Buttons
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
                            val activeBatchId = batch?.id ?: batchId
                            if (barcodes.isNotEmpty()) {
                                onNavigateToDirectSend(activeBatchId)
                            } else {
                                Toast.makeText(context, "No barcodes to send", Toast.LENGTH_SHORT).show()
                            }
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
                            val activeBatchId = batch?.id ?: batchId
                            if (barcodes.isNotEmpty()) {
                                onNavigateToGeneratePdf(activeBatchId)
                            } else {
                                Toast.makeText(context, "No barcodes to generate", Toast.LENGTH_SHORT).show()
                            }
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Summary Chips
            val verifiedCount = barcodes.count { it.verificationStatus == "VERIFIED" }
            val reviewCount = barcodes.count { it.verificationStatus == "NEEDS_REVIEW" }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = "$verifiedCount Verified", color = Color(0xFF2E7D32))
                if (reviewCount > 0) {
                    StatusBadge(text = "$reviewCount Review", color = Color(0xFFE65100))
                }
                if (duplicateValues.isNotEmpty()) {
                    StatusBadge(text = "${duplicateValues.size} Duplicates", color = Color(0xFFD32F2F))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (reviewCount > 0) {
                    Text(
                        text = "Approve All",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            scope.launch {
                                for (b in barcodes) {
                                    if (b.verificationStatus != "VERIFIED") {
                                        db.updateBarcode(b.copy(verificationStatus = "VERIFIED"))
                                    }
                                }
                                refreshData()
                                Toast.makeText(context, "All barcodes approved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // List of Extracted Barcodes
            if (barcodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No barcode numbers found in this batch",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Text("Add Barcode Manually")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(barcodes, key = { _, item -> item.id }) { index, item ->
                        val isDuplicate = duplicateValues.contains(item.correctedValue)
                        val needsReview = item.verificationStatus == "NEEDS_REVIEW"

                        BarcodeVerificationCard(
                            index = index + 1,
                            record = item,
                            isDuplicate = isDuplicate,
                            onValueChanged = { newVal ->
                                scope.launch {
                                    val updated = item.copy(
                                        correctedValue = newVal,
                                        verificationStatus = "VERIFIED"
                                    )
                                    db.updateBarcode(updated)
                                    refreshData()
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    db.deleteBarcode(item.id)
                                    refreshData()
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Add Missing Barcode Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Missing Barcode (नंबर जोडा)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the product barcode number written on the bill:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newBarcodeValue,
                        onValueChange = { newBarcodeValue = it },
                        label = { Text("Barcode Number (Exact String)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newBarcodeValue.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch {
                                val activeBatchId = batch?.id ?: batchId
                                db.insertBarcodeRecords(
                                    listOf(
                                        BarcodeRecord(
                                            batchId = activeBatchId,
                                            originalOcrValue = trimmed,
                                            correctedValue = trimmed,
                                            pageNumber = 1,
                                            confidence = 1.0f,
                                            verificationStatus = "VERIFIED",
                                            barcodeFormat = "CODE_128",
                                            scanStatus = "PENDING"
                                        )
                                    )
                                )
                                newBarcodeValue = ""
                                showAddDialog = false
                                refreshData()
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun BarcodeVerificationCard(
    index: Int,
    record: BarcodeRecord,
    isDuplicate: Boolean,
    onValueChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    var textValue by remember(record.correctedValue) { mutableStateOf(record.correctedValue) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                isDuplicate -> Color(0xFFFFEBEE)
                record.verificationStatus == "NEEDS_REVIEW" -> Color(0xFFFFF8E1)
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
            // Index & Page badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(42.dp)
            ) {
                Text(
                    text = "#$index",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "P.${record.pageNumber}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Editable Text Field (Preserves exact string)
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onValueChanged(it)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                trailingIcon = {
                    if (isDuplicate) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Duplicate",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (record.verificationStatus == "VERIFIED") {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            // Delete action
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
