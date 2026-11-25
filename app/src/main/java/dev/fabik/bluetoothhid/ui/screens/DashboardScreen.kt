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
import dev.fabik.bluetoothhid.data.CatalogueStats
import dev.fabik.bluetoothhid.data.CustomerScansSummary
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.data.SummaryStats
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search

import dev.fabik.bluetoothhid.tally.AutomationMode
import dev.fabik.bluetoothhid.tally.TallyAutomationEngine
import dev.fabik.bluetoothhid.tally.TallyProfileRepository
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.fabik.bluetoothhid.tally.WifiTallyManager
import dev.fabik.bluetoothhid.tally.WifiStatus
import dev.fabik.bluetoothhid.ui.SoundToggleAction

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
    onNavigateToTallyAutomation: () -> Unit = {},
    onNavigateToCustomerDailyScan: () -> Unit = {},
    onNavigateToCatalogue: () -> Unit = {},
    onNavigateToKeyboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val controller = LocalController.current
    val tallyRepo = remember { TallyProfileRepository.getInstance(context) }
    val activeProfile by tallyRepo.activeProfile.collectAsStateWithLifecycle()
    val automationMode by tallyRepo.automationMode.collectAsStateWithLifecycle()
    val isExecuting by TallyAutomationEngine.isExecuting.collectAsStateWithLifecycle()
    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    val scope = rememberCoroutineScope()
    val wifiManager = remember { WifiTallyManager.getInstance(context) }
    val isWifiMode by wifiManager.isWifiMode.collectAsStateWithLifecycle()
    val wifiStatus by wifiManager.wifiStatus.collectAsStateWithLifecycle()
    val serverIp by wifiManager.serverIp.collectAsStateWithLifecycle()
    val pcHostname by wifiManager.pcHostname.collectAsStateWithLifecycle()
    val lastWifiMsg by wifiManager.lastMessage.collectAsStateWithLifecycle()

    var showIpDialog by remember { mutableStateOf(false) }
    var editIpText by remember(serverIp) { mutableStateOf(serverIp) }

    var stats by remember { mutableStateOf(SummaryStats()) }
    var customerScansSummary by remember { mutableStateOf(CustomerScansSummary()) }
    var catalogueStats by remember { mutableStateOf(CatalogueStats()) }

    LaunchedEffect(Unit) {
        val db = MauliDatabase.getInstance(context)
        db.ensureMasterCatalogueSeeded()
        db.ensureCompleteCatalogueSeeded()
        stats = db.getSummaryStats()
        customerScansSummary = db.getCustomerScansSummary()
        catalogueStats = db.getCatalogueStats()
    }

    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("PC WiFi IP Address Set Karein") },
            text = {
                Column {
                    Text(
                        "Apne TallyPrime wale PC ka WiFi IP enter karein:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editIpText,
                        onValueChange = { editIpText = it },
                        label = { Text("PC IP Address (e.g. 192.168.1.104)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    wifiManager.setServerIp(editIpText)
                    showIpDialog = false
                    Toast.makeText(context, "IP Set to $editIpText! Checking connection...", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                    // 1-Tap Sound On/Off Toggle
                    SoundToggleAction()

                    // Wireless Keyboard 1-Tap Shortcut
                    IconButton(onClick = onNavigateToKeyboard) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = "Tally Keyboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

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
            // 1. Connection Mode Switcher (WiFi Tally Mode vs Bluetooth Mode)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mode Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isWifiMode,
                            onClick = { wifiManager.setWifiMode(true) },
                            label = { Text("📶 WiFi Mode (Recommended)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.Wifi, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE8F5E9),
                                selectedLabelColor = Color(0xFF2E7D32)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isWifiMode,
                            onClick = { wifiManager.setWifiMode(false) },
                            label = { Text("⚡ Bluetooth", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE3F2FD),
                                selectedLabelColor = Color(0xFF1565C0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isWifiMode) {
                        // WiFi Tally Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = when (wifiStatus) {
                                    WifiStatus.CONNECTED -> Color(0xFFE8F5E9)
                                    WifiStatus.SEARCHING -> Color(0xFFFFF8E1)
                                    else -> Color(0xFFFFEBEE)
                                }
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (wifiStatus) {
                                                        WifiStatus.CONNECTED -> Color(0xFF2E7D32)
                                                        WifiStatus.SEARCHING -> Color(0xFFF57F17)
                                                        else -> Color(0xFFC62828)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = when (wifiStatus) {
                                                    WifiStatus.CONNECTED -> "Tally WiFi: CONNECTED 🟢"
                                                    WifiStatus.SEARCHING -> "Searching PC on WiFi... 🔍"
                                                    else -> "Tally WiFi: DISCONNECTED 🔴"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = when (wifiStatus) {
                                                    WifiStatus.CONNECTED -> Color(0xFF1B5E20)
                                                    WifiStatus.SEARCHING -> Color(0xFFE65100)
                                                    else -> Color(0xFFB71C1C)
                                                }
                                            )
                                            Text(
                                                text = "PC: ${pcHostname ?: "Tally PC"} • $serverIp:8080",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (wifiStatus == WifiStatus.CONNECTED) "ONLINE" else "OFFLINE",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (wifiStatus == WifiStatus.CONNECTED) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = lastWifiMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            wifiManager.discoverPc(scope) { ok, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto Detect", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            wifiManager.pingServer(serverIp) { ok, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ping Test", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showIpDialog = true },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit IP", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Bluetooth Status Bar
                        BluetoothStatusBar(
                            isConnected = currentDevice != null,
                            deviceName = currentDevice?.name ?: currentDevice?.address,
                            onClick = onNavigateToBluetooth
                        )
                    }
                }
            }

            // 1.1 Tally Wireless Keyboard Remote (Connect hote hi phone Tally keyboard ban jata hai)
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToKeyboard() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFF0F172A)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp)
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
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "⌨️ Tally Wireless Keyboard Remote",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "Phone acts as wireless keyboard for TallyPrime (Numpad, Enter, Alt+A, F1-F12)",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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

            // 1.6 Customer Daily Spares & Night PDF Report (Hero Honda Master Database)
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCustomerDailyScan() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFF0F766E)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🛒 Customer Spares & Night PDF",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "ग्राहक विक्री बारकोड स्कॅन व रात्रीचा PDF अहवाल",
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status badge inside card
                        Surface(
                            color = Color.Black.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (customerScansSummary.totalRecords > 0) {
                                        "आजची विक्री: ${customerScansSummary.totalRecords} पार्ट (${customerScansSummary.totalQuantity} pcs)"
                                    } else {
                                        "Hero & Honda 50+ Master Spares Loaded"
                                    },
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (customerScansSummary.totalAmount > 0) {
                                        "₹${"%.2f".format(customerScansSummary.totalAmount)}"
                                    } else {
                                        "OPEN SCANNER ›"
                                    },
                                    color = Color(0xFFFDE047),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 1.7 Spare Parts Master Catalogue with Images
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCatalogue() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color(0xFF1E3A8A)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🏍️ Spare Parts Master Catalogue",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "पार्ट्सचे फोटो, ओरिजिनल बारकोड व बाईक मॉडेल लिस्ट",
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color.Black.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${catalogueStats.totalProducts} Verified OEM Parts • Hero & Honda",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "OPEN CATALOGUE ›",
                                    color = Color(0xFF67E8F9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Quick Tools & Utilities
            item {
                Text(
                    text = "Quick Tools & Reports",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToReports),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.Assessment, contentDescription = null, tint = Color(0xFF37474F), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Scan History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Reports & CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToBluetooth),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF0277BD), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Bluetooth Pair", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Windows PC HID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
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
