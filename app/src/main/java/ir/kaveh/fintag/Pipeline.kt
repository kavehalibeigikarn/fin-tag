package ir.kaveh.fintag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Notifier {
    private const val CH = "txn"

    fun show(ctx: Context, id: Long, isIn: Boolean, amount: Long, accountName: String = "") {
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
        val title = (if (isIn) "واریز جدید" else "برداشت جدید") +
            (if (accountName.isNotBlank()) "  •  $accountName" else "")
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(formatMoney(amount, Prefs.toman(ctx)) + " — برای افزودن توضیح و تگ لمس کنید")
            .setContentIntent(pi)
            .setOngoing(true)       // تا وقتی توضیح/تگ ذخیره نشده، اعلان می‌ماند
            .setAutoCancel(false)
            .build()
        nm.notify(id.toInt(), n)
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }
}

object Pipeline {
    private suspend fun log(dao: AppDao, sender: String, body: String, time: Long, status: String, reason: String) {
        if (sender == "TEST") return
        dao.insertLog(SmsLog(time = time, sender = sender, body = body.take(600), status = status, reason = reason))
        dao.pruneLog()
    }

    /** پیامک را تحلیل می‌کند؛ اگر تراکنش بود به حساب مربوط نسبت داده، ذخیره و آیکون/اعلان نشان می‌دهد. */
    suspend fun handle(
        ctx: Context,
        sender: String,
        body: String,
        time: Long,
        force: Boolean = false,
        forceAccountId: Long? = null,
        relaxedOverride: Boolean? = null
    ): Long? {
        val dao = AppDb.get(ctx).dao()
        val relaxed = relaxedOverride ?: Prefs.relaxed(ctx)
        val res = SmsParser.parseDetailed(body, requireBalance = !relaxed)
        val parsed = res.parsed
        if (parsed == null) {
            if (SmsParser.shouldLog(body)) log(dao, sender, body, time, "ردشد", res.reason)
            return null
        }
        if (!force && dao.recentDup(body, time - 20_000) > 0) {
            log(dao, sender, body, time, "تکراری", "همین پیامک چند ثانیه‌ی پیش ثبت شده بود")
            return null
        }

        var accountName = ""
        val id = Accounts.lock.withLock {
            val accId = forceAccountId
                ?: if (sender == "TEST") Accounts.defaultAccount(dao) else Accounts.resolve(dao, sender, body)
            accountName = dao.getAccount(accId)?.name ?: ""
            dao.insertTxn(
                Txn(
                    type = if (parsed.isIn) "IN" else "OUT",
                    amount = parsed.amountRial,
                    balance = parsed.balanceRial,
                    sender = sender,
                    rawText = body,
                    time = time,
                    accountId = accId
                )
            )
        }
        log(dao, sender, body, time, "ثبت شد", if (accountName.isNotBlank()) "حساب: $accountName" else "")
        withContext(Dispatchers.Main) {
            Notifier.show(ctx, id, parsed.isIn, parsed.amountRial, accountName)
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
