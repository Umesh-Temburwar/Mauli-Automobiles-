package dev.fabik.bluetoothhid.ui.screens

// TallyKeyboardScreen – Full wireless keyboard remote for TallyPrime.
// Tab sections: QWERTY | Numpad | Function | Navigation | Custom Shortcuts
// DIRECT TEXT BAR: Type any text and send directly to TallyPrime
// QWERTY: Full A-Z keyboard with Shift, Caps, symbols, all keys
// CUSTOM SHORTCUTS: Real-time builder – tap keys, name it, save it

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.tally.TallyActionType
import dev.fabik.bluetoothhid.tally.TallyAutomationEngine
import dev.fabik.bluetoothhid.tally.TallyStep
import dev.fabik.bluetoothhid.tally.WifiStatus
import dev.fabik.bluetoothhid.tally.WifiTallyManager
import dev.fabik.bluetoothhid.ui.SoundToggleAction
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceStateDefault
import kotlinx.coroutines.launch

// ─── Data model for a saved custom shortcut ────────────────────────────────────
data class CustomShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val steps: List<TallyStep>,
    val color: Color = Color(0xFF1565C0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyKeyboardScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalController.current
    val scope = rememberCoroutineScope()
    val wifiManager = remember { WifiTallyManager.getInstance(context) }

    val wifiStatus by wifiManager.wifiStatus.collectAsStateWithLifecycle()
    val serverIp by wifiManager.serverIp.collectAsStateWithLifecycle()
    val pcHostname by wifiManager.pcHostname.collectAsStateWithLifecycle()
    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    val playSound by context.getPreferenceStateDefault(PreferenceStore.PLAY_SOUND)
    val vibratePref by context.getPreferenceStateDefault(PreferenceStore.VIBRATE)

    val toneGen = remember {
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60) } catch (_: Exception) { null }
    }
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    DisposableEffect(Unit) {
        onDispose { try { toneGen?.release() } catch (_: Exception) {} }
    }

    // ─── State ──────────────────────────────────────────────────────────────────
    var selectedTab by remember { mutableStateOf(0) }
    var textInput by remember { mutableStateOf("") }
    var isCapsOn by remember { mutableStateOf(false) }
    var isShiftOn by remember { mutableStateOf(false) }
    var ctrlHeld by remember { mutableStateOf(false) }
    var altHeld by remember { mutableStateOf(false) }

    // Custom shortcut builder state
    val customShortcuts = remember { mutableStateListOf<CustomShortcut>() }
    val builderSteps = remember { mutableStateListOf<TallyStep>() }
    var builderName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<CustomShortcut?>(null) }

    val isConnected = wifiStatus == WifiStatus.CONNECTED || currentDevice != null

    val tabs = listOf("QWERTY", "Tally", "Numpad", "Function", "Navigation", "My Shortcuts")

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    fun triggerKeyFeedback() {
        if (playSound) try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 22) } catch (_: Exception) {}
        if (vibratePref) try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vibrator?.vibrate(VibrationEffect.createOneShot(18, 100))
            else @Suppress("DEPRECATION") vibrator?.vibrate(18)
        } catch (_: Exception) {}
    }

    fun makeStep(type: TallyActionType, key: String = "", modifiers: List<String> = emptyList(), text: String = "") =
        TallyStep(type = type, key = key, modifiers = modifiers, textPayload = text, delayMs = 25L)

    fun sendStep(step: TallyStep) {
        triggerKeyFeedback()
        TallyAutomationEngine.sendDirectKeyAction(scope, step, controller, wifiManager)
    }

    fun sendAction(type: TallyActionType, key: String = "", modifiers: List<String> = emptyList(), text: String = "") {
        val step = makeStep(type, key, modifiers, text)
        sendStep(step)
        // If in builder tab, also append to builder
        if (selectedTab == 5 && type != TallyActionType.TYPE_TEXT) {
            builderSteps.add(step)
        }
    }

    fun sendChar(char: String) {
        val mods = mutableListOf<String>()
        if (ctrlHeld) mods.add("CTRL")
        if (altHeld) mods.add("ALT")
        val effectiveChar = if (isShiftOn || isCapsOn) char.uppercase() else char.lowercase()
        val step = makeStep(TallyActionType.PRESS_KEY, key = effectiveChar, modifiers = mods)
        sendStep(step)
        if (selectedTab == 5) builderSteps.add(step)
        if (isShiftOn) isShiftOn = false // shift is one-shot
    }

    fun sendTextDirect(text: String, withEnter: Boolean) {
        if (text.isBlank()) return
        triggerKeyFeedback()
        TallyAutomationEngine.sendDirectText(scope, text, withEnter, controller, wifiManager)
    }

    fun runCustomShortcut(shortcut: CustomShortcut) {
        triggerKeyFeedback()
        scope.launch {
            for (step in shortcut.steps) {
                TallyAutomationEngine.sendDirectKeyAction(scope, step, controller, wifiManager)
                kotlinx.coroutines.delay(step.delayMs.coerceAtLeast(30L))
            }
        }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1565C0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Keyboard, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("TALLY KEYBOARD REMOTE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (isConnected) "🟢 Connected · Live Keystrokes" else "🟠 Not Connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    SoundToggleAction()
                    IconButton(onClick = {
                        wifiManager.discoverPc(scope)
                        Toast.makeText(context, "Searching for Tally PC...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, "Reconnect")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)
        ) {
            // Connection Banner
            ConnectionBanner(isConnected, wifiStatus, pcHostname, serverIp, currentDevice?.name) {
                wifiManager.discoverPc(scope)
            }

            // Direct Text Bar (always visible)
            DirectTextBar(
                textInput = textInput,
                onTextChange = { textInput = it },
                onSend = {
                    sendTextDirect(textInput, true)
                    if (selectedTab == 5 && textInput.isNotBlank()) {
                        builderSteps.add(makeStep(TallyActionType.TYPE_TEXT, text = textInput))
                    }
                    textInput = ""
                },
                onClear = { textInput = "" }
            )

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    0 -> QwertyTab(
                        isCapsOn = isCapsOn,
                        isShiftOn = isShiftOn,
                        ctrlHeld = ctrlHeld,
                        altHeld = altHeld,
                        onCapsToggle = { isCapsOn = !isCapsOn },
                        onShiftToggle = { isShiftOn = !isShiftOn },
                        onCtrlToggle = { ctrlHeld = !ctrlHeld },
                        onAltToggle = { altHeld = !altHeld },
                        onChar = { sendChar(it) },
                        onSpecial = { type, key, mods -> sendAction(type, key, mods) }
                    )
                    1 -> TallyShortcutsTab(onAction = { t, k, m -> sendAction(t, k, m) })
                    2 -> NumpadTab(onText = { sendTextDirect(it, false) }, onAction = { t -> sendAction(t) })
                    3 -> FunctionKeysTab(onKey = { sendAction(TallyActionType.PRESS_FUNCTION_KEY, key = it) })
                    4 -> NavigationTab(onAction = { sendAction(it) })
                    5 -> CustomShortcutsTab(
                        shortcuts = customShortcuts,
                        builderSteps = builderSteps,
                        builderName = builderName,
                        onNameChange = { builderName = it },
                        onSave = {
                            if (builderName.isBlank()) {
                                Toast.makeText(context, "Shortcut ka naam likho!", Toast.LENGTH_SHORT).show()
                            } else if (builderSteps.isEmpty()) {
                                Toast.makeText(context, "Koi bhi key tap karo pehle!", Toast.LENGTH_SHORT).show()
                            } else {
                                showSaveDialog = true
                            }
                        },
                        onClearBuilder = { builderSteps.clear() },
                        onRun = { runCustomShortcut(it) },
                        onDelete = { showDeleteConfirm = it },
                        onRemoveStep = { idx -> if (idx in builderSteps.indices) builderSteps.removeAt(idx) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ─── Save Dialog ─────────────────────────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon = { Icon(Icons.Default.Save, null, tint = Color(0xFF1565C0)) },
            title = { Text("Shortcut Save Karo?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("\"$builderName\" naam se ${builderSteps.size} steps ka shortcut save hoga.")
                    Spacer(Modifier.height(8.dp))
                    builderSteps.forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. ${step.describe()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        customShortcuts.add(
                            CustomShortcut(
                                name = builderName,
                                steps = builderSteps.toList(),
                                color = listOf(
                                    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
                                    Color(0xFFE65100), Color(0xFF00695C)
                                ).random()
                            )
                        )
                        builderSteps.clear()
                        builderName = ""
                        showSaveDialog = false
                        Toast.makeText(context, "Shortcut save ho gaya! ✅", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Save Karo") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ─── Delete Confirm ───────────────────────────────────────────────────────────
    showDeleteConfirm?.let { sc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFD32F2F)) },
            title = { Text("Delete \"${sc.name}\"?") },
            text = { Text("Yeh shortcut permanently delete ho jayega.") },
            confirmButton = {
                Button(
                    onClick = { customShortcuts.remove(sc); showDeleteConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// CONNECTION BANNER
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun ConnectionBanner(
    isConnected: Boolean,
    wifiStatus: WifiStatus,
    pcHostname: String?,
    serverIp: String?,
    btName: String?,
    onConnect: () -> Unit
) {
    Surface(
        color = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null,
                    tint = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        wifiStatus == WifiStatus.CONNECTED -> "PC: ${pcHostname ?: "Tally PC"} · $serverIp"
                        btName != null -> "BT: $btName"
                        else -> "PC se connect nahi · Refresh tap karo"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) Color(0xFF1B5E20) else Color(0xFFBF360C)
                )
            }
            if (!isConnected) {
                Text(
                    "CONNECT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE3F2FD))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .clickable { onConnect() }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// DIRECT TEXT BAR
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun DirectTextBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                placeholder = { Text("Type barcode / text here...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    if (textInput.isNotEmpty())
                        IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                        }
                }
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("SEND", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 0 – FULL QWERTY KEYBOARD
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun QwertyTab(
    isCapsOn: Boolean,
    isShiftOn: Boolean,
    ctrlHeld: Boolean,
    altHeld: Boolean,
    onCapsToggle: () -> Unit,
    onShiftToggle: () -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onChar: (String) -> Unit,
    onSpecial: (TallyActionType, String, List<String>) -> Unit
) {
    val isUpper = isCapsOn || isShiftOn
    val activeModBg = Color(0xFF1565C0)
    val normalBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val modBgColor = { held: Boolean -> if (held) activeModBg else normalBg }
    val modTextColor = { held: Boolean -> if (held) Color.White else onSurfaceColor }

    // Number row + symbols
    val numberRow = listOf("1","2","3","4","5","6","7","8","9","0")
    val symbolRow = if (isShiftOn)
        listOf("!","@","#","\$","%","^","&","*","(",")","_","+")
    else
        listOf("`","-","=","[","]","\\",";","'",",",".","/")

    val qwertyRows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m")
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Modifier indicator strip
        if (ctrlHeld || altHeld || isShiftOn || isCapsOn) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1565C0)).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (ctrlHeld) ModChip("CTRL")
                if (altHeld) ModChip("ALT")
                if (isShiftOn) ModChip("SHIFT")
                if (isCapsOn) ModChip("CAPS")
            }
        }

        // Number row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            numberRow.forEach { n ->
                QKey(label = n, modifier = Modifier.weight(1f)) { onChar(n) }
            }
        }

        // Symbol row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            symbolRow.forEach { s ->
                QKey(label = s, modifier = Modifier.weight(1f)) { onChar(s) }
            }
        }

        // QWERTY rows
        qwertyRows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add CAPS on row 1 (a row), SHIFT on row 2 (z row)
                if (rowIdx == 1) {
                    // CAPS LOCK
                    QKey(
                        label = if (isCapsOn) "⇪ON" else "⇪",
                        modifier = Modifier.weight(1.5f),
                        bgColor = if (isCapsOn) Color(0xFF1565C0) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        textColor = if (isCapsOn) Color.White else MaterialTheme.colorScheme.onSurface
                    ) { onCapsToggle() }
                }
                if (rowIdx == 2) {
                    // SHIFT
                    QKey(
                        label = if (isShiftOn) "⇧ON" else "⇧",
                        modifier = Modifier.weight(1.5f),
                        bgColor = if (isShiftOn) Color(0xFF1565C0) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        textColor = if (isShiftOn) Color.White else MaterialTheme.colorScheme.onSurface
                    ) { onShiftToggle() }
                }

                row.forEach { c ->
                    val label = if (isUpper) c.uppercase() else c
                    QKey(label = label, modifier = Modifier.weight(1f)) { onChar(c) }
                }

                if (rowIdx == 2) {
                    // Backspace on the right of Z row
                    QKey(
                        label = "⌫",
                        modifier = Modifier.weight(1.5f),
                        bgColor = MaterialTheme.colorScheme.errorContainer,
                        textColor = MaterialTheme.colorScheme.onErrorContainer
                    ) { onSpecial(TallyActionType.PRESS_BACKSPACE, "", emptyList()) }
                }
            }
        }

        // Bottom control row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // CTRL
            QKey(
                label = "CTRL",
                modifier = Modifier.weight(1.4f),
                bgColor = modBgColor(ctrlHeld),
                textColor = modTextColor(ctrlHeld)
            ) { onCtrlToggle() }

            // ALT
            QKey(
                label = "ALT",
                modifier = Modifier.weight(1.2f),
                bgColor = modBgColor(altHeld),
                textColor = modTextColor(altHeld)
            ) { onAltToggle() }

            // SPACE
            QKey(
                label = "SPACE",
                modifier = Modifier.weight(3f),
                bgColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                textColor = MaterialTheme.colorScheme.onSurface
            ) { onSpecial(TallyActionType.PRESS_SPACE, "", emptyList()) }

            // TAB
            QKey(
                label = "TAB",
                modifier = Modifier.weight(1.2f),
                bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                textColor = MaterialTheme.colorScheme.onSurface
            ) { onSpecial(TallyActionType.PRESS_TAB, "", emptyList()) }

            // ENTER
            QKey(
                label = "↵",
                modifier = Modifier.weight(1.4f),
                bgColor = Color(0xFF2E7D32),
                textColor = Color.White
            ) { onSpecial(TallyActionType.PRESS_ENTER, "", emptyList()) }
        }

        // ESC + Delete row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QKey(
                label = "ESC",
                modifier = Modifier.weight(1f),
                bgColor = Color(0xFFD32F2F),
                textColor = Color.White
            ) { onSpecial(TallyActionType.PRESS_ESCAPE, "", emptyList()) }
            QKey(
                label = "DEL",
                modifier = Modifier.weight(1f),
                bgColor = MaterialTheme.colorScheme.errorContainer,
                textColor = MaterialTheme.colorScheme.onErrorContainer
            ) { onSpecial(TallyActionType.PRESS_KEY, "DELETE", emptyList()) }
            QKey(label = "HOME", modifier = Modifier.weight(1f)) { onSpecial(TallyActionType.PRESS_KEY, "HOME", emptyList()) }
            QKey(label = "END", modifier = Modifier.weight(1f)) { onSpecial(TallyActionType.PRESS_KEY, "END", emptyList()) }
            QKey(label = "INS", modifier = Modifier.weight(1f)) { onSpecial(TallyActionType.PRESS_KEY, "INSERT", emptyList()) }
        }
    }
}

