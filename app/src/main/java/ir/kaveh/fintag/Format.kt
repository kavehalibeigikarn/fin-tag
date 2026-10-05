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

// ───────────── عدد به حروف فارسی ─────────────
private val ones = listOf(
    "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده",
    "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"
)
private val tens = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
private val hundreds = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
private val scales = listOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "کوادریلیون")

private fun tripleWords(n: Int): String {
    val parts = ArrayList<String>()
    val h = n / 100
    val r = n % 100
    if (h > 0) parts.add(hundreds[h])
    if (r in 1..19) {
        parts.add(ones[r])
    } else if (r >= 20) {
        parts.add(tens[r / 10])
        if (r % 10 > 0) parts.add(ones[r % 10])
    }
    return parts.joinToString(" و ")
}

fun numberToWords(n: Long): String {
    if (n == 0L) return "صفر"
    if (n < 0) return "منفی " + numberToWords(-n)
    val groups = ArrayList<String>()
    var x = n
    var i = 0
    while (x > 0 && i < scales.size) {
        val t = (x % 1000).toInt()
        if (t > 0) {
            groups.add(
                when {
                    i == 0 -> tripleWords(t)
                    i == 1 && t == 1 -> "هزار"
                    else -> tripleWords(t) + " " + scales[i]
                }
            )
        }
        x /= 1000
        i++
    }
    return groups.reversed().joinToString(" و ")
}

// ───────────── تاریخ شمسی ↔ میلادی ─────────────
private fun persianCal(): android.icu.util.Calendar =
    android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))

/** [سال، ماه (۱..۱۲)، روز، ساعت، دقیقه] به تقویم شمسی */
fun jalaliParts(ms: Long): IntArray {
    val c = persianCal()
    c.timeInMillis = ms
    return intArrayOf(
        c.get(android.icu.util.Calendar.YEAR),
        c.get(android.icu.util.Calendar.MONTH) + 1,
        c.get(android.icu.util.Calendar.DAY_OF_MONTH),
        c.get(android.icu.util.Calendar.HOUR_OF_DAY),
        c.get(android.icu.util.Calendar.MINUTE)
    )
}

/** اگر تاریخ نامعتبر باشد null برمی‌گرداند */
fun jalaliToMillis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long? {
    if (y < 1200 || y > 1700 || m !in 1..12 || d !in 1..31 || h !in 0..23 || min !in 0..59) return null
    return try {
        val c = persianCal()
        c.isLenient = false
        c.clear()
        c.set(android.icu.util.Calendar.YEAR, y)
        c.set(android.icu.util.Calendar.MONTH, m - 1)
        c.set(android.icu.util.Calendar.DAY_OF_MONTH, d)
        c.set(android.icu.util.Calendar.HOUR_OF_DAY, h)
        c.set(android.icu.util.Calendar.MINUTE, min)
        c.timeInMillis
    } catch (e: Exception) {
        null
    }
}
