package dev.fabik.bluetoothhid.tally

import android.util.Log
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.KeyTranslator
import dev.fabik.bluetoothhid.bt.KeyboardSender
import dev.fabik.bluetoothhid.utils.ExtraKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object TallyAutomationEngine {
    private const val TAG = "TallyAutomationEngine"
    private const val MAX_SEQUENCE_TIMEOUT_MS = 20_000L

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(-1)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _currentStepSummary = MutableStateFlow<String?>(null)
    val currentStepSummary: StateFlow<String?> = _currentStepSummary.asStateFlow()

    private val _lastSentBarcode = MutableStateFlow<String?>(null)
    val lastSentBarcode: StateFlow<String?> = _lastSentBarcode.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    /**
     * Emergency Stop: immediately halts the running automation sequence
     * and releases any held keyboard modifier keys (Ctrl/Alt/Shift).
     */
    fun emergencyStop(keyboardSender: KeyboardSender?) {
        Log.w(TAG, "EMERGENCY STOP TRIGGERED!")
        activeJob?.cancel()
        activeJob = null
        _isExecuting.value = false
        _currentStepIndex.value = -1
        _currentStepSummary.value = null
        _statusMessage.value = "Emergency Stopped by User"

        // Force release all held keys
        keyboardSender?.let { sender ->
            CoroutineScope(Dispatchers.IO).launch {
                repeat(3) {
                    sender.sendKey(0, 0, releaseKey = false)
                    delay(30)
                }
            }
        }
    }

    /**
     * Executes the complete TallyProfile sequence for [scannedBarcode].
     */
    fun executeProfileSequence(
        scope: CoroutineScope,
        barcode: String,
        profile: TallyProfile,
        controller: BluetoothController,
        layout: String = "us",
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (_isExecuting.value) {
            Log.w(TAG, "Automation already executing! Ignoring overlapping scan.")
            onComplete?.invoke(false, "Another automation sequence is currently running.")
            return
        }

        val sender = controller.keyboardSender
        val currentDevice = controller.currentDevice.value

        if (currentDevice == null || sender == null) {
            _statusMessage.value = "Bluetooth Host Disconnected!"
            onComplete?.invoke(false, "Bluetooth PC is not connected. Connect via Bluetooth first.")
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            _isExecuting.value = true
            _lastSentBarcode.value = barcode
            _statusMessage.value = "Executing Profile: ${profile.name}"

            try {
                withTimeout(MAX_SEQUENCE_TIMEOUT_MS) {
                    val enabledSteps = profile.steps.filter { it.isEnabled }
                    for ((index, step) in enabledSteps.withIndex()) {
                        // Check if Bluetooth disconnected during sequence
                        if (controller.currentDevice.value == null) {
                            throw IllegalStateException("Bluetooth host disconnected mid-sequence!")
                        }

                        _currentStepIndex.value = index
                        _currentStepSummary.value = "[${index + 1}/${enabledSteps.size}] ${step.getSummaryText()}"

                        executeSingleStep(step, barcode, sender, layout)
                    }
                }

                _statusMessage.value = "Completed Profile: ${profile.name}"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, "Completed ${profile.name}")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Automation sequence cancelled by user.")
                _statusMessage.value = "Sequence Cancelled"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, "Sequence Cancelled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing sequence: ${e.message}", e)
                _statusMessage.value = "Error: ${e.message}"
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Automation failed")
                }
            } finally {
                // Guaranteed safety cleanup: ALWAYS release all keys at end
                repeat(2) {
                    sender.sendKey(0, 0, releaseKey = false)
                    delay(20)
                }
                _isExecuting.value = false
                _currentStepIndex.value = -1
                _currentStepSummary.value = null
                activeJob = null
            }
        }
    }

    private suspend fun executeSingleStep(
        step: TallyStep,
        barcode: String,
        sender: KeyboardSender,
        layout: String
    ) {
        when (step.type) {
            TallyActionType.TYPE_BARCODE -> {
                Log.d(TAG, "Typing Barcode: $barcode")
                sender.sendProcessedString(barcode, 10, ExtraKeys.NONE, layout)
                delay(step.delayMs)
            }

            TallyActionType.PRESS_ENTER -> {
                Log.d(TAG, "Pressing ENTER")
                sendKeyDirect(sender, 0u, 0x28u, step.delayMs)
            }

            TallyActionType.PRESS_TAB -> {
                Log.d(TAG, "Pressing TAB")
                sendKeyDirect(sender, 0u, 0x2Bu, step.delayMs)
            }

            TallyActionType.PRESS_ESCAPE -> {
                Log.d(TAG, "Pressing ESCAPE")
                sendKeyDirect(sender, 0u, 0x29u, step.delayMs)
            }

            TallyActionType.PRESS_BACKSPACE -> {
                Log.d(TAG, "Pressing BACKSPACE")
                sendKeyDirect(sender, 0u, 0x2Au, step.delayMs)
            }

            TallyActionType.PRESS_SPACE -> {
                Log.d(TAG, "Pressing SPACE")
                sendKeyDirect(sender, 0u, 0x2Cu, step.delayMs)
            }

            TallyActionType.PRESS_FUNCTION_KEY -> {
                val fScanCode = getFunctionKeyScanCode(step.key)
                Log.d(TAG, "Pressing Function Key ${step.key} (0x${fScanCode.toString(16)})")
                sendKeyDirect(sender, 0u, fScanCode, step.delayMs)
            }

            TallyActionType.PRESS_SHORTCUT -> {
                val modifierByte = calculateModifierByte(step.modifiers)
                val keyScanCode = getScanCodeForKeyName(step.key)
                Log.d(TAG, "Pressing Shortcut: mods=$modifierByte key=$keyScanCode (${step.key})")
                sendKeyDirect(sender, modifierByte, keyScanCode, step.delayMs)
            }

            TallyActionType.WAIT -> {
                Log.d(TAG, "Waiting ${step.delayMs}ms")
                delay(step.delayMs)
            }

            TallyActionType.TYPE_TEXT -> {
                if (step.textPayload.isNotEmpty()) {
                    Log.d(TAG, "Typing Text: ${step.textPayload}")
                    sender.sendProcessedString(step.textPayload, 10, ExtraKeys.NONE, layout)
                    delay(step.delayMs)
                }
            }

            TallyActionType.END_SEQUENCE -> {
                Log.d(TAG, "END_SEQUENCE reached.")
            }
        }
    }

    private suspend fun sendKeyDirect(
        sender: KeyboardSender,
        modifier: UByte,
        scanCode: UByte,
        postDelayMs: Long
    ) {
        // Send key down with modifiers
        sender.sendKey(modifier.toByte(), scanCode.toByte(), releaseKey = true, releaseDelay = 40)
        delay(postDelayMs)
    }

    private fun calculateModifierByte(modifiers: List<String>): UByte {
        var mod: UByte = 0u
        for (m in modifiers) {
            when (m.uppercase()) {
                "CTRL", "LCTRL", "CONTROL" -> mod = mod or KeyTranslator.LCTRL
                "ALT", "LALT" -> mod = mod or KeyTranslator.LALT
                "SHIFT", "LSHIFT" -> mod = mod or KeyTranslator.LSHIFT
            }
        }
        return mod
    }

    private fun getFunctionKeyScanCode(keyName: String): UByte {
        return when (keyName.uppercase().trim()) {
            "F1" -> 0x3Au
            "F2" -> 0x3Bu
            "F3" -> 0x3Cu
            "F4" -> 0x3Du
            "F5" -> 0x3Eu
            "F6" -> 0x3Fu
            "F7" -> 0x40u
            "F8" -> 0x41u
            "F9" -> 0x42u
            "F10" -> 0x43u
            "F11" -> 0x44u
            "F12" -> 0x45u
            else -> 0x3Au
        }
    }

    fun getScanCodeForKeyName(keyName: String): UByte {
        val clean = keyName.uppercase().trim()
        if (clean.startsWith("F") && clean.length in 2..3 && clean.substring(1).toIntOrNull() != null) {
            return getFunctionKeyScanCode(clean)
        }
        return when (clean) {
            "ENTER", "RETURN" -> 0x28u
            "ESC", "ESCAPE" -> 0x29u
            "BKSP", "BACKSPACE" -> 0x2Au
            "TAB" -> 0x2Bu
            "SPACE" -> 0x2Cu
            "UP" -> 0x52u
            "DOWN" -> 0x51u
            "LEFT" -> 0x50u
            "RIGHT" -> 0x4Fu
            "HOME" -> 0x4Au
            "END" -> 0x4Du
            "PAGEUP", "PGUP" -> 0x4Bu
            "PAGEDOWN", "PGDN" -> 0x4Eu
            "DELETE", "DEL" -> 0x4Cu
            "INSERT", "INS" -> 0x49u
            "A" -> 0x04u
            "B" -> 0x05u
            "C" -> 0x06u
            "D" -> 0x07u
            "E" -> 0x08u
            "F" -> 0x09u
            "G" -> 0x0Au
            "H" -> 0x0Bu
            "I" -> 0x0Cu
            "J" -> 0x0Du
            "K" -> 0x0Eu
            "L" -> 0x0Fu
            "M" -> 0x10u
            "N" -> 0x11u
            "O" -> 0x12u
            "P" -> 0x13u
            "Q" -> 0x14u
            "R" -> 0x15u
            "S" -> 0x16u
            "T" -> 0x17u
            "U" -> 0x18u
            "V" -> 0x19u
            "W" -> 0x1Au
            "X" -> 0x1Bu
            "Y" -> 0x1Cu
            "Z" -> 0x1Du
            "1" -> 0x1Eu
            "2" -> 0x1Fu
            "3" -> 0x20u
            "4" -> 0x21u
            "5" -> 0x22u
            "6" -> 0x23u
            "7" -> 0x24u
            "8" -> 0x25u
            "9" -> 0x26u
            "0" -> 0x27u
            else -> 0x28u // default Enter
        }
    }
}
