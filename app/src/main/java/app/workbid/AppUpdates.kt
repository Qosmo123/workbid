package app.workbid

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Keeps people on the latest version without them visiting the Play Store.
 *
 * When Google Play has a newer version (because a new release went out from GitHub), it downloads in the
 * background while the app is in use, then the app shows "Update ready · Restart". Only works for installs
 * from Google Play (including internal and closed testing); sideloaded and debug builds simply skip it.
 */
class AppUpdates(private val activity: ComponentActivity) {
    /** True once a new version has downloaded and is waiting for a restart. */
    var ready by mutableStateOf(false)
        private set

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { /* declined is fine */ }

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) ready = true
    }

    /** Call from onCreate. Asks Play whether there's an update and starts a background download if so. */
    fun check() {
        runCatching {
            manager.registerListener(listener)
            manager.appUpdateInfo.addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> ready = true
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        runCatching {
                            manager.startUpdateFlowForResult(
                                info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                            )
                        }
                }
            }
        }
    }

    /** Call from onResume, in case the download finished while the app was in the background. */
    fun recheck() {
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) ready = true
            }
        }
    }

    /** Installs the downloaded version; the app restarts on its own. */
    fun restart() {
        runCatching { manager.completeUpdate() }
    }

    fun close() {
        runCatching { manager.unregisterListener(listener) }
    }
}
