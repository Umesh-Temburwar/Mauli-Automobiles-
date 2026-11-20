package dev.fabik.bluetoothhid.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.SettingsActivity
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.SummaryStats

import dev.fabik.bluetoothhid.tally.AutomationMode
import dev.fabik.bluetoothhid.tally.TallyAutomationEngine
import dev.fabik.bluetoothhid.tally.TallyProfileRepository
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPdfImport: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onNavigateToGeneratePdf: () -> Unit,
    onNavigateToBatchScanner: () -> Unit,
    onNavigateToBluetooth: () -> Unit,
    onNavigateToDirectSend: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToCameraScanner: () -> Unit,
    onNavigateToTallyAutomation: () -> Unit = {}
) {
    val context = LocalContext.current
    val controller = LocalController.current
    val tallyRepo = remember { TallyProfileRepository.getInstance(context) }
    val activeProfile by tallyRepo.activeProfile.collectAsStateWithLifecycle()
    val automationMode by tallyRepo.automationMode.collectAsStateWithLifecycle()
    val isExecuting by TallyAutomationEngine.isExecuting.collectAsStateWithLifecycle()
    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    var stats by remember { mutableStateOf(SummaryStats()) }

    LaunchedEffect(Unit) {
        val db = MauliDatabase.getInstance(context)
        stats = db.getSummaryStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.mauli_logo),
                            contentDescription = "Mauli Barcode",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "MAULI BARCODE",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Honda & Hero Genuine Spares • Ambajogai",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Emergency Stop Action in Dashboard Top Bar
                    IconButton(onClick = {
                        TallyAutomationEngine.emergencyStop(controller?.keyboardSender)
                        Toast.makeText(context, "EMERGENCY STOPPED!", Toast.LENGTH_SHORT).show()
                    }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD32F2F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Emergency Stop", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Bluetooth Connection Status Banner
            item {
                BluetoothStatusBar(
                    isConnected = currentDevice != null,
                    deviceName = currentDevice?.name ?: currentDevice?.address,
                    onClick = onNavigateToBluetooth
                )
            }

            // 1.2 Tally Automation Profile & Mode Banner (Direct Shortcut to Configuration)
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTallyAutomation() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFF1E293B)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD32F2F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Tally: ${activeProfile.name}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Mode: ${automationMode.name} • ${activeProfile.steps.size} Shortcut Steps",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "EDIT ⚙️",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 1.5 Hero 1-Tap Photo Bill to Tally (Simplest 1-Click Access for Shopkeeper!)
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToBatchScanner() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFFE65100)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "⚡ 1-Tap Bill Photo to Tally",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "बिलाचा फोटो काढा व एका क्लिकवर Tally मध्ये भरा",
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // 2. Summary Statistics Card
            item {
                StatsOverviewCard(stats = stats)
            }

            // 3. Workflow Header
            item {
                Text(
                    text = "Automobile Bill to Tally Workflow",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 4. Primary Workflow Actions (Large Touch-Friendly Cards for Shopkeeper)
            item {
                ActionCard(
                    title = "1. Import Handwritten PDF",
                    subtitle = "ग्राहक बिल PDF अपलोड करा (Upload Customer Bill PDF)",
                    icon = Icons.Default.PictureAsPdf,
                    accentColor = Color(0xFF1976D2),
                    onClick = onNavigateToPdfImport
                )
            }

            item {
                ActionCard(
                    title = "2. Verify Barcode Numbers",
                    subtitle = "AI ने काढलेले नंबर तपासा (Review & Edit Extracted Numbers)",
                    icon = Icons.Default.EditNote,
                    accentColor = Color(0xFFE65100),
                    badgeCount = stats.totalNeedsReview.takeIf { it > 0 },
                    onClick = onNavigateToVerify
                )
            }

            item {
                ActionCard(
                    title = "3. Generate Barcode PDF",
                    subtitle = "बारकोड शीट प्रिंट करा (Print Code 128 Barcodes)",
                    icon = Icons.Default.Print,
                    accentColor = Color(0xFF2E7D32),
                    onClick = onNavigateToGeneratePdf
                )
            }

            item {
                ActionCard(
                    title = "4. Batch Barcode Scanner",
                    subtitle = "कॅमेऱ्याने बारकोड Tally मध्ये स्कॅन करा (Scan into Tally)",
                    icon = Icons.Default.QrCodeScanner,
                    accentColor = Color(0xFF6A1B9A),
                    onClick = onNavigateToBatchScanner
                )
            }

            item {
                ActionCard(
                    title = "5. Direct Send to Tally",
                    subtitle = "विना प्रिंट थेट Tally मध्ये पाठवा (Direct Keyboard Typing)",
                    icon = Icons.Default.FlashOn,
                    accentColor = Color(0xFFD81B60),
                    onClick = onNavigateToDirectSend
                )
            }

            item {
                ActionCard(
                    title = "6. Bluetooth Connect to Tally",
                    subtitle = "Windows संगणकाशी ब्लूटूथ जोडा (Connect to PC)",
                    icon = Icons.Default.Bluetooth,
                    accentColor = Color(0xFF0277BD),
                    onClick = onNavigateToBluetooth
                )
            }

            item {
                ActionCard(
                    title = "7. Scan History & Reports",
                    subtitle = "इतिहास व CSV रिपोर्ट डाउनलोड करा (Reports & History)",
                    icon = Icons.Default.Assessment,
                    accentColor = Color(0xFF37474F),
                    onClick = onNavigateToReports
                )
            }

            item {
                ActionCard(
                    title = "8. Standard Camera Scanner",
                    subtitle = "सिंगल बारकोड स्कॅनर (Direct Single Scanner)",
                    icon = Icons.Default.CameraAlt,
                    accentColor = Color(0xFF455A64),
                    onClick = onNavigateToCameraScanner
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun BluetoothStatusBar(
    isConnected: Boolean,
    deviceName: String?,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isConnected) "Tally Connected" else "Tally Not Connected",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                    Text(
                        text = if (isConnected) "PC: ${deviceName ?: "Windows PC"}" else "Tap to pair with Windows Tally",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }
            }

            Text(
                text = if (isConnected) "ACTIVE" else "CONNECT",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
        }
    }
}

@Composable
fun StatsOverviewCard(stats: SummaryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem(label = "Bills", count = stats.totalBills, color = MaterialTheme.colorScheme.primary)
            StatItem(label = "Barcodes", count = stats.totalBarcodes, color = MaterialTheme.colorScheme.secondary)
            StatItem(label = "Scanned", count = stats.totalScanned, color = Color(0xFF2E7D32))
            StatItem(label = "Pending", count = stats.totalPending, color = Color(0xFFE65100))
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Red)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
