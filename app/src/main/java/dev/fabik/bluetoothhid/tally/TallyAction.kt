package dev.fabik.bluetoothhid.tally

import java.util.UUID

enum class TallyActionType(val displayName: String, val iconName: String) {
    TYPE_BARCODE("Type Barcode", "Barcode"),
    PRESS_ENTER("Press Enter", "KeyboardReturn"),
    PRESS_TAB("Press Tab", "KeyboardTab"),
    PRESS_ESCAPE("Press Escape", "Close"),
    PRESS_BACKSPACE("Press Backspace", "Backspace"),
    PRESS_SPACE("Press Space", "SpaceBar"),
    PRESS_UP("Press Up Arrow", "ArrowUpward"),
    PRESS_DOWN("Press Down Arrow", "ArrowDownward"),
    PRESS_LEFT("Press Left Arrow", "ArrowBack"),
    PRESS_RIGHT("Press Right Arrow", "ArrowForward"),
    PRESS_PAGE_UP("Press Page Up", "KeyboardArrowUp"),
    PRESS_PAGE_DOWN("Press Page Down", "KeyboardArrowDown"),
    PRESS_FUNCTION_KEY("Press Function Key (F1-F12)", "SmartButton"),
    PRESS_SHORTCUT("Press Shortcut (Ctrl/Alt combo)", "KeyboardCommandKey"),
    WAIT("Wait Delay", "HourglassEmpty"),
    TYPE_TEXT("Type Custom Text", "TextFields"),
    END_SEQUENCE("End Sequence", "StopCircle")
}

enum class AutomationMode(val displayName: String, val description: String) {
    MANUAL("Manual (Confirm First)", "Shows scanned barcode and Send button. Executes shortcuts on user tap."),
    AUTOMATIC("Automatic (Instant)", "Sends barcode immediately and runs profile shortcut sequence."),
    BARCODE_ONLY("Barcode Only", "Types barcode into Tally without extra automation shortcuts.")
}

data class TallyStep(
    val id: String = UUID.randomUUID().toString(),
    val type: TallyActionType = TallyActionType.PRESS_ENTER,
    val key: String = "ENTER",                  // e.g. "ENTER", "TAB", "ESC", "F1", "F12", "A", "C", "V"
    val modifiers: List<String> = emptyList(),  // "CTRL", "ALT", "SHIFT"
    val delayMs: Long = 300L,                   // Delay in milliseconds
    val textPayload: String = "",               // Used if type == TYPE_TEXT
    val isEnabled: Boolean = true
) {
    fun getSummaryText(): String {
        return when (type) {
            TallyActionType.TYPE_BARCODE -> "Send Scanned Barcode String"
            TallyActionType.PRESS_ENTER -> "Press Enter"
            TallyActionType.PRESS_TAB -> "Press Tab"
            TallyActionType.PRESS_ESCAPE -> "Press Escape (Esc)"
            TallyActionType.PRESS_BACKSPACE -> "Press Backspace"
            TallyActionType.PRESS_SPACE -> "Press Space"
            TallyActionType.PRESS_UP -> "Press Up Arrow"
            TallyActionType.PRESS_DOWN -> "Press Down Arrow"
            TallyActionType.PRESS_LEFT -> "Press Left Arrow"
            TallyActionType.PRESS_RIGHT -> "Press Right Arrow"
            TallyActionType.PRESS_PAGE_UP -> "Press Page Up"
            TallyActionType.PRESS_PAGE_DOWN -> "Press Page Down"
            TallyActionType.PRESS_FUNCTION_KEY -> "Press $key"
            TallyActionType.PRESS_SHORTCUT -> {
                val modStr = if (modifiers.isNotEmpty()) modifiers.joinToString(" + ") + " + " else ""
                "Press $modStr$key"
            }
            TallyActionType.WAIT -> "Wait: ${delayMs}ms"
            TallyActionType.TYPE_TEXT -> "Type: \"$textPayload\""
            TallyActionType.END_SEQUENCE -> "End Automation Sequence"
        }
    }
}
