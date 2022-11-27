package im.molly.unifiedpush.receiver

import android.content.Context
import im.molly.unifiedpush.util.UnifiedPushHelper
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.MessagingReceiver
import java.util.Timer
import kotlin.concurrent.schedule

class UnifiedPushReceiver: MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val TIMEOUT = 20_000L //20secs

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
    //TODO: alert if air gaped and endpoint changes
    SignalStore.unifiedpush.endpoint = endpoint
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    SignalStore.unifiedpush.endpoint = null
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    if (SignalStore.account.isRegistered && UnifiedPushHelper.isUnifiedPushEnabled()) {
      Log.d(TAG, "New message")
      AppDependencies.incomingMessageObserver.registerKeepAliveToken(UnifiedPushReceiver::class.java.name)
      Timer().schedule(TIMEOUT) {
        AppDependencies.incomingMessageObserver.removeKeepAliveToken(UnifiedPushReceiver::class.java.name)
      }
    }
  }
}