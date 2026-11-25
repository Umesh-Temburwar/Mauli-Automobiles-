package dev.fabik.bluetoothhid.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceStateDefault
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.launch

/**
 * Reusable 1-tap sound toggle button for TopAppBars and screen toolbars.
 * Allows instant muting or enabling of barcode scanner audio beeps.
 */
@Composable
fun SoundToggleAction(
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF2E7D32),
    mutedColor: Color = Color(0xFF9E9E9E)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playSound by context.getPreferenceStateDefault(PreferenceStore.PLAY_SOUND)

    IconButton(
        onClick = {
            scope.launch {
                val newState = !playSound
                context.setPreference(PreferenceStore.PLAY_SOUND, newState)
                val msg = if (newState) "🔊 Barcode Sound: ON" else "🔇 Barcode Sound: MUTED"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (playSound) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            contentDescription = if (playSound) "Mute Sound" else "Enable Sound",
            tint = if (playSound) activeColor else mutedColor
        )
    }
}
