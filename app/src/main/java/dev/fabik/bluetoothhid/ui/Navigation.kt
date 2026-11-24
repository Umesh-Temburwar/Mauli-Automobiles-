package dev.fabik.bluetoothhid.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.History
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.ui.model.BarcodeResult
import dev.fabik.bluetoothhid.ui.screens.BarcodePdfScreen
import dev.fabik.bluetoothhid.ui.screens.BatchScannerScreen
import dev.fabik.bluetoothhid.ui.screens.DashboardScreen
import dev.fabik.bluetoothhid.ui.screens.DirectSendScreen
import dev.fabik.bluetoothhid.ui.screens.PdfImportScreen
import dev.fabik.bluetoothhid.ui.screens.ReportsScreen
import dev.fabik.bluetoothhid.ui.screens.VerificationScreen
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import dev.fabik.bluetoothhid.utils.getPreferenceStateBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dev.fabik.bluetoothhid.tally.AutomationMode
import dev.fabik.bluetoothhid.tally.TallyAutomationEngine
import dev.fabik.bluetoothhid.tally.TallyProfileRepository
import dev.fabik.bluetoothhid.tally.WifiTallyManager
import dev.fabik.bluetoothhid.ui.screens.CatalogueScreen
import dev.fabik.bluetoothhid.ui.screens.CustomerDailyScanScreen
import dev.fabik.bluetoothhid.ui.screens.ProductDetailScreen
import dev.fabik.bluetoothhid.ui.screens.TallyAutomationScreen
import dev.fabik.bluetoothhid.data.MasterProduct
import dev.fabik.bluetoothhid.data.MauliDatabase
import dev.fabik.bluetoothhid.generator.BarcodePdfGenerator

object Routes {
    const val Dashboard = "Dashboard"
    const val CustomerDailyScan = "CustomerDailyScan"
    const val Catalogue = "Catalogue"
    const val ProductDetail = "ProductDetail/{productId}"
    fun productDetailRoute(productId: Long) = "ProductDetail/$productId"
    const val TallyAutomation = "TallyAutomation"
    const val PdfImport = "PdfImport"
    const val Verification = "Verification/{batchId}"
    fun verificationRoute(batchId: Long) = "Verification/$batchId"
    const val BarcodePdf = "BarcodePdf/{batchId}"
    fun barcodePdfRoute(batchId: Long) = "BarcodePdf/$batchId"
    const val BatchScanner = "BatchScanner/{batchId}"
    fun batchScannerRoute(batchId: Long) = "BatchScanner/$batchId"
    const val DirectSend = "DirectSend/{batchId}"
    fun directSendRoute(batchId: Long) = "DirectSend/$batchId"
    const val Reports = "Reports"

    const val Devices = "Devices"
    const val Main = "Main"
    const val History = "History"
}

val LocalNavigation = staticCompositionLocalOf<NavHostController> {
    error("No Navigation provided")
}