@Composable
private fun ModChip(label: String) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun QKey(
    label: String,
    modifier: Modifier = Modifier,
    bgColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (label.length > 3) 9.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 1 – TALLY QUICK SHORTCUTS
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun TallyShortcutsTab(onAction: (TallyActionType, String, List<String>) -> Unit) {
    val shortcuts = listOf(
        Triple("Alt + A", "Accept Voucher", Pair("A", listOf("ALT"))),
        Triple("Alt + C", "Create / Master", Pair("C", listOf("ALT"))),
        Triple("Alt + D", "Delete Voucher", Pair("D", listOf("ALT"))),
        Triple("Alt + E", "Export", Pair("E", listOf("ALT"))),
        Triple("Alt + G", "Go To", Pair("G", listOf("ALT"))),
        Triple("Alt + H", "Help", Pair("H", listOf("ALT"))),
        Triple("Alt + I", "More Details", Pair("I", listOf("ALT"))),
        Triple("Alt + M", "Email", Pair("M", listOf("ALT"))),
        Triple("Alt + N", "Contra Voucher", Pair("N", listOf("ALT"))),
        Triple("Alt + P", "Print", Pair("P", listOf("ALT"))),
        Triple("Alt + R", "Remove Line", Pair("R", listOf("ALT"))),
        Triple("Alt + S", "Invoice Mode", Pair("S", listOf("ALT"))),
        Triple("Alt + W", "Browse Web", Pair("W", listOf("ALT"))),
        Triple("Alt + X", "Cancel Voucher", Pair("X", listOf("ALT"))),
        Triple("Ctrl + A", "Select All / Save", Pair("A", listOf("CTRL"))),
        Triple("Ctrl + B", "Bold Toggle", Pair("B", listOf("CTRL"))),
        Triple("Ctrl + C", "Copy", Pair("C", listOf("CTRL"))),
        Triple("Ctrl + E", "Centre", Pair("E", listOf("CTRL"))),
        Triple("Ctrl + F", "Filter / Search", Pair("F", listOf("CTRL"))),
        Triple("Ctrl + G", "Group Totals", Pair("G", listOf("CTRL"))),
        Triple("Ctrl + H", "Toggle Mode", Pair("H", listOf("CTRL"))),
        Triple("Ctrl + I", "Stock Query", Pair("I", listOf("CTRL"))),
        Triple("Ctrl + N", "New Num Series", Pair("N", listOf("CTRL"))),
        Triple("Ctrl + P", "Print", Pair("P", listOf("CTRL"))),
        Triple("Ctrl + Q", "Quit", Pair("Q", listOf("CTRL"))),
        Triple("Ctrl + R", "Repeat Narration", Pair("R", listOf("CTRL"))),
        Triple("Ctrl + V", "Paste", Pair("V", listOf("CTRL"))),
        Triple("Ctrl + X", "Cut", Pair("X", listOf("CTRL"))),
        Triple("Ctrl + Z", "Undo", Pair("Z", listOf("CTRL"))),
        Triple("Alt+F1", "Detailed", Pair("F1", listOf("ALT"))),
        Triple("Alt+F2", "Period", Pair("F2", listOf("ALT"))),
        Triple("Alt+F12", "Range Filter", Pair("F12", listOf("ALT"))),
        Triple("Ctrl+F4", "New Voucher", Pair("F4", listOf("CTRL"))),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Tally Complete Shortcut Reference",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        shortcuts.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (label, desc, action) ->
                    ElevatedCard(
                        modifier = Modifier.weight(1f).height(52.dp).clickable {
                            onAction(TallyActionType.PRESS_SHORTCUT, action.first, action.second)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                            Text(desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 2 – NUMPAD
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun NumpadTab(onText: (String) -> Unit, onAction: (TallyActionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Number Pad", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        listOf(
            listOf("7","8","9","/"),
            listOf("4","5","6","*"),
            listOf("1","2","3","-"),
            listOf("0","00",".","+")
        ).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { k ->
                    val isOp = k in listOf("/","*","-","+")
                    Card(
                        modifier = Modifier.weight(1f).height(56.dp).clickable { onText(k) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOp) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(k, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                color = if (isOp) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        // Enter + Backspace row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Card(
                modifier = Modifier.weight(2f).height(50.dp).clickable { onAction(TallyActionType.PRESS_ENTER) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ENTER ↵", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Card(
                modifier = Modifier.weight(1f).height(50.dp).clickable { onAction(TallyActionType.PRESS_BACKSPACE) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("⌫", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Card(
                modifier = Modifier.weight(1f).height(50.dp).clickable { onAction(TallyActionType.PRESS_TAB) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("TAB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 3 – FUNCTION KEYS
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun FunctionKeysTab(onKey: (String) -> Unit) {
    val fKeys = listOf(
        "F1" to "Company","F2" to "Date","F3" to "Change Co","F4" to "Contra",
        "F5" to "Payment","F6" to "Receipt","F7" to "Journal","F8" to "Sales",
        "F9" to "Purchase","F10" to "Vouchers","F11" to "Features","F12" to "Config"
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Function Keys (F1-F12)", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        fKeys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { (key, role) ->
                    ElevatedCard(
                        modifier = Modifier.weight(1f).height(60.dp).clickable { onKey(key) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(key, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                            Text(role, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 4 – NAVIGATION
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun NavigationTab(onAction: (TallyActionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tally Menu Navigation", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        // Arrow cross
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()) {
            NavBtn("▲  UP", Color(0xFF1565C0)) { onAction(TallyActionType.PRESS_UP) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()) {
                NavBtn("◄ LEFT", Color(0xFF1565C0), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_LEFT) }
                ElevatedCard(
                    modifier = Modifier.size(60.dp).clickable { onAction(TallyActionType.PRESS_ENTER) },
                    shape = CircleShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF2E7D32))
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("↵", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }}
                NavBtn("RIGHT ►", Color(0xFF1565C0), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_RIGHT) }
            }
            NavBtn("▼  DOWN", Color(0xFF1565C0)) { onAction(TallyActionType.PRESS_DOWN) }
        }
        // Page keys row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavBtn("⇞ Page Up", Color(0xFF37474F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_PAGE_UP) }
            NavBtn("⇟ Page Down", Color(0xFF37474F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_PAGE_DOWN) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NavBtn("HOME", Color(0xFF37474F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_KEY) }
            NavBtn("END", Color(0xFF37474F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_KEY) }
            NavBtn("ESC", Color(0xFFD32F2F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_ESCAPE) }
            NavBtn("TAB", Color(0xFF37474F), Modifier.weight(1f)) { onAction(TallyActionType.PRESS_TAB) }
        }
    }
}

@Composable
private fun NavBtn(label: String, bg: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    ElevatedCard(
        modifier = modifier.height(50.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = bg)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// TAB 5 – CUSTOM SHORTCUTS (Real-time builder)
// ════════════════════════════════════════════════════════════════════════════════
@Composable
private fun CustomShortcutsTab(
    shortcuts: List<CustomShortcut>,
    builderSteps: List<TallyStep>,
    builderName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearBuilder: () -> Unit,
    onRun: (CustomShortcut) -> Unit,
    onDelete: (CustomShortcut) -> Unit,
    onRemoveStep: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── SAVED SHORTCUTS ──────────────────────────────────────────────────────
        if (shortcuts.isNotEmpty()) {
            Text("Saved Shortcuts", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            shortcuts.forEach { sc ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = sc.color.copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(sc.color))
                                Spacer(Modifier.width(8.dp))
                                Text(sc.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = sc.color)
                            }
                            Row {
                                // Run button
                                Button(
                                    onClick = { onRun(sc) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("RUN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(4.dp))
                                // Delete button
                                IconButton(onClick = { onDelete(sc) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            sc.steps.joinToString(" → ") { it.describe() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── REAL-TIME BUILDER ────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Real-Time Shortcut Builder", fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0), fontSize = 13.sp)
                }

                Text(
                    "Steps recorded: ${builderSteps.size}  •  Koi bhi keyboard tab me key tap karo — woh yahan record hoga ✅",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live step preview
                if (builderSteps.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            builderSteps.forEachIndexed { i, step ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${i + 1}",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1565C0)),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(step.describe(), fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { onRemoveStep(i) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "👆 Upar koi bhi tab (QWERTY / Tally / Numpad / Function / Navigation) me keys tap karo.\nSab yahan record hoga!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Name field
                OutlinedTextField(
                    value = builderName,
                    onValueChange = onNameChange,
                    label = { Text("Shortcut ka naam likho (e.g. Purchase Entry)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = { Icon(Icons.Default.Star, null, tint = Color(0xFFF9A825), modifier = Modifier.size(18.dp)) }
                )

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onClearBuilder,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        enabled = builderSteps.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Shortcut", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── QUICK ADD COMMON TALLY STEPS ────────────────────────────────────────
        Text("Quick Steps Add Karo", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Builder me directly steps add karne ke liye tap karo:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier, shape = shape, enabled = enabled
    ) { content() }
}
