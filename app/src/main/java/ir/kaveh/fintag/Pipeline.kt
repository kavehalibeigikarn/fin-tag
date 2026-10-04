package ir.kaveh.fintag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Notifier {
    private const val CH = "txn"

    fun show(ctx: Context, id: Long, isIn: Boolean, amount: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "تراکنش‌ها", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = Intent(ctx, TagPopupActivity::class.java)
            .putExtra("id", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, id.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (isIn) "واریز جدید" else "برداشت جدید")
            .setContentText(formatMoney(amount, Prefs.toman(ctx)) + " — برای افزودن توضیح و تگ لمس کنید")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id.toInt(), n)
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}

object Pipeline {
    /** پیامک را تحلیل می‌کند؛ اگر تراکنش بود ذخیره کرده و پاپ‌آپ/اعلان نشان می‌دهد. */
    suspend fun handle(ctx: Context, sender: String, body: String, time: Long, force: Boolean = false): Long? {
        val parsed = SmsParser.parse(body) ?: return null
        val dao = AppDb.get(ctx).dao()
        if (!force && dao.recentDup(body, time - 60_000) > 0) return null

        val id = dao.insertTxn(
            Txn(
                type = if (parsed.isIn) "IN" else "OUT",
                amount = parsed.amountRial,
                balance = parsed.balanceRial,
                sender = sender,
                rawText = body,
                time = time
            )
        )
        withContext(Dispatchers.Main) {
            Notifier.show(ctx, id, parsed.isIn, parsed.amountRial)
            if (Prefs.popup(ctx) && Settings.canDrawOverlays(ctx)) {
                try {
                    ctx.startService(Intent(ctx, OverlayService::class.java).putExtra("id", id))
                } catch (_: Exception) {
                }
            }
        }
        return id
    }
}
