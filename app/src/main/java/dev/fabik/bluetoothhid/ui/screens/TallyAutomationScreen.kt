package dev.fabik.bluetoothhid.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.tally.AutomationMode
import dev.fabik.bluetoothhid.tally.TallyActionType
import dev.fabik.bluetoothhid.tally.TallyAutomationEngine
import dev.fabik.bluetoothhid.tally.TallyProfile
import dev.fabik.bluetoothhid.tally.TallyProfileRepository
import dev.fabik.bluetoothhid.tally.TallyStep
import dev.fabik.bluetoothhid.tally.WifiTallyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyAutomationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = LocalController.current
    val repo = remember { TallyProfileRepository.getInstance(context) }
    val clipboardManager = LocalClipboardManager.current

    val profiles by repo.profiles.collectAsStateWithLifecycle()
    val activeProfile by repo.activeProfile.collectAsStateWithLifecycle()
    val automationMode by repo.automationMode.collectAsStateWithLifecycle()

    val isExecuting by TallyAutomationEngine.isExecuting.collectAsStateWithLifecycle()
    val currentStepSummary by TallyAutomationEngine.currentStepSummary.collectAsStateWithLifecycle()

    var showProfileDropdown by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<Pair<Int, TallyStep>?>(null) }
    var showAddStepDialog by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var jsonDialogText by remember { mutableStateOf("") }
    var isImportMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TallyPrime Automation", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Programmable Keyboard Sequence",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Emergency Stop Button
                    IconButton(
                        onClick = {
                            TallyAutomationEngine.emergencyStop(controller?.keyboardSender)
                            Toast.makeText(context, "EMERGENCY STOPPED!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD32F2F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "STOP", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111827), titleContentColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Running Execution Banner
            if (isExecuting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Yellow)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Executing Shortcut Sequence...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(currentStepSummary ?: "Processing...", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                            }
                            Button(
                                onClick = { TallyAutomationEngine.emergencyStop(controller?.keyboardSender) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("STOP", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. Profile Selector Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Automation Profile", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { showProfileDropdown = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(activeProfile.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                Text(activeProfile.description.ifEmpty { "Custom Tally Sequence" }, fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Text("Change ▼", fontSize = 12.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)

                            DropdownMenu(
                                expanded = showProfileDropdown,
                                onDismissRequest = { showProfileDropdown = false }
                            ) {
                                profiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (p.id == activeProfile.id) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(p.name, fontWeight = if (p.id == activeProfile.id) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        },
                                        onClick = {
                                            repo.setActiveProfile(p.id)
                                            showProfileDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Profile Actions Row
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showNewProfileDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Profile", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val copy = repo.duplicateProfile(activeProfile.id)
                                    if (copy != null) repo.setActiveProfile(copy.id)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Duplicate", fontSize = 11.sp)
                            }
                            if (profiles.size > 1) {
                                OutlinedButton(
                                    onClick = { repo.deleteProfile(activeProfile.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Automation Mode Switcher (Manual / Automatic / Barcode Only)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Automation Mode", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AutomationMode.values().forEach { mode ->
                                val selected = mode == automationMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .clickable { repo.setAutomationMode(mode) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        mode.displayName.split(" ")[0],
                                        color = if (selected) Color.White else Color(0xFF475569),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(automationMode.description, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // 4. Sequence Steps Title & Add Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Keyboard Action Steps (${activeProfile.steps.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )
                    Button(
                        onClick = { showAddStepDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Step", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. Sequence Steps List
            itemsIndexed(activeProfile.steps) { index, step ->
                StepItemCard(
                    stepNumber = index + 1,
                    step = step,
                    isFirst = index == 0,
                    isLast = index == activeProfile.steps.size - 1,
                    onMoveUp = {
                        val list = activeProfile.steps.toMutableList()
                        if (index > 0) {
                            val temp = list[index]
                            list[index] = list[index - 1]
                            list[index - 1] = temp
                            repo.saveProfile(activeProfile.copy(steps = list))
                        }
                    },
                    onMoveDown = {
                        val list = activeProfile.steps.toMutableList()
                        if (index < list.size - 1) {
                            val temp = list[index]
                            list[index] = list[index + 1]
                            list[index + 1] = temp
                            repo.saveProfile(activeProfile.copy(steps = list))
                        }
                    },
                    onToggle = { enabled ->
                        val list = activeProfile.steps.toMutableList()
                        list[index] = step.copy(isEnabled = enabled)
                        repo.saveProfile(activeProfile.copy(steps = list))
                    },
                    onEdit = { editingStep = Pair(index, step) },
                    onDelete = {
                        val list = activeProfile.steps.toMutableList()
                        list.removeAt(index)
                        repo.saveProfile(activeProfile.copy(steps = list))
                    }
                )
            }

            // 6. Test Sequence & JSON Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Test & Backup Profiles", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)

                        Button(
                            onClick = {
                                val wifiManager = WifiTallyManager.getInstance(context)
                                if (wifiManager.isWifiMode.value) {
                                    scope.launch {
                                        val (success, msg) = wifiManager.sendBarcode("8904235100018", activeProfile)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, if (success) "WiFi Tally: $msg" else "WiFi Error: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (controller?.keyboardSender == null) {
                                    Toast.makeText(context, "Connect to Windows PC via Bluetooth first!", Toast.LENGTH_LONG).show()
                                } else {
                                    TallyAutomationEngine.executeProfileSequence(
                                        scope = scope,
                                        barcode = "8904235100018",
                                        profile = activeProfile,
                                        controller = controller
                                    ) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Sequence with Sample Barcode (8904235100018)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    jsonDialogText = repo.exportProfilesJson()
                                    isImportMode = false
                                    showJsonDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export JSON", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    jsonDialogText = ""
                                    isImportMode = true
                                    showJsonDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import JSON", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    repo.restoreDefaults()
                                    Toast.makeText(context, "Restored default profiles!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Adding / Editing a Step
    if (showAddStepDialog || editingStep != null) {
        val initialStep = editingStep?.second ?: TallyStep()
        StepEditorDialog(
            initialStep = initialStep,
            isNew = showAddStepDialog,
            onDismiss = {
                showAddStepDialog = false
                editingStep = null
            },
            onSave = { step ->
                val list = activeProfile.steps.toMutableList()
                if (editingStep != null) {
                    list[editingStep!!.first] = step
                } else {
                    list.add(step)
                }
                repo.saveProfile(activeProfile.copy(steps = list))
                showAddStepDialog = false
                editingStep = null
            }
        )
    }

    // Dialog for creating a New Profile
    if (showNewProfileDialog) {
        var profileName by remember { mutableStateOf("") }
        var profileDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Create Automation Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name (e.g. Sales Entry)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileDesc,
                        onValueChange = { profileDesc = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (profileName.isNotBlank()) {
                            val newProfile = TallyProfile(
                                name = profileName.trim(),
                                description = profileDesc.trim(),
                                steps = listOf(
                                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 150L),
                                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 150L)
                                )
                            )
                            repo.saveProfile(newProfile)
                            repo.setActiveProfile(newProfile.id)
                            showNewProfileDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog for JSON Export / Import
    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text(if (isImportMode) "Import Profiles JSON" else "Export Profiles JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = jsonDialogText,
                        onValueChange = { if (isImportMode) jsonDialogText = it },
                        readOnly = !isImportMode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                if (isImportMode) {
                    Button(
                        onClick = {
                            val ok = repo.importProfilesJson(jsonDialogText)
                            if (ok) {
                                Toast.makeText(context, "Profiles imported successfully!", Toast.LENGTH_SHORT).show()
                                showJsonDialog = false
                            } else {
                                Toast.makeText(context, "Invalid JSON format!", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) { Text("Import") }
                } else {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jsonDialogText))
                            Toast.makeText(context, "Copied JSON to Clipboard!", Toast.LENGTH_SHORT).show()
                            showJsonDialog = false
                        }
                    ) { Text("Copy to Clipboard") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun StepItemCard(
    stepNumber: Int,
    step: TallyStep,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (step.isEnabled) Color.White else Color(0xFFF1F5F9)
        ),
        elevation = CardDefaults.cardElevation(if (step.isEnabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step Number Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (step.isEnabled) Color(0xFFD32F2F) else Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("$stepNumber", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Step Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    step.getSummaryText(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (step.isEnabled) Color(0xFF1E293B) else Color.Gray
                )
                Text(
                    "Action: ${step.type.displayName} • Wait: ${step.delayMs}ms",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            // Controls: Move Up/Down, Edit, Delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
                Checkbox(
                    checked = step.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun StepEditorDialog(
    initialStep: TallyStep,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (TallyStep) -> Unit
) {
    var type by remember { mutableStateOf(initialStep.type) }
    var key by remember { mutableStateOf(initialStep.key) }
    var delayMs by remember { mutableStateOf(initialStep.delayMs) }
    var textPayload by remember { mutableStateOf(initialStep.textPayload) }
    var ctrl by remember { mutableStateOf(initialStep.modifiers.contains("CTRL")) }
    var alt by remember { mutableStateOf(initialStep.modifiers.contains("ALT")) }
    var shift by remember { mutableStateOf(initialStep.modifiers.contains("SHIFT")) }

    var showTypeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Automation Step" else "Edit Step", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Action Type Selector
                Column {
                    Text("Action Type:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .clickable { showTypeDropdown = true }
                            .padding(12.dp)
                    ) {
                        Text(type.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        DropdownMenu(expanded = showTypeDropdown, onDismissRequest = { showTypeDropdown = false }) {
                            TallyActionType.values().forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.displayName) },
                                    onClick = {
                                        type = t
                                        showTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Modifiers (Only for PRESS_SHORTCUT)
                if (type == TallyActionType.PRESS_SHORTCUT) {
                    Text("Modifiers:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = alt, onCheckedChange = { alt = it })
                            Text("ALT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = ctrl, onCheckedChange = { ctrl = it })
                            Text("CTRL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = shift, onCheckedChange = { shift = it })
                            Text("SHIFT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Target Key (For Function Key or Shortcut)
                if (type in listOf(TallyActionType.PRESS_SHORTCUT, TallyActionType.PRESS_FUNCTION_KEY)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.uppercase().trim() },
                        label = { Text("Key (e.g. A, C, V, F12, ENTER)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Custom Text (For TYPE_TEXT)
                if (type == TallyActionType.TYPE_TEXT) {
                    OutlinedTextField(
                        value = textPayload,
                        onValueChange = { textPayload = it },
                        label = { Text("Custom Text String") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Post-Action Delay Slider
                Column {
                    Text("Wait Delay: ${delayMs}ms", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Slider(
                        value = delayMs.toFloat(),
                        onValueChange = { delayMs = it.toLong() },
                        valueRange = 50f..2000f,
                        steps = 39
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mods = mutableListOf<String>()
                    if (alt) mods.add("ALT")
                    if (ctrl) mods.add("CTRL")
                    if (shift) mods.add("SHIFT")

                    onSave(
                        initialStep.copy(
                            type = type,
                            key = key,
                            modifiers = mods,
                            delayMs = delayMs,
                            textPayload = textPayload
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
