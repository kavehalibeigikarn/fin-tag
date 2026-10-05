@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.kaveh.fintag

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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

// ───────────── فرم توضیح / تگ / ویرایش / حذف ─────────────
/**
 * full = true  → ویرایش کامل (نوع و مبلغ هم قابل تغییر است) — از داخل برنامه
 * full = false → فقط توضیح و تگ — از پاپ‌آپ پیامک
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
    var txn by remember { mutableStateOf<Txn?>(null) }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var newTag by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var accountId by remember { mutableLongStateOf(0L) }
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    val toman = remember { Prefs.toman(ctx) }

    LaunchedEffect(txnId) {
        dao.getTxn(txnId)?.let {
            txn = it
            note = it.note
            selected = it.tagList().toSet()
            isIn = it.isIn()
            accountId = it.accountId
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
                            formatDate(t.time) + (accounts.firstOrNull { a -> a.id == accountId }?.let { a -> "  •  " + a.name } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        formatMoney(t.amount, toman),
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

                // ویرایش نوع و مبلغ و حساب (فقط از داخل برنامه)
                if (full) {
                    if (accounts.isNotEmpty()) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("برداشت") }, shape = RoundedCornerShape(50))
                        FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("واریز") }, shape = RoundedCornerShape(50))
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = SmsParser.normalize(it).filter { c -> c.isDigit() } },
                        label = { Text(if (toman) "مبلغ (تومان)" else "مبلغ (ریال)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات (مثلاً: خرید نان)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("برچسب‌ها", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val names = allTags.map { it.name } + selected.filter { s -> allTags.none { it.name == s } }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    names.forEach { n ->
                        val c = tagColor(n)
                        FilterChip(
                            selected = n in selected,
                            onClick = { selected = if (n in selected) selected - n else selected + n },
                            label = { Text(n) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.copy(alpha = 0.18f),
                                selectedLabelColor = c
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("تگ جدید") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addNew() }),
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = { addNew() }, shape = RoundedCornerShape(14.dp)) { Text("افزودن") }
                }

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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { confirmDelete = true }) { Text("🗑 حذف", color = Expense) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            accountId = accountId
                                        )
                                        if (full) {
                                            val v = SmsParser.normalize(amountText).filter { it.isDigit() }.toLongOrNull()
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
