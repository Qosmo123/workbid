package app.workbid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.workbid.ui.WorkbidApp
import app.workbid.ui.WorkbidTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectEmulatorsIfRequested()
        enableEdgeToEdge()
        setContent {
            WorkbidTheme {
                WorkbidApp()
            }
        }
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
