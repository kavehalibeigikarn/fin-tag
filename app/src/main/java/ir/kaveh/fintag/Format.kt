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

fun formatDate(ms: Long): String {
    val f = android.icu.text.SimpleDateFormat(
        "yyyy/MM/dd  HH:mm",
        android.icu.util.ULocale("fa_IR@calendar=persian")
    )
    return f.format(java.util.Date(ms))
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
