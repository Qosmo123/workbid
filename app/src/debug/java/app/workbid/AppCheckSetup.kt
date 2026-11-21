package app.workbid

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (from Android Studio) can't pass Play Integrity, so they use a debug token instead.
 * The first run prints a line like "Enter this debug secret into the allow list in the Firebase Console"
 * in Logcat. Add that token under App Check → Apps → Manage debug tokens before turning on enforcement,
 * or debug builds will be refused.
 */
object AppCheckSetup {
    private var installed = false

    fun install() {
        if (installed) return
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        installed = true
    }
}
