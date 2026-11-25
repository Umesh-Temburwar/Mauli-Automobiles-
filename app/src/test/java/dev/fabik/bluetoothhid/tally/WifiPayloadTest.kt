package dev.fabik.bluetoothhid.tally

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WifiPayloadTest {

    @Test
    fun testKeyboardDirectProfileCreation() {
        val step = TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 50L)
        val profile = TallyProfile(
            id = "keyboard_direct",
            name = "KeyboardDirect",
            steps = listOf(step)
        )
        assertNotNull(profile)
        assertEquals("keyboard_direct", profile.id)
        assertEquals(1, profile.steps.size)
        assertEquals(TallyActionType.PRESS_ENTER, profile.steps[0].type)
    }
}
