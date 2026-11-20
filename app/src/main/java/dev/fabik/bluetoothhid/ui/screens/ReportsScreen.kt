package dev.fabik.bluetoothhid.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fabik.bluetoothhid.data.BarcodeRecord
import dev.fabik.bluetoothhid.data.BillBatch
import dev.fabik.bluetoothhid.data.ExportManager
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.SummaryStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    onSelectBatch: (batchId: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MauliDatabase.getInstance(context) }

    var stats by remember { mutableStateOf(SummaryStats()) }
    val batches = remember { mutableStateListOf<BillBatch>() }
    val batchStats = remember { mutableStateMapOf<Long, Pair<Int, Int>>() } // batchId -> Pair(scanned, total)

    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    fun reloadData() {
        scope.launch {
            stats = db.getSummaryStats()
            val list = db.getAllBatches()
            batches.clear()
            batches.addAll(list)

            batchStats.clear()
            for (b in list) {
                val recs = db.getBarcodesForBatch(b.id)
                val scanned = recs.count { it.scanStatus == "SCANNED" }
                batchStats[b.id] = Pair(scanned, recs.size)
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan History & Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Cards
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Overall Performance Summary",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Bills Processed:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalBills}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Total Barcodes:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalBarcodes}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column {
                                Text("Verified:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalVerified}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Needs Review:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalNeedsReview}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                            }
                            Column {
                                Text("Scanned to Tally:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalScanned}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pending:", color = Color.Gray, fontSize = 12.sp)
                                Text("${stats.totalPending}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                            }
                        }
                    }
                }
            }

            // Batches Section Header
            item {
                Text(
                    text = "Customer Bill Batches (${batches.size}):",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (batches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No bill batches found yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(batches) { batch ->
                    val pair = batchStats[batch.id] ?: Pair(0, 0)
                    val scanned = pair.first
                    val total = pair.second
                    val progress = if (total > 0) scanned.toFloat() / total else 0f

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBatch(batch.id) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = batch.billNumber,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${batch.batchName} • ${dateFormat.format(Date(batch.createdAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    // Export CSV
                                    IconButton(onClick = {
                                        scope.launch {
                                            val recs = db.getBarcodesForBatch(batch.id)
                                            val csvFile = ExportManager.exportBatchToCsv(context, batch, recs)
                                            ExportManager.shareCsvFile(context, csvFile, "Batch ${batch.billNumber} Report")
                                        }
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    // Delete
                                    IconButton(onClick = {
                                        scope.launch {
                                            db.deleteBatch(batch.id)
                                            reloadData()
                                            Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Progress bar
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$scanned of $total Scanned (${(progress * 100).toInt()}%)",
                                    fontSize = 11.sp,
                                    color = if (scanned == total && total > 0) Color(0xFF2E7D32) else Color.DarkGray,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to open",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
