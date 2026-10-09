package ir.kaveh.fintag

/**
 * تشخیص پیامک واریز/برداشت بانک‌های ایرانی.
 * اگر قالب پیامک بانک شما تشخیص داده نشد، فقط همین فایل را اصلاح کنید.
 */
object SmsParser {
    data class Parsed(val isIn: Boolean, val amountRial: Long, val balanceRial: Long?)
    data class ParseResult(val parsed: Parsed?, val reason: String)

    // پیامک رمز/کد تأیید؛ فقط وقتی «مانده» ندارد رد می‌شود (اگر مانده داشت، تراکنش واقعی است)
    private val otpRe = Regex(
        """(رمز\s*(پویا|دوم|یک\s*بار|یکبار|ورود|عبور|اینترنتی|پیامکی)|رمز\s*[:：]?\s*\d{4,8}|کد\s*(تایید|تأیید|فعال\s*سازی|فعال‌سازی|امنیتی|یکبار|یک\s*بار)|\botp\b|cvv2?|password)""",
        RegexOption.IGNORE_CASE
    )
    private val financialRe = Regex("ریال|تومان|مانده|موجودی|برداشت|واریز|خرید|پرداخت|حواله|کارمزد|deposit|withdraw|balance|purchase")

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
                    '٬', '،', '٫' -> ','
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun toLong(s: String): Long? = s.replace(",", "").toLongOrNull()

    /** آیا این پیامک ارزش نمایش در فهرست عیب‌یابی را دارد؟ (پیامک رمز یکبار مصرف ذخیره نمی‌شود) */
    fun shouldLog(raw: String): Boolean {
        val t = normalize(raw)
        val l = t.lowercase()
        if (!t.any { it.isDigit() } || !financialRe.containsMatchIn(l)) return false
        if (otpRe.containsMatchIn(t) && !balRe.containsMatchIn(l)) return false
        return true
    }

    fun parse(raw: String, requireBalance: Boolean = true): Parsed? = parseDetailed(raw, requireBalance).parsed

    fun parseDetailed(raw: String, requireBalance: Boolean = true): ParseResult {
        val t0 = normalize(raw)
        val tl = t0.replace(dateRe, " ").replace(timeRe, " ").replace(maskRe, " ").lowercase()

        val balMatch = balRe.find(tl)
        val balRange = balMatch?.groups?.get(1)?.range
        val balance = balMatch?.groups?.get(1)?.value?.let { toLong(it) }
        if (balance == null) {
            if (otpRe.containsMatchIn(t0)) return ParseResult(null, "پیامک رمز/کد تأیید است")
            if (requireBalance) return ParseResult(null, "عدد «مانده/موجودی» در پیامک پیدا نشد")
        }

        val cands = numRe.findAll(tl)
            .filter { m -> balRange == null || m.range.first !in balRange }
            .toList()
        val chosen = cands.firstOrNull { it.value.contains(',') }
            ?: cands.firstOrNull { it.value.length in 4..9 }
            ?: return ParseResult(null, "مبلغ تراکنش در پیامک پیدا نشد")
        val amount = toLong(chosen.value)
        if (amount == null || amount <= 0) return ParseResult(null, "مبلغ تراکنش خوانده نشد")

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
                    else -> return ParseResult(null, "واریز یا برداشت بودن مشخص نشد (کلمه‌ی واریز/برداشت/خرید یا علامت +/- نبود)")
                }
            }
        }

        val mult = if (tl.contains("تومان")) 10L else 1L
        return ParseResult(Parsed(isIn, amount * mult, balance?.times(mult)), "")
    }
}
