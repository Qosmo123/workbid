package app.workbid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.workbid.ui.WorkbidApp
import app.workbid.ui.WorkbidTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private lateinit var updates: AppUpdates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectEmulatorsIfRequested()
        // The emulators ignore App Check, so only install it when talking to the real Firebase project.
        if (!BuildConfig.USE_EMULATOR) AppCheckSetup.install()
        updates = AppUpdates(this)
        if (!BuildConfig.DEBUG) updates.check()
        enableEdgeToEdge()
        setContent {
            WorkbidTheme {
                Box(Modifier.fillMaxSize()) {
                    WorkbidApp()
                    if (updates.ready) UpdateReadyBar(
                        onRestart = { updates.restart() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEBUG) updates.recheck()
    }

    override fun onDestroy() {
        updates.close()
        super.onDestroy()
    }

    private companion object {
        var emulatorsConnected = false

        /** Debug builds made with -Pworkbid.emulator=true talk to the local Firebase emulators, not live data. */
        fun connectEmulatorsIfRequested() {
            if (!BuildConfig.USE_EMULATOR || emulatorsConnected) return
            // 10.0.2.2 is the computer running the Android emulator.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8085)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
            emulatorsConnected = true
        }
    }
}

/** Shown when a new version has downloaded from Google Play. */
@androidx.compose.runtime.Composable
private fun UpdateReadyBar(onRestart: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        modifier = modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A new version of Workbid is ready.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRestart) { Text("Restart") }
        }
    }
}
