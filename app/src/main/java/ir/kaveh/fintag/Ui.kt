@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.kaveh.fintag

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ───────────── رنگ‌ها و قلم ─────────────
val Teal = Color(0xFF0F766E)
val Income = Color(0xFF16A34A)
val Expense = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF0EB), onPrimaryContainer = Color(0xFF042F2C),
    secondaryContainer = Color(0xFFD7EFEB), onSecondaryContainer = Color(0xFF0B3B36),
    background = Color(0xFFF2F6F5), onBackground = Color(0xFF16201F),
    surface = Color.White, onSurface = Color(0xFF16201F),
    surfaceVariant = Color(0xFFE3ECEA), onSurfaceVariant = Color(0xFF3F4F4D),
    outline = Color(0xFF7A8B88), outlineVariant = Color(0xFFD0DCD9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF042F2C),
    primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCF0EB),
    secondaryContainer = Color(0xFF1B3B38), onSecondaryContainer = Color(0xFFCCF0EB),
    background = Color(0xFF0D1413), onBackground = Color(0xFFE3EDEB),
    surface = Color(0xFF16201F), onSurface = Color(0xFFE3EDEB),
    surfaceVariant = Color(0xFF243230), onSurfaceVariant = Color(0xFFB4C4C1),
    outline = Color(0xFF86948F), outlineVariant = Color(0xFF34423F)
)

private val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_regular, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

private val AppTypography = Typography().withFont(Vazir)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}

// ───────────── چیپ رنگی تگ ─────────────
private val tagPalette = listOf(
    0xFF0F766E, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777,
    0xFFEA580C, 0xFF65A30D, 0xFF0891B2, 0xFFCA8A04
).map { Color(it) }

fun tagColor(name: String): Color = tagPalette[(name.hashCode() and 0x7fffffff) % tagPalette.size]

@Composable
fun TagChip(name: String) {
    val c = tagColor(name)
    Box(
        Modifier
            .background(c.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text("#$name", color = c, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TagToggle(name: String, selected: Boolean, onToggle: (String) -> Unit) {
    val c = tagColor(name)
    FilterChip(
        selected = selected,
        onClick = { onToggle(name) },
        label = { Text(name) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = c.copy(alpha = 0.18f),
            selectedLabelColor = c
        )
    )
}

/**
 * انتخاب یک یا چند تگ: تگ‌های انتخاب‌شده + ۴ تگ پرکاربرد همیشه دیده می‌شوند؛
 * بقیه با دکمه‌ی «＋» باز می‌شوند (با جستجو) تا برای ۲۰ تگ هم صفحه شلوغ نشود.
 * اگر onAdd داده شود، ساخت تگ جدید هم داخل بخش باز‌شده هست.
 */
@Composable
fun TagPicker(
    allTags: List<Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    sampleCount: Int = 4,
    newText: String = "",
    onNewText: ((String) -> Unit)? = null,
    onAdd: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val names = allTags.map { it.name }
    val samples = names.filter { it !in selected }.take(sampleCount)
    val rest = names.filter { it !in selected && it !in samples }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            selected.forEach { TagToggle(it, true, onToggle) }
            samples.forEach { TagToggle(it, false, onToggle) }
            if (rest.isNotEmpty() || onAdd != null) {
                AssistChip(
                    onClick = { open = !open },
                    label = {
                        Text(if (open) "▲ بستن" else "＋ تگ‌های بیشتر (${toFa(rest.size.toString())})")
                    },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        if (open) {
            if (rest.isNotEmpty()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("جستجوی تگ") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                val filtered = rest.filter { search.isBlank() || it.contains(search.trim()) }
                Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        filtered.forEach { TagToggle(it, false, onToggle) }
                    }
                }
            }
            if (onAdd != null && onNewText != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newText,
                        onValueChange = onNewText,
                        label = { Text("تگ جدید") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onAdd() }),
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Text("افزودن") }
                }
            }
        }
    }
}

// ───────────── مبلغ: جداکننده‌ی سه‌رقمی + نوشتن به حروف ─────────────
class ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        val sb = StringBuilder()
        val isSep = ArrayList<Boolean>()
        for (i in raw.indices) {
            val ch = raw[i]
            sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
            isSep.add(false)
            val rem = n - 1 - i
            if (rem > 0 && rem % 3 == 0) {
                sb.append('٬')
                isSep.add(true)
            }
        }
        val origToTrans = IntArray(n + 1)
        val transToOrig = IntArray(isSep.size + 1)
        var oc = 0
        for (k in isSep.indices) {
            if (!isSep[k]) {
                oc++
                origToTrans[oc] = k + 1
            }
            transToOrig[k + 1] = oc
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int): Int = transToOrig[offset.coerceIn(0, isSep.size)]
        }
        return TransformedText(AnnotatedString(sb.toString()), mapping)
    }
}

