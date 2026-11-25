package dev.fabik.bluetoothhid.ui.screens

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

/**
 * Full Wireless Tally Keyboard Remote.
 * Converts the Android phone into an instant tactile wireless keyboard for TallyPrime.
 * Works seamlessly over Wi-Fi (zero PC setup) or Bluetooth HID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyKeyboardScreen(
    onNavigateBack: () -> Unit
) {
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

    // Audio & Haptic feedback on keyboard press
    val toneGen = remember {
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60) } catch (_: Exception) { null }
    }
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    DisposableEffect(Unit) {
        onDispose {
            try { toneGen?.release() } catch (_: Exception) {}
        }
    }

    fun triggerKeyFeedback() {
        if (playSound) {
            try { toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 25) } catch (_: Exception) {}
        }
        if (vibratePref) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, 90))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            } catch (_: Exception) {}
        }
    }

    fun sendAction(
        type: TallyActionType,
        key: String = "",
        modifiers: List<String> = emptyList(),
        text: String = ""
    ) {
        triggerKeyFeedback()
        val step = TallyStep(type = type, key = key, modifiers = modifiers, textPayload = text, delayMs = 25L)
        TallyAutomationEngine.sendDirectKeyAction(scope, step, controller, wifiManager)
    }

    fun sendTextDirect(text: String, withEnter: Boolean) {
        if (text.isBlank()) return
        triggerKeyFeedback()
        TallyAutomationEngine.sendDirectText(scope, text, withEnter, controller, wifiManager)
    }

    var textInput by remember { mutableStateOf("") }
    val isConnected = wifiStatus == WifiStatus.CONNECTED || currentDevice != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1565C0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "TALLY WIRELESS KEYBOARD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isConnected) "🟢 Live Keystroke Remote • Connected" else "🟠 Connecting to PC...",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    SoundToggleAction()
                    IconButton(onClick = {
                        wifiManager.discoverPc(scope)
                        Toast.makeText(context, "Searching for PC on Wi-Fi...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Connection Status Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isConnected) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (wifiStatus == WifiStatus.CONNECTED) {
                                "PC: ${pcHostname ?: "Tally PC"} ($serverIp)"
                            } else if (currentDevice != null) {
                                "BT: ${currentDevice?.name ?: "PC Connected"}"
                            } else {
                                "Not Connected. Tap refresh or Wi-Fi scan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isConnected) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                    }

                    if (!isConnected) {
                        Text(
                            text = "CONNECT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE3F2FD))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable { wifiManager.discoverPc(scope) }
                        )
                    }
                }
            }

            // 2. Direct Barcode / Text Typing Field
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Type Barcode / Part No / Text...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            trailingIcon = {
                                if (textInput.isNotEmpty()) {
                                    IconButton(onClick = { textInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                sendTextDirect(textInput, withEnter = true)
                                textInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ENTER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Essential Tally Special Actions (High-Visibility Touch Buttons)
            Text(
                "Tally Quick Shortcuts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton(
                    label = "Alt + A",
                    subtext = "Accept",
                    bgColor = Color(0xFF2E7D32),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_SHORTCUT, key = "A", modifiers = listOf("ALT")) }
                )
                KeyButton(
                    label = "Alt + C",
                    subtext = "Create",
                    bgColor = Color(0xFF1565C0),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_SHORTCUT, key = "C", modifiers = listOf("ALT")) }
                )
                KeyButton(
                    label = "Ctrl + A",
                    subtext = "Quick Save",
                    bgColor = Color(0xFF6A1B9A),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_SHORTCUT, key = "A", modifiers = listOf("CTRL")) }
                )
                KeyButton(
                    label = "ESC",
                    subtext = "Back",
                    bgColor = Color(0xFFD32F2F),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_ESCAPE) }
                )
            }

            // Second Tally Actions Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyButton(
                    label = "ENTER ↵",
                    subtext = "Next Field",
                    bgColor = Color(0xFF00695C),
                    textColor = Color.White,
                    modifier = Modifier.weight(1.3f),
                    onClick = { sendAction(TallyActionType.PRESS_ENTER) }
                )
                KeyButton(
                    label = "TAB ⇥",
                    subtext = "Tab",
                    bgColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_TAB) }
                )
                KeyButton(
                    label = "SPACE ␣",
                    subtext = "Select",
                    bgColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_SPACE) }
                )
                KeyButton(
                    label = "BKSP ⌫",
                    subtext = "Delete",
                    bgColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = { sendAction(TallyActionType.PRESS_BACKSPACE) }
                )
            }

            // 4. Function Keys Ribbon (F1 - F12)
            Text(
                "Tally Function Keys (F1 - F12)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            val functionKeys = listOf(
                Pair("F1", "Company"),
                Pair("F2", "Date"),
                Pair("F3", "Change Co"),
                Pair("F4", "Contra"),
                Pair("F5", "Payment"),
                Pair("F6", "Receipt"),
                Pair("F7", "Journal"),
                Pair("F8", "Sales"),
                Pair("F9", "Purchase"),
                Pair("F10", "Vouchers"),
                Pair("F11", "Features"),
                Pair("F12", "Config")
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(functionKeys) { (fKey, role) ->
                    Card(
                        modifier = Modifier
                            .width(76.dp)
                            .height(52.dp)
                            .clickable {
                                sendAction(TallyActionType.PRESS_FUNCTION_KEY, key = fKey)
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = borderStroke()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(fKey, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Text(role, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 5. Full Numeric Keypad + Arithmetic Operators
            Text(
                "Number Pad (संख्या पॅड)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: 7, 8, 9, /
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumpadKey("7", Modifier.weight(1f)) { sendTextDirect("7", false) }
                    NumpadKey("8", Modifier.weight(1f)) { sendTextDirect("8", false) }
                    NumpadKey("9", Modifier.weight(1f)) { sendTextDirect("9", false) }
                    OperatorKey("/", Modifier.weight(0.9f)) { sendTextDirect("/", false) }
                }
                // Row 2: 4, 5, 6, *
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumpadKey("4", Modifier.weight(1f)) { sendTextDirect("4", false) }
                    NumpadKey("5", Modifier.weight(1f)) { sendTextDirect("5", false) }
                    NumpadKey("6", Modifier.weight(1f)) { sendTextDirect("6", false) }
                    OperatorKey("*", Modifier.weight(0.9f)) { sendTextDirect("*", false) }
                }
                // Row 3: 1, 2, 3, -
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumpadKey("1", Modifier.weight(1f)) { sendTextDirect("1", false) }
                    NumpadKey("2", Modifier.weight(1f)) { sendTextDirect("2", false) }
                    NumpadKey("3", Modifier.weight(1f)) { sendTextDirect("3", false) }
                    OperatorKey("-", Modifier.weight(0.9f)) { sendTextDirect("-", false) }
                }
                // Row 4: 0, 00, ., +
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumpadKey("0", Modifier.weight(1f)) { sendTextDirect("0", false) }
                    NumpadKey("00", Modifier.weight(1f)) { sendTextDirect("00", false) }
                    NumpadKey(".", Modifier.weight(1f)) { sendTextDirect(".", false) }
                    OperatorKey("+", Modifier.weight(0.9f)) { sendTextDirect("+", false) }
                }
            }

            // 6. Navigation Pad (Arrows & Page Up / Down)
            Text(
                "Tally Menu Navigation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Page navigation
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton(
                        label = "PgUp",
                        subtext = "Page Up",
                        bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { sendAction(TallyActionType.PRESS_PAGE_UP) }
                    )
                    KeyButton(
                        label = "PgDn",
                        subtext = "Page Down",
                        bgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { sendAction(TallyActionType.PRESS_PAGE_DOWN) }
                    )
                }

                // Right side: Direction cross
                Column(
                    modifier = Modifier.weight(2f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NavArrowKey(Icons.Default.ArrowUpward, "Up") { sendAction(TallyActionType.PRESS_UP) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NavArrowKey(Icons.AutoMirrored.Filled.ArrowBack, "Left") { sendAction(TallyActionType.PRESS_LEFT) }
                        NavArrowKey(Icons.Default.Check, "Enter", isEnter = true) { sendAction(TallyActionType.PRESS_ENTER) }
                        NavArrowKey(Icons.AutoMirrored.Filled.ArrowForward, "Right") { sendAction(TallyActionType.PRESS_RIGHT) }
                    }
                    NavArrowKey(Icons.Default.ArrowDownward, "Down") { sendAction(TallyActionType.PRESS_DOWN) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
)

@Composable
private fun KeyButton(
    label: String,
    subtext: String = "",
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(54.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
            if (subtext.isNotEmpty()) {
                Text(subtext, fontSize = 9.sp, color = textColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun NumpadKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        border = borderStroke()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun OperatorKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun NavArrowKey(
    icon: ImageVector,
    desc: String,
    isEnter: Boolean = false,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isEnter) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = if (isEnter) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
