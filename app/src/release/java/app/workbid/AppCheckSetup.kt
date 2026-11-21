package app.workbid

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds prove they're the genuine app from Google Play using Play Integrity.
 * Nothing is blocked until App Check enforcement is switched on in the Firebase console
 * (see the hand-off guide, chapter 10).
 */
object AppCheckSetup {
    private var installed = false

    fun install() {
        if (installed) return
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        installed = true
    }
}
