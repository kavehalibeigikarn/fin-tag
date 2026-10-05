package ir.kaveh.fintag

import java.util.Locale

fun toFa(s: String): String = buildString {
    for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c)
}

fun formatMoney(rial: Long, toman: Boolean): String {
    val v = if (toman) rial / 10 else rial
    val s = toFa(String.format(Locale.US, "%,d", v)).replace(',', '٬')
    return s + if (toman) " تومان" else " ریال"
}

private fun persianFormat(pattern: String) =
    android.icu.text.SimpleDateFormat(pattern, android.icu.util.ULocale("fa_IR@calendar=persian"))

fun formatDate(ms: Long): String = persianFormat("yyyy/MM/dd  HH:mm").format(java.util.Date(ms))

fun formatDay(ms: Long): String = persianFormat("yyyy/MM/dd").format(java.util.Date(ms))

fun formatTime(ms: Long): String = persianFormat("HH:mm").format(java.util.Date(ms))

fun monthTitle(ms: Long = System.currentTimeMillis()): String =
    persianFormat("MMMM yyyy").format(java.util.Date(ms))

fun dayTitle(ms: Long): String {
    val now = System.currentTimeMillis()
    val key = formatDay(ms)
    return when (key) {
        formatDay(now) -> "امروز"
        formatDay(now - 86_400_000L) -> "دیروز"
        else -> persianFormat("EEEE d MMMM yyyy").format(java.util.Date(ms))
    }
}

/** شروع ماه شمسی جاری */
fun monthStart(now: Long = System.currentTimeMillis()): Long {
    val c = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))
    c.timeInMillis = now
    c.set(android.icu.util.Calendar.DAY_OF_MONTH, 1)
    c.set(android.icu.util.Calendar.HOUR_OF_DAY, 0)
    c.set(android.icu.util.Calendar.MINUTE, 0)
    c.set(android.icu.util.Calendar.SECOND, 0)
    c.set(android.icu.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
