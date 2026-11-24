package dev.fabik.bluetoothhid.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.fabik.bluetoothhid.data.CatalogueStats
import dev.fabik.bluetoothhid.data.MasterProduct
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.tally.TallyProfileRepository
import dev.fabik.bluetoothhid.tally.WifiTallyManager
import dev.fabik.bluetoothhid.ui.image.MauliImageLoader
import dev.fabik.bluetoothhid.ui.image.ProductImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueScreen(
    onNavigateBack: () -> Unit,
    onProductSelected: (MasterProduct) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { MauliDatabase.getInstance(context) }
    val wifiManager = remember { WifiTallyManager.getInstance(context) }
    val tallyRepo = remember { TallyProfileRepository.getInstance(context) }

    val products = remember { mutableStateListOf<MasterProduct>() }
    var stats by remember { mutableStateOf(CatalogueStats()) }
    var isLoading by remember { mutableStateOf(true) }

    // Search and Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("All") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedModel by remember { mutableStateOf("All") }
    var isGridView by remember { mutableStateOf(false) }

    // Fullscreen Image Zoom Dialog
    var zoomedProduct by remember { mutableStateOf<MasterProduct?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var cacheSizeBytes by remember { mutableStateOf(0L) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val imported = MauliImageLoader.importOfflineImageZip(context, stream)
                        Toast.makeText(context, "✓ $imported Offline Images Imported!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "ZIP Import Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadData() {
        scope.launch {
            isLoading = true
            db.ensureCompleteCatalogueSeeded()
            val st = db.getCatalogueStats()
            stats = st
            val results = db.searchProducts(
                query = searchQuery,
                brandFilter = selectedBrand,
                categoryFilter = selectedCategory,
                modelFilter = selectedModel
            )
            products.clear()
            products.addAll(results)
            cacheSizeBytes = MauliImageLoader.getImageCacheSize(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    LaunchedEffect(searchQuery, selectedBrand, selectedCategory, selectedModel) {
        val results = db.searchProducts(
            query = searchQuery,
            brandFilter = selectedBrand,
            categoryFilter = selectedCategory,
            modelFilter = selectedModel
        )
        products.clear()
        products.addAll(results)
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label Copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun sendToTally(product: MasterProduct) {
        scope.launch {
            val codeToSend = product.originalBarcode.ifBlank { product.oemPartNumber }
            if (wifiManager.isWifiMode.value) {
                val profile = tallyRepo.activeProfile.value
                val (ok, msg) = wifiManager.sendBarcode(codeToSend, profile)
                Toast.makeText(context, if (ok) "✓ Tally WiFi: $msg" else "Error: $msg", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Barcode: $codeToSend ready for Tally", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Spare Parts Catalogue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stats.totalProducts} Verified OEM Parts • Hero & Honda",
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
                    // Grid / List Toggle
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View"
                        )
                    }
                    // Storage / Offline Images Manage
                    IconButton(onClick = { showStorageDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Offline Images")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Part No, Name, Barcode, Bike (Splendor, Activa)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // Brand Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val brands = listOf("All", "Hero MotoCorp", "Hero Honda", "Honda")
                items(brands) { brand ->
                    FilterChip(
                        selected = selectedBrand == brand,
                        onClick = { selectedBrand = brand },
                        label = { Text(brand, fontSize = 12.sp) }
                    )
                }
            }

            // Category Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = listOf("All", "Braking", "Transmission", "Filters", "Chains & Sprockets", "Electrical", "Engine", "Suspension", "Bearings", "Consumables")
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Products Grid or List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching spare parts found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing the search query or selecting 'All' brands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(products, key = { it.id }) { product ->
                            CatalogueGridCard(
                                product = product,
                                onImageClick = { zoomedProduct = product },
                                onCardClick = { onProductSelected(product) },
                                onSendTally = { sendToTally(product) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(products, key = { it.id }) { product ->
                            CatalogueListCard(
                                product = product,
                                onImageClick = { zoomedProduct = product },
                                onCardClick = { onProductSelected(product) },
                                onSendTally = { sendToTally(product) },
                                onCopyBarcode = { copyToClipboard("Barcode", product.originalBarcode) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Zoom Dialog
    zoomedProduct?.let { prod ->
        Dialog(onDismissRequest = { zoomedProduct = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prod.productName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { zoomedProduct = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ProductImageView(
                        imageUrl = prod.imageUrl,
                        localPath = prod.localImagePath,
                        productName = prod.productName,
                        partNumber = prod.oemPartNumber,
                        brand = prod.brand,
                        category = prod.category,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "OEM Part No: ${prod.oemPartNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC2410C)
                    )
                    Text(
                        text = "Barcode: ${prod.originalBarcode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "MRP: ₹${"%.2f".format(prod.mrp)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            sendToTally(prod)
                            zoomedProduct = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Barcode to TallyPrime")
                    }
                }
            }
        }
    }

    // Storage Management & Offline Image Pack Dialog
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Product Image & Storage Manager") },
            text = {
                Column {
                    Text(
                        text = "Mode A (Online CDN): Direct official image URLs are active.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mode B (Offline Thumbnails): 100% pre-rendered local images included.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Image Cache Size: ${cacheSizeBytes / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Offline Image ZIP Pack")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            MauliImageLoader.clearImageCache(context)
                            cacheSizeBytes = 0L
                            Toast.makeText(context, "Image Cache Cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Image Cache")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun CatalogueListCard(
    product: MasterProduct,
    onImageClick: () -> Unit,
    onCardClick: () -> Unit,
    onSendTally: () -> Unit,
    onCopyBarcode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Image Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .clickable { onImageClick() }
            ) {
                ProductImageView(
                    imageUrl = product.imageUrl,
                    localPath = product.localImagePath,
                    productName = product.productName,
                    partNumber = product.oemPartNumber,
                    brand = product.brand,
                    category = product.category,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Part No: ${product.oemPartNumber} • ${product.brand}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC2410C),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Fits: ${product.vehicleModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹${"%.2f".format(product.mrp)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onCopyBarcode,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Barcode", modifier = Modifier.size(16.dp))
                        }
                        Button(
                            onClick = onSendTally,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Tally", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueGridCard(
    product: MasterProduct,
    onImageClick: () -> Unit,
    onCardClick: () -> Unit,
    onSendTally: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF8FAFC))
                    .clickable { onImageClick() }
            ) {
                ProductImageView(
                    imageUrl = product.imageUrl,
                    localPath = product.localImagePath,
                    productName = product.productName,
                    partNumber = product.oemPartNumber,
                    brand = product.brand,
                    category = product.category,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = product.productName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Text(
                text = product.oemPartNumber,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFC2410C),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = product.brand,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${"%.2f".format(product.mrp)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )

                Button(
                    onClick = onSendTally,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Tally", fontSize = 10.sp)
                }
            }
        }
    }
}