@Composable
fun NavGraph() {
    val controller = LocalController.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    var canExit by remember { mutableStateOf(false) }
    val exitString = stringResource(R.string.exit_confirm)

    // External mode has no Bluetooth device, so it starts directly on the scanner and the
    // controller manages its own lifecycle (no disconnect on navigation).
    val context = LocalContext.current
    val connectionMode by context.getPreferenceStateBlocking(PreferenceStore.CONNECTION_MODE)
    val isExternal = connectionMode == ConnectionMode.EXTERNAL.ordinal

    // Handles shortcut to scanner/history
    val startDestination = remember {
        when (activity?.intent?.dataString) {
            "Scanner" -> Routes.Main
            "History" -> Routes.History
            else -> null
        }
    }

    LaunchedEffect(startDestination) {
        startDestination?.let {
            navController.navigate(it)
        }
    }

    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    CompositionLocalProvider(LocalNavigation provides navController) {
        NavHost(
            navController,
            startDestination ?: if (isExternal) Routes.Main else Routes.Dashboard
        ) {
            composable(Routes.Dashboard) {
                DashboardScreen(
                    onNavigateToPdfImport = { navController.navigate(Routes.PdfImport) },
                    onNavigateToVerify = { navController.navigate(Routes.verificationRoute(0)) },
                    onNavigateToGeneratePdf = { navController.navigate(Routes.barcodePdfRoute(0)) },
                    onNavigateToBatchScanner = { navController.navigate(Routes.batchScannerRoute(0)) },
                    onNavigateToBluetooth = { navController.navigate(Routes.Devices) },
                    onNavigateToDirectSend = { navController.navigate(Routes.directSendRoute(0)) },
                    onNavigateToReports = { navController.navigate(Routes.Reports) },
                    onNavigateToCameraScanner = { navController.navigate(Routes.Main) },
                    onNavigateToTallyAutomation = { navController.navigate(Routes.TallyAutomation) },
                    onNavigateToCustomerDailyScan = { navController.navigate(Routes.CustomerDailyScan) },
                    onNavigateToCatalogue = { navController.navigate(Routes.Catalogue) }
                )

                // Confirm back presses to exit the app from Dashboard
                BackHandler {
                    if (canExit) {
                        activity?.finishAfterTransition()
                    } else {
                        canExit = true
                        Toast.makeText(activity, exitString, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            canExit = false
                        }
                    }
                }
            }

            composable(Routes.PdfImport) {
                PdfImportScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToVerify = { batchId ->
                        navController.navigate(Routes.verificationRoute(batchId)) {
                            popUpTo(Routes.Dashboard)
                        }
                    }
                )
            }

            composable(
                route = Routes.Verification,
                arguments = listOf(navArgument("batchId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val bId = backStackEntry.arguments?.getLong("batchId") ?: 0L
                VerificationScreen(
                    batchId = bId,
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToGeneratePdf = { targetBatchId ->
                        navController.navigate(Routes.barcodePdfRoute(targetBatchId))
                    },
                    onNavigateToDirectSend = { targetBatchId ->
                        navController.navigate(Routes.directSendRoute(targetBatchId))
                    }
                )
            }

            composable(
                route = Routes.BarcodePdf,
                arguments = listOf(navArgument("batchId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val bId = backStackEntry.arguments?.getLong("batchId") ?: 0L
                BarcodePdfScreen(
                    batchId = bId,
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToBatchScanner = { targetBatchId ->
                        navController.navigate(Routes.batchScannerRoute(targetBatchId))
                    }
                )
            }

            composable(
                route = Routes.BatchScanner,
                arguments = listOf(navArgument("batchId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val bId = backStackEntry.arguments?.getLong("batchId") ?: 0L
                BatchScannerScreen(
                    batchId = bId,
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(
                route = Routes.DirectSend,
                arguments = listOf(navArgument("batchId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val bId = backStackEntry.arguments?.getLong("batchId") ?: 0L
                DirectSendScreen(
                    batchId = bId,
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(Routes.Reports) {
                ReportsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onSelectBatch = { selectedBatchId ->
                        navController.navigate(Routes.verificationRoute(selectedBatchId))
                    }
                )
            }

            composable(Routes.CustomerDailyScan) {
                CustomerDailyScanScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(Routes.Catalogue) {
                CatalogueScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onProductSelected = { prod ->
                        navController.navigate(Routes.productDetailRoute(prod.id))
                    }
                )
            }

            composable(
                route = Routes.ProductDetail,
                arguments = listOf(navArgument("productId") { type = NavType.LongType; defaultValue = 0L })
            ) { backStackEntry ->
                val prodId = backStackEntry.arguments?.getLong("productId") ?: 0L
                val db = remember { MauliDatabase.getInstance(context) }
                var product by remember { mutableStateOf<MasterProduct?>(null) }

                LaunchedEffect(prodId) {
                    val all = db.getAllProducts()
                    product = all.find { it.id == prodId }
                }

                product?.let { prod ->
                    ProductDetailScreen(
                        product = prod,
                        onNavigateBack = { navController.navigateUp() },
                        onGenerateBarcodePdf = { targetProd ->
                            scope.launch {
                                try {
                                    val file = BarcodePdfGenerator.generateProductLabelPdf(context, targetProd)
                                    BarcodePdfGenerator.sharePdf(context, file)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            composable(Routes.TallyAutomation) {
                TallyAutomationScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(Routes.Devices) {
                // Keep Bluetooth connected across all screens. Do NOT auto-disconnect on navigating.

                Devices {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Dashboard) { inclusive = false }
                        launchSingleTop = true
                    }
                }

                BackHandler {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Dashboard) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }

            composable(Routes.Main) {
                val tallyRepo = remember { TallyProfileRepository.getInstance(context) }
                val automationMode by tallyRepo.automationMode.collectAsStateWithLifecycle()
                val activeProfile by tallyRepo.activeProfile.collectAsStateWithLifecycle()
                val db = remember { MauliDatabase.getInstance(context) }

                // Unbounded channel ensures every barcode result is queued even during active sends
                val sendQueue = remember { Channel<BarcodeResult>(Channel.UNLIMITED) }

                // Sequential queue processor — handles WiFi Mode, Mode A, Mode B, Mode C
                LaunchedEffect(controller, automationMode, activeProfile) {
                    val wifiManager = WifiTallyManager.getInstance(context)
                    for (result in sendQueue) {
                        val barcodeType = ZXingAnalyzer.index2String(result.format)

                        if (wifiManager.isWifiMode.value) {
                            // 📶 WIRELESS WIFI TALLY MODE (Zero Bluetooth needed!)
                            scope.launch {
                                val matchedProd = db.findProductByBarcode(result.text)
                                val (success, msg) = wifiManager.sendBarcode(result.text, activeProfile)
                                withContext(Dispatchers.Main) {
                                    val feedback = if (matchedProd != null && success) {
                                        "✓ ${matchedProd.productName} (₹${matchedProd.mrp.toInt()}) ➔ Tally"
                                    } else {
                                        if (success) "WiFi Tally: $msg" else "WiFi Error: $msg"
                                    }
                                    Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            // ⚡ BLUETOOTH HID MODE
                            when (automationMode) {
                                AutomationMode.BARCODE_ONLY -> {
                                    controller?.sendString(
                                        result.text,
                                        true,
                                        "SCAN",
                                        null,
                                        barcodeType,
                                        imageName = result.imageName,
                                        regexGroups = result.regexGroups
                                    )
                                }
                                AutomationMode.AUTOMATIC -> {
                                    if (controller != null) {
                                        TallyAutomationEngine.executeProfileSequence(
                                            scope = scope,
                                            barcode = result.text,
                                            profile = activeProfile,
                                            controller = controller
                                        )
                                    } else {
                                        Toast.makeText(context, "Bluetooth PC not connected!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                AutomationMode.MANUAL -> {
                                    controller?.sendString(
                                        result.text,
                                        true,
                                        "SCAN",
                                        null,
                                        barcodeType,
                                        imageName = result.imageName,
                                        regexGroups = result.regexGroups
                                    )
                                }
                            }
                        }
                    }
                }

                // Returning from Scanner goes safely to Dashboard without disconnecting Bluetooth
                BackHandler {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Dashboard) { inclusive = false }
                        launchSingleTop = true
                    }
                }

                Scanner(currentDevice) { result: BarcodeResult ->
                    sendQueue.trySend(result)
                }
            }

            composable(Routes.History) {
                val onBack: () -> Unit = {
                    navController.navigateUp()
                }

                History(onBack) { historyEntry ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val barcodeType = historyEntry.format.let { ZXingAnalyzer.index2String(it) }
                        controller?.sendString(
                            historyEntry.value,
                            true,
                            "HISTORY",
                            historyEntry.timestamp,
                            barcodeType
                        )
                    }
                }
            }
        }
    }

    ExternalInputReceiver { code, format, source ->
        CoroutineScope(Dispatchers.IO).launch {
            controller?.sendString(
                code,
                true,
                source ?: "EXTERNAL_INPUT",
                System.currentTimeMillis(),
                format
            )
        }
    }

    // Listen for changes in the current device
    LaunchedEffect(currentDevice) {
        // When connected to a device, navigate to Dashboard smoothly (preserving connection!)
        if (currentDevice != null && navController.currentDestination?.route == Routes.Devices) {
            navController.navigate(Routes.Dashboard) {
                popUpTo(Routes.Dashboard) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

}