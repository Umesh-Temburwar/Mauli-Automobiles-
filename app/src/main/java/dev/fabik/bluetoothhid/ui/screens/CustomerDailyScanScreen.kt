package dev.fabik.bluetoothhid.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fabik.bluetoothhid.data.CustomerScanRecord
import dev.fabik.bluetoothhid.data.CustomerScansSummary
import dev.fabik.bluetoothhid.data.MasterSparePart
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.generator.CustomerBillingPdfGenerator
import dev.fabik.bluetoothhid.ui.CameraPreviewContent
import dev.fabik.bluetoothhid.ui.RequiresCameraPermission
import dev.fabik.bluetoothhid.ui.SoundToggleAction
import dev.fabik.bluetoothhid.ui.model.BarcodeResult
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceStateDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDailyScanScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MauliDatabase.getInstance(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Live Cart & Scan, 1: Camera Scanner, 2: Master Catalogue
    val todayDateStr = remember { MauliDatabase.getTodayDateString() }

    val customerScans = remember { mutableStateListOf<CustomerScanRecord>() }
    var summary by remember { mutableStateOf(CustomerScansSummary()) }
    var isLoading by remember { mutableStateOf(true) }

    var inputBarcode by remember { mutableStateOf("") }
    var inputCustomerName by remember { mutableStateOf("") }

    // Master parts search state
    var catalogueSearchQuery by remember { mutableStateOf("") }
    val masterParts = remember { mutableStateListOf<MasterSparePart>() }

    // Dialog states
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var exportedPdfFile by remember { mutableStateOf<File?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    // Camera scanner debouncing
    var lastScannedCode by remember { mutableStateOf<String?>(null) }
    var lastScanTime by remember { mutableStateOf(0L) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Audio / Haptic feedback
    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (_: Exception) {
            null
        }
    }
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGen?.release()
            } catch (_: Exception) {}
        }
    }

    val playSound by context.getPreferenceStateDefault(PreferenceStore.PLAY_SOUND)
    val vibratePref by context.getPreferenceStateDefault(PreferenceStore.VIBRATE)

    fun playBeepAndVibrate() {
        if (playSound) {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            } catch (_: Exception) {}
        }
        if (vibratePref) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            } catch (_: Exception) {}
        }
    }

    fun reloadData() {
        scope.launch {
            isLoading = true
            db.ensureMasterCatalogueSeeded()
            val scans = db.getTodayCustomerScans(todayDateStr)
            val sum = db.getCustomerScansSummary(todayDateStr)
            customerScans.clear()
            customerScans.addAll(scans)
            summary = sum
            isLoading = false
        }
    }

    fun performBarcodeAdd(barcode: String) {
        val clean = barcode.trim()
        if (clean.isBlank()) return
        scope.launch {
            val record = db.addCustomerScan(
                barcode = clean,
                customerName = inputCustomerName.trim()
            )
            playBeepAndVibrate()
            Toast.makeText(
                context,
                "✓ Added: ${record.productName} (Qty: ${record.quantity})",
                Toast.LENGTH_SHORT
            ).show()
            inputBarcode = ""
            reloadData()
        }
    }

    LaunchedEffect(Unit) {
        reloadData()
    }

    LaunchedEffect(catalogueSearchQuery, selectedTab) {
        if (selectedTab == 2) {
            val results = if (catalogueSearchQuery.isBlank()) {
                db.getAllMasterParts()
            } else {
                db.searchMasterParts(catalogueSearchQuery)
            }
            masterParts.clear()
            masterParts.addAll(results)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Customer Spares (ग्राहक विक्री)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Today: $todayDateStr • Mauli Automobiles",
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
                    SoundToggleAction()
                    if (customerScans.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear List", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            // Sticky Bottom Night PDF & WhatsApp Bar
            if (customerScans.isNotEmpty() && selectedTab != 1) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Summary row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total: ${summary.totalRecords} parts (${summary.totalQuantity} pcs)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bill: ₹${"%.2f".format(summary.totalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Night PDF Export Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            isExportingPdf = true
                                            showPdfExportDialog = true
                                            val file = CustomerBillingPdfGenerator.generateCustomerReportPdf(
                                                context = context,
                                                dateStr = todayDateStr,
                                                records = customerScans,
                                                summary = summary
                                            )
                                            exportedPdfFile = file
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isExportingPdf = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E3A8A)
                                )
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("रात्रीचा PDF अहवाल", maxLines = 1, fontSize = 13.sp)
                            }

                            // Share via WhatsApp Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val file = CustomerBillingPdfGenerator.generateCustomerReportPdf(
                                                context = context,
                                                dateStr = todayDateStr,
                                                records = customerScans,
                                                summary = summary
                                            )
                                            CustomerBillingPdfGenerator.sharePdf(context, file)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Share Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF15803D)
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp शेअर", maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Selection
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("आजची विक्री (${customerScans.size})") },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("कॅमेरा स्कॅनर") },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Master Spares") },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: TODAY'S CART & MANUAL INPUT
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Quick Input Box
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "बारकोड स्कॅन करा किंवा नंबर टाका:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = inputBarcode,
                                        onValueChange = { inputBarcode = it },
                                        placeholder = { Text("उदा. 8904235100018") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { performBarcodeAdd(inputBarcode) },
                                        enabled = inputBarcode.isNotBlank(),
                                        modifier = Modifier.height(54.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add")
                                        Text("जोडा")
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Tip: तोच बारकोड पुन्हा स्कॅन केल्यास Quantity वाढते",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { selectedTab = 1 }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("कॅमेरा उघडा")
                                    }
                                }
                            }
                        }

                        // Scanned Items List
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (customerScans.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "आज अद्याप कोणताही पार्ट स्कॅन केला नाही",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "ग्राहक आल्यावर कॅमेऱ्याने किंवा बारकोड नंबर टाकून जोडा. रात्री 1-क्लिक मध्ये PDF रिपोर्ट तयार होईल.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { selectedTab = 1 }) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("कॅमेरा स्कॅनर सुरू करा")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(customerScans, key = { _, item -> item.id }) { index, item ->
                                    CustomerItemCard(
                                        index = index + 1,
                                        item = item,
                                        onIncrease = {
                                            scope.launch {
                                                db.updateCustomerScanQty(item.id, item.quantity + 1)
                                                reloadData()
                                            }
                                        },
                                        onDecrease = {
                                            scope.launch {
                                                db.updateCustomerScanQty(item.id, item.quantity - 1)
                                                reloadData()
                                            }
                                        },
                                        onDelete = {
                                            scope.launch {
                                                db.deleteCustomerScan(item.id)
                                                reloadData()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: INTEGRATED HIGH-SPEED CAMERA SCANNER
                    Box(modifier = Modifier.fillMaxSize()) {
                        RequiresCameraPermission {
                            CameraPreviewContent(
                                onCameraReady = { control, _, _ ->
                                    cameraControl = control
                                },
                                onBarcodeDetected = { result: BarcodeResult? ->
                                    val code = result?.text?.trim()
                                    if (code.isNullOrBlank()) return@CameraPreviewContent

                                    val now = System.currentTimeMillis()
                                    if (lastScannedCode == code && (now - lastScanTime) < 1800L) {
                                        return@CameraPreviewContent
                                    }
                                    lastScanTime = now
                                    lastScannedCode = code

                                    performBarcodeAdd(code)
                                }
                            )
                        }

                        // Overlay Banner on top of Camera
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📸 बारकोड कॅमेऱ्यासमोर धरा",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Hero MotoCorp, Hero Honda आणि Honda चे ओरिजिनल बारकोड आपोआप ओळखले जातील.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                if (lastScannedCode != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "शेवटचा स्कॅन: $lastScannedCode",
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Bottom Floating Button to switch to cart
                        Button(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                                .fillMaxWidth(0.85f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("आजची विक्री यादी पहा (${customerScans.size})")
                        }
                    }
                }

                2 -> {
                    // TAB 2: MASTER SPARE PARTS CATALOGUE
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = catalogueSearchQuery,
                            onValueChange = { catalogueSearchQuery = it },
                            placeholder = { Text("शोधा: Splendor, Activa, Brake Shoe, Filter, Chain...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (catalogueSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { catalogueSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )

                        Text(
                            text = "Hero & Honda Verified Catalogue (${masterParts.size} parts):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(masterParts, key = { _, part -> part.barcode }) { _, part ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = part.productName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Part No: ${part.partNumber} • ${part.brand}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFC2410C),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Fits: ${part.compatibleModels}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Barcode: ${part.barcode}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "MRP: ₹${"%.2f".format(part.mrp)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                performBarcodeAdd(part.barcode)
                                                selectedTab = 0
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear confirmation dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("आजची यादी साफ करायची?") },
            text = { Text("आज स्कॅन केलेले सर्व ${customerScans.size} पार्ट हटवले जातील. आपण खात्री केली आहे का?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.clearCustomerScans(todayDateStr)
                            showClearConfirmDialog = false
                            reloadData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("होय, साफ करा")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("रद्द करा")
                }
            }
        )
    }

    // PDF Export Success / Action Dialog
    if (showPdfExportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isExportingPdf) showPdfExportDialog = false
            },
            title = {
                Text(if (isExportingPdf) "PDF तयार होत आहे..." else "PDF अहवाल तयार झाला! 📄")
            },
            text = {
                if (isExportingPdf) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("बारकोड आणि सर्व पार्टसह A4 PDF तयार करत आहे...")
                    }
                } else {
                    Column {
                        Text(
                            text = "आपला दैनिक विक्री अहवाल आणि बारकोड शीट यशस्वीरीत्या जनरेट झाली आहे:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = exportedPdfFile?.name ?: "Customer_Report.pdf",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "एकूण पार्ट: ${summary.totalRecords} (${summary.totalQuantity} pcs) | रक्कम: ₹${"%.2f".format(summary.totalAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (!isExportingPdf && exportedPdfFile != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                CustomerBillingPdfGenerator.sharePdf(context, exportedPdfFile!!)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp शेअर")
                        }
                        OutlinedButton(
                            onClick = {
                                CustomerBillingPdfGenerator.openPdf(context, exportedPdfFile!!)
                            }
                        ) {
                            Text("PDF पहा")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isExportingPdf) {
                    TextButton(onClick = { showPdfExportDialog = false }) {
                        Text("बंद करा")
                    }
                }
            }
        )
    }
}

@Composable
private fun CustomerItemCard(
    index: Int,
    item: CustomerScanRecord,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$index",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.productName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Part No: ${item.partNumber} • ${item.brand}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC2410C),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.notes.isNotBlank()) {
                            Text(
                                text = "Fits: ${item.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barcode & Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "बारकोड: ${item.barcode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (item.mrp > 0) {
                        Text(
                            text = "₹${"%.2f".format(item.mrp)} × ${item.quantity} = ₹${"%.2f".format(item.quantity * item.mrp)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                // Quantity Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onDecrease() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onIncrease() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
