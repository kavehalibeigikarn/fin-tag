package ir.kaveh.fintag

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountStat(
    val account: Account,
    val balance: Long,              // موجودی محاسبه‌شده = موجودی اول دوره + واریزها − برداشت‌ها
    val lastSmsBalance: Long?,      // آخرین مانده‌ای که در پیامک بانک آمده
    val monthStartBalance: Long     // موجودی اول ماه شمسی جاری
)

fun computeStat(a: Account, txns: List<Txn>, monthStart: Long): AccountStat {
    val mine = txns.filter { it.accountId == a.id }
    val counted = mine.filter { it.time >= a.openingTime }
    fun signed(t: Txn): Long = if (t.isIn()) t.amount else -t.amount
    val balance = a.openingBalance + counted.sumOf { signed(it) }
    val before = a.openingBalance + counted.filter { it.time < monthStart }.sumOf { signed(it) }
    val last = mine.filter { it.balance != null }.maxByOrNull { it.time }?.balance
    return AccountStat(a, balance, last, before)
}

object Accounts {
    /** ساخت/تخصیص حساب باید پشت این قفل انجام شود تا حساب تکراری ساخته نشود */
    val lock = Mutex()

    private fun norm(s: String) = SmsParser.normalize(s).lowercase().filter { it.isLetterOrDigit() }

    fun senderMatches(a: Account, sender: String): Boolean {
        val s = norm(sender)
        if (s.isEmpty()) return false
        return a.senders.split(",").map { norm(it) }.filter { it.isNotEmpty() }.any { x ->
            x == s || (x.length >= 6 && s.length >= 6 && (x.endsWith(s) || s.endsWith(x)))
        }
    }

    fun hintMatches(a: Account, body: String): Boolean {
        val b = SmsParser.normalize(body)
        return a.hint.split(",").map { SmsParser.normalize(it).trim() }.filter { it.isNotEmpty() }
            .any { b.contains(it) }
    }

    /** پیامک را به یک حساب نسبت می‌دهد؛ اگر حسابی نبود، خودکار می‌سازد. داخل lock صدا بزنید. */
    suspend fun resolve(dao: AppDao, sender: String, body: String): Long {
        val all = dao.getAccounts()
        val bySender = all.filter { senderMatches(it, sender) }
        if (bySender.isNotEmpty()) {
            return (bySender.firstOrNull { hintMatches(it, body) }
                ?: bySender.firstOrNull { it.hint.isBlank() }
                ?: bySender.first()).id
        }
        all.firstOrNull { hintMatches(it, body) }?.let { return it.id }
        val name = if (sender.any { it.isLetter() }) sender else "حساب $sender"
        return dao.insertAccount(Account(name = name.ifBlank { "حساب جدید" }, senders = sender))
    }

    suspend fun defaultAccount(dao: AppDao): Long {
        val first = dao.getAccounts().firstOrNull()
        return first?.id ?: dao.insertAccount(Account(name = "حساب اصلی"))
    }

    /** تراکنش‌های قدیمی (قبل از نسخه‌ی چندحسابی) را بر اساس فرستنده به حساب‌ها نسبت می‌دهد */
    suspend fun assignUnassigned(ctx: Context) {
        val dao = AppDb.get(ctx).dao()
        lock.withLock {
            for (t in dao.unassigned()) {
                val id = if (t.sender.isBlank() || t.sender == "manual" || t.sender == "TEST") {
                    defaultAccount(dao)
                } else {
                    resolve(dao, t.sender, t.rawText)
                }
                dao.updateTxn(t.copy(accountId = id))
            }
        }
    }
}
