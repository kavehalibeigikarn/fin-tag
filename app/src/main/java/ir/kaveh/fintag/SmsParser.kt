package ir.kaveh.fintag

/**
 * تشخیص پیامک واریز/برداشت بانک‌های ایرانی.
 * اگر قالب پیامک بانک شما تشخیص داده نشد، فقط همین فایل را اصلاح کنید.
 */
object SmsParser {
    data class Parsed(val isIn: Boolean, val amountRial: Long, val balanceRial: Long?)

    private val ignoreWords = listOf("رمز", "کد تایید", "کد تأیید", "کد فعال", "otp", "cvv2", "password")
    private val inKeys = listOf("واریز", "دریافت", "افزایش", "وارد شد", "انتقال از", "deposit", "credit", "received")
    private val outKeys = listOf("برداشت", "خرید", "پرداخت", "کسر", "قبض", "انتقال به", "withdraw", "debit", "purchase", "paid", "payment")

    private val dateRe = Regex("""\d{2,4}[/.\-]\d{1,2}[/.\-]\d{1,4}""")
    private val timeRe = Regex("""\d{1,2}:\d{2}(:\d{2})?""")
    private val maskRe = Regex("""[\d\-]*[*•]+[\d*•\-]*""")
    private val numRe = Regex("""\d{1,3}(?:,\d{3})+|\d+""")
    private val balRe = Regex(
        """(?:مانده|موجودی|balance|bal)[^\d]{0,20}(\d{1,3}(?:,\d{3})+|\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    '٬', '،' -> ','
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun toLong(s: String): Long? = s.replace(",", "").toLongOrNull()

    fun parse(raw: String): Parsed? {
        val t0 = normalize(raw)
        val lower0 = t0.lowercase()
        if (ignoreWords.any { lower0.contains(it) }) return null

        val tl = t0.replace(dateRe, " ").replace(timeRe, " ").replace(maskRe, " ").lowercase()

        val balMatch = balRe.find(tl)
        val balRange = balMatch?.groups?.get(1)?.range
               if (balance == null) return null

        val cands = numRe.findAll(tl)
            .filter { m -> balRange == null || m.range.first !in balRange }
            .toList()
        val chosen = cands.firstOrNull { it.value.contains(',') }
            ?: cands.firstOrNull { it.value.length in 4..9 }
            ?: return null
        val amount = toLong(chosen.value) ?: return null
        if (amount <= 0) return null

        val inIdx = inKeys.map { tl.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val outIdx = outKeys.map { tl.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val isIn = when {
            inIdx != null && outIdx != null -> inIdx < outIdx
            inIdx != null -> true
            outIdx != null -> false
            else -> {
                val after = tl.getOrNull(chosen.range.last + 1)
                val before = tl.getOrNull(chosen.range.first - 1)
                when {
                    after == '+' || before == '+' -> true
                    after == '-' || before == '-' -> false
                    else -> return null
                }
            }
        }

        val mult = if (tl.contains("تومان")) 10L else 1L
        return Parsed(isIn, amount * mult, balance?.times(mult))
    }
}
