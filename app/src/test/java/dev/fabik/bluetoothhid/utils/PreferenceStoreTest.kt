package dev.fabik.bluetoothhid.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreTest {

    @Test
    fun testDefaultPreferences() {
        assertTrue("PLAY_SOUND should default to true", PreferenceStore.PLAY_SOUND.defaultValue)
        assertTrue("VIBRATE should default to true", PreferenceStore.VIBRATE.defaultValue)
    }
}
