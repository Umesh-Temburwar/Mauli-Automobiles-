package dev.fabik.bluetoothhid.tally

import java.util.UUID

data class TallyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<TallyStep> = emptyList(),
    val isDefault: Boolean = false
) {
    companion object {
        fun createDefaultProfiles(): List<TallyProfile> = listOf(
            // 1. Purchase Entry Profile
            TallyProfile(
                id = "profile_purchase_entry",
                name = "Purchase Entry",
                description = "Types barcode, presses Enter, waits, then presses Alt+A to add/accept product line.",
                isDefault = true,
                steps = listOf(
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 150L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 200L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 500L),
                    TallyStep(
                        type = TallyActionType.PRESS_SHORTCUT,
                        key = "A",
                        modifiers = listOf("ALT"),
                        delayMs = 200L
                    ),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 150L)
                )
            ),

            // 2. Sales Entry Profile
            TallyProfile(
                id = "profile_sales_entry",
                name = "Sales Entry",
                description = "Types barcode into Sales Voucher, advances with Enter, Tabs to quantity.",
                steps = listOf(
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 150L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 200L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_TAB, delayMs = 150L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 200L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 150L)
                )
            ),

            // 3. Stock Journal Profile
            TallyProfile(
                id = "profile_stock_journal",
                name = "Stock Journal",
                description = "For stock inward / consumption transfers in TallyPrime.",
                steps = listOf(
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 150L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 200L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 500L),
                    TallyStep(type = TallyActionType.PRESS_TAB, delayMs = 200L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 150L)
                )
            ),

            // 4. Stock Item Search Profile
            TallyProfile(
                id = "profile_stock_item_search",
                name = "Stock Item Search",
                description = "Filters items in stock list using Alt+F12, types barcode, and presses Enter.",
                steps = listOf(
                    TallyStep(
                        type = TallyActionType.PRESS_SHORTCUT,
                        key = "F12",
                        modifiers = listOf("ALT"),
                        delayMs = 250L
                    ),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 350L),
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 150L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 300L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 150L)
                )
            ),

            // 5. Custom Profile 1
            TallyProfile(
                id = "profile_custom_1",
                name = "Custom Profile 1",
                description = "Single Enter confirmation after barcode entry.",
                steps = listOf(
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 100L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 200L),
                    TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 100L)
                )
            ),

            // 6. Custom Profile 2
            TallyProfile(
                id = "profile_custom_2",
                name = "Custom Profile 2",
                description = "Tab navigation after barcode entry.",
                steps = listOf(
                    TallyStep(type = TallyActionType.TYPE_BARCODE, delayMs = 100L),
                    TallyStep(type = TallyActionType.WAIT, delayMs = 200L),
                    TallyStep(type = TallyActionType.PRESS_TAB, delayMs = 100L)
                )
            )
        )
    }
}
