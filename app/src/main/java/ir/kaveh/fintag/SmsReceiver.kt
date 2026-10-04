package ir.kaveh.fintag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return

        val sender = msgs[0].originatingAddress ?: ""
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val time = System.currentTimeMillis()
        val app = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Pipeline.handle(app, sender, body, time)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