/** value فقط رقم‌های لاتین است؛ نمایش با جداکننده و رقم فارسی، و زیرش مبلغ به حروف */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    toman: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(SmsParser.normalize(it).filter { c -> c.isDigit() }.take(15)) },
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = ThousandsTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        val n = value.toLongOrNull()
        if (n != null && n > 0) {
            Text(
                numberToWords(n) + if (toman) " تومان" else " ریال",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

// ───────────── تاریخ و ساعت شمسی ─────────────
@Composable
private fun DateBox(value: String, label: String, maxLen: Int, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(SmsParser.normalize(it).filter { c -> c.isDigit() }.take(maxLen)) },
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** ویرایشگر تاریخ شمسی (سال/ماه/روز و در صورت نیاز ساعت/دقیقه). فقط وقتی تاریخ معتبر باشد onChange صدا زده می‌شود. */
@Composable
fun DateTimeEditor(initial: Long, withTime: Boolean = true, onChange: (Long) -> Unit) {
    val p = remember { jalaliParts(initial) }
    var y by remember { mutableStateOf(p[0].toString()) }
    var m by remember { mutableStateOf(p[1].toString()) }
    var d by remember { mutableStateOf(p[2].toString()) }
    var h by remember { mutableStateOf(p[3].toString()) }
    var mi by remember { mutableStateOf(p[4].toString()) }

    val ms = jalaliToMillis(
        y.toIntOrNull() ?: -1, m.toIntOrNull() ?: -1, d.toIntOrNull() ?: -1,
        if (withTime) h.toIntOrNull() ?: -1 else 0,
        if (withTime) mi.toIntOrNull() ?: -1 else 0
    )
    LaunchedEffect(ms) { if (ms != null) onChange(ms) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DateBox(y, "سال", 4, Modifier.weight(1.6f)) { y = it }
            DateBox(m, "ماه", 2, Modifier.weight(1f)) { m = it }
            DateBox(d, "روز", 2, Modifier.weight(1f)) { d = it }
            if (withTime) {
                DateBox(h, "ساعت", 2, Modifier.weight(1f)) { h = it }
                DateBox(mi, "دقیقه", 2, Modifier.weight(1f)) { mi = it }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val q = jalaliParts(System.currentTimeMillis())
                y = q[0].toString(); m = q[1].toString(); d = q[2].toString()
                h = q[3].toString(); mi = q[4].toString()
            }) { Text("اکنون") }
            if (ms == null) {
                Text("تاریخ نامعتبر است", color = Expense, fontSize = 12.sp)
            } else {
                Text(
                    if (withTime) formatDate(ms) else formatDay(ms),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ───────────── متن اشتراک‌گذاری ─────────────
fun buildShareText(
    isIn: Boolean, amountRial: Long, time: Long, note: String, tags: List<String>,
    balance: Long?, account: String?, toman: Boolean
): String = buildString {
    appendLine("🧾 " + (if (isIn) "واریز" else "برداشت") + (account?.let { " • $it" } ?: ""))
    appendLine("مبلغ: " + formatMoney(amountRial, toman))
    appendLine("تاریخ: " + formatDate(time))
    if (note.isNotBlank()) appendLine("توضیحات: $note")
    if (tags.isNotEmpty()) appendLine("تگ‌ها: " + tags.joinToString(" ") { "#" + it.replace(' ', '_') })
    if (balance != null) appendLine("مانده: " + formatMoney(balance, toman))
}.trimEnd()

// ───────────── فرم توضیح / تگ / ویرایش / حذف / اشتراک ─────────────
/**
 * full = true  → ویرایش کامل (نوع، مبلغ و تاریخ هم قابل تغییرند) — از داخل برنامه
 * full = false → توضیح، تگ و حساب — از آیکون شناور پیامک
 */
@Composable
fun TxnEditor(
    txnId: Long,
    onLater: (() -> Unit)? = null,
    full: Boolean = false,
    onFinish: () -> Unit
) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val allTags by dao.observeTags().collectAsState(initial = emptyList())
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    var txn by remember { mutableStateOf<Txn?>(null) }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var newTag by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var accountId by remember { mutableLongStateOf(0L) }
    var timeMs by remember { mutableLongStateOf(0L) }
    var showDate by remember { mutableStateOf(false) }
    val toman = remember { Prefs.toman(ctx) }

    LaunchedEffect(txnId) {
        dao.getTxn(txnId)?.let {
            txn = it
            note = it.note
            selected = it.tagList().toSet()
            isIn = it.isIn()
            accountId = it.accountId
            timeMs = it.time
            amountText = (if (toman) it.amount / 10 else it.amount).toString()
        }
    }

    // تگ جدید همان لحظه در لیست تگ‌ها ذخیره می‌شود
    fun addNew() {
        val n = newTag.replace("|", " ").trim().take(24)
        if (n.isNotEmpty()) {
            selected = selected + n
            scope.launch { dao.insertTag(Tag(n)) }
        }
        newTag = ""
    }

    val t = txn
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 10.dp
    ) {
        if (t == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val color = if (isIn) Income else Expense
            val shownTime = if (timeMs != 0L) timeMs else t.time

            fun currentAmount(): Long {
                val v = amountText.toLongOrNull()
                return if (full && v != null && v > 0) (if (toman) v * 10 else v) else t.amount
            }

            fun shareNow() {
                val text = buildShareText(
                    isIn, currentAmount(), shownTime, note.trim(), selected.toList(), t.balance,
                    accounts.firstOrNull { a -> a.id == accountId }?.name, toman
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                try {
                    ctx.startActivity(
                        Intent.createChooser(send, "اشتراک‌گذاری تراکنش").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }

            Column(
                Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // سربرگ
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).background(color.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isIn) "↓" else "↑", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (isIn) "واریز" else "برداشت", color = color, fontWeight = FontWeight.Bold)
                        Text(
                            formatDate(shownTime) +
                                (accounts.firstOrNull { a -> a.id == accountId }?.let { a -> "  •  " + a.name } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        formatMoney(currentAmount(), toman),
                        color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                t.balance?.let {
                    Text(
                        "مانده حساب: " + formatMoney(it, toman),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // انتخاب حساب (اگر تشخیص خودکار اشتباه بود)
                if (accounts.size > 1) {
                    Text("حساب بانکی", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        accounts.forEach { a ->
                            FilterChip(
                                selected = a.id == accountId,
                                onClick = { accountId = a.id },
                                label = { Text(a.name) },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }

                // ویرایش نوع، مبلغ و تاریخ (فقط از داخل برنامه)
                if (full) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("برداشت") }, shape = RoundedCornerShape(50))
                        FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("واریز") }, shape = RoundedCornerShape(50))
                    }
                    MoneyField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = if (toman) "مبلغ (تومان)" else "مبلغ (ریال)",
                        toman = toman
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📅 " + formatDate(shownTime))
                        TextButton(onClick = { showDate = !showDate }) { Text(if (showDate) "بستن" else "تغییر تاریخ") }
                    }
                    if (showDate) DateTimeEditor(initial = shownTime) { timeMs = it }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات (مثلاً: خرید نان)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("برچسب‌ها (یک یا چند مورد)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TagPicker(
                    allTags = allTags,
                    selected = selected,
                    onToggle = { n -> selected = if (n in selected) selected - n else selected + n },
                    newText = newTag,
                    onNewText = { newTag = it },
                    onAdd = { addNew() }
                )

                if (confirmDelete) {
                    Surface(color = Expense.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("این تراکنش حذف شود؟", color = Expense, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { confirmDelete = false }) { Text("خیر") }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            dao.deleteTxnById(t.id)
                                            Notifier.cancel(ctx, t.id)
                                            onFinish()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Expense)
                                ) { Text("حذف") }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { confirmDelete = true }) { Text("🗑 حذف", color = Expense) }
                            TextButton(onClick = { shareNow() }) { Text("↗ اشتراک‌گذاری") }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onLater ?: onFinish) { Text(if (full) "انصراف" else "بعداً") }
                            Button(
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    scope.launch {
                                        // اگر تگی تایپ شده ولی «افزودن» زده نشده، همین‌جا اضافه می‌شود
                                        val pending = newTag.replace("|", " ").trim().take(24)
                                        val tags = (selected + (if (pending.isNotEmpty()) setOf(pending) else emptySet()))
                                            .map { it.trim() }.filter { it.isNotEmpty() }
                                        val before = t.tagList().toSet()
                                        tags.forEach {
                                            dao.insertTag(Tag(it))
                                            if (it !in before) dao.bumpTag(it)
                                        }
                                        var updated = t.copy(
                                            note = note.trim(),
                                            tags = tags.joinToString("|"),
                                            reviewed = true,
                                            accountId = accountId,
                                            time = shownTime
                                        )
                                        if (full) {
                                            val v = amountText.toLongOrNull()
                                            if (v != null && v > 0) {
                                                updated = updated.copy(
                                                    type = if (isIn) "IN" else "OUT",
                                                    amount = if (toman) v * 10 else v
                                                )
                                            }
                                        }
                                        dao.updateTxn(updated)
                                        Notifier.cancel(ctx, t.id)
                                        onFinish()
                                    }
                                }
                            ) { Text("ذخیره") }
                        }
                    }
                }
            }
        }
    }
}
