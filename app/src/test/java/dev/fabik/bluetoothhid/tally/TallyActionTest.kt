package dev.fabik.bluetoothhid.tally

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TallyActionTest {

    @Test
    fun testDirectionalActionsExist() {
        assertNotNull(TallyActionType.PRESS_UP)
        assertNotNull(TallyActionType.PRESS_DOWN)
        assertNotNull(TallyActionType.PRESS_LEFT)
        assertNotNull(TallyActionType.PRESS_RIGHT)
        assertNotNull(TallyActionType.PRESS_PAGE_UP)
        assertNotNull(TallyActionType.PRESS_PAGE_DOWN)
    }

    @Test
    fun testStepDescriptionFormatting() {
        val stepUp = TallyStep(type = TallyActionType.PRESS_UP)
        assertEquals("Press Up Arrow", stepUp.getDescription())

        val stepDown = TallyStep(type = TallyActionType.PRESS_DOWN)
        assertEquals("Press Down Arrow", stepDown.getDescription())

        val stepPgUp = TallyStep(type = TallyActionType.PRESS_PAGE_UP)
        assertEquals("Press Page Up", stepPgUp.getDescription())
    }
}
