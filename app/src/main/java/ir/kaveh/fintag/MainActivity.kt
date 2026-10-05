@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.kaveh.fintag

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { AppTheme { MainScreen(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

// ───────────── اجزای مشترک ─────────────
@Composable
fun GradientHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF134E4A))),
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content
    )
}

@Composable
fun StatBox(label: String, amount: Long, toman: Boolean, arrow: String, tint: Color, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(arrow, color = tint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Text(formatMoney(amount, toman), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

// ───────────── صفحه‌ی اصلی ─────────────
@Composable
fun MainScreen(tick: Int) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val txns by dao.observeTxns().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var toman by remember { mutableStateOf(Prefs.toman(ctx)) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf("تراکنش‌ها" to "🧾", "گزارش" to "📊", "تنظیمات" to "⚙️")
                items.forEachIndexed { i, (label, emoji) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = Teal,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("＋  تراکنش جدید", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeTab(txns, toman) { editId = it }
                1 -> ReportTab(txns, toman)
                else -> SettingsTab(tick, toman) { toman = it }
            }
        }
    }

    editId?.let { id ->
        Dialog(
            onDismissRequest = { editId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.padding(16.dp)) { TxnEditor(id, full = true) { editId = null } }
        }
    }
    if (showAdd) {
        ManualAddDialog(
            onDismiss = { showAdd = false },
            onCreated = { showAdd = false; editId = it }
        )
    }
}

// ───────────── تب تراکنش‌ها ─────────────
@Composable
fun HomeTab(txns: List<Txn>, toman: Boolean, onOpen: (Long) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val start = remember { monthStart() }
    val month = txns.filter { it.time >= start }
    val income = month.filter { it.isIn() }.sumOf { it.amount }
    val expense = month.filter { !it.isIn() }.sumOf { it.amount }
    val pending = txns.count { !it.reviewed }

    val q = SmsParser.normalize(query).trim()
    val shown = txns.filter { t ->
        val byFilter = when (filter) {
            1 -> !t.reviewed
            2 -> t.isIn()
            3 -> !t.isIn()
            else -> true
        }
        val byQuery = q.isEmpty() || t.note.contains(q) || t.tags.contains(q) || t.amount.toString().contains(q)
        byFilter && byQuery
    }
    val groups = shown.groupBy { formatDay(it.time) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            GradientHeader {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("مدیریت مالی", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(monthTitle(), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text("تراز این ماه", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Text(formatMoney(income - expense, toman), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("درآمد", income, toman, "↓", Color(0xFF86EFAC), Modifier.weight(1f))
                    StatBox("هزینه", expense, toman, "↑", Color(0xFFFCA5A5), Modifier.weight(1f))
                }
            }
        }
        item {
            Row(
                Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("همه", "بدون توضیح (${toFa(pending.toString())})", "واریز", "برداشت").forEachIndexed { i, label ->
                    FilterChip(
                        selected = filter == i,
                        onClick = { filter = i },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جستجو در توضیحات و تگ‌ها") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            )
        }
        if (shown.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📭", fontSize = 44.sp)
                    Text("تراکنشی پیدا نشد", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        for ((_, list) in groups) {
            item {
                Text(
                    dayTitle(list.first().time),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(list, key = { it.id }) { t -> TxnRow(t, toman) { onOpen(t.id) } }
        }
    }
}

@Composable
fun TxnRow(t: Txn, toman: Boolean, onClick: () -> Unit) {
    val color = if (t.isIn()) Income else Expense
    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (t.isIn()) "↓" else "↑", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.note.isNotBlank()) {
                    Text(t.note, fontWeight = FontWeight.Medium, maxLines = 2)
                } else {
                    Text("بدون توضیح", color = MaterialTheme.colorScheme.outline)
                }
                if (t.tagList().isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        t.tagList().forEach { TagChip(it) }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatMoney(t.amount, toman), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(formatTime(t.time), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                if (!t.reviewed) Text("● نیازمند توضیح", color = Color(0xFFEA580C), fontSize = 10.sp)
            }
        }
    }
}

// ───────────── تب گزارش ─────────────
@Composable
fun ReportTab(list: List<Txn>, toman: Boolean) {
    var kind by remember { mutableIntStateOf(0) } // 0 = هزینه، 1 = درآمد
    val start = remember { monthStart() }
    val month = list.filter { it.time >= start }
    val income = month.filter { it.isIn() }.sumOf { it.amount }
    val expense = month.filter { !it.isIn() }.sumOf { it.amount }
    val src = month.filter { it.isIn() == (kind == 1) }
    val total = src.sumOf { it.amount }
    val byTag = src
        .flatMap { t -> t.tagList().ifEmpty { listOf("بدون تگ") }.map { it to t.amount } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GradientHeader {
                Text("گزارش ماهانه", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(monthTitle(), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }
        item {
            Card(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("درآمد")
                        Text(formatMoney(income, toman), color = Income, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("هزینه")
                        Text(formatMoney(expense, toman), color = Expense, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
                    ) {
                        if (income > 0) Box(Modifier.weight(income.toFloat()).fillMaxHeight().background(Income))
                        if (expense > 0) Box(Modifier.weight(expense.toFloat()).fillMaxHeight().background(Expense))
                        if (income == 0L && expense == 0L) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تراز", fontWeight = FontWeight.Bold)
                        Text(
                            formatMoney(income - expense, toman),
                            color = if (income >= expense) Income else Expense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = kind == 0, onClick = { kind = 0 }, label = { Text("هزینه‌ها") }, shape = RoundedCornerShape(50))
                FilterChip(selected = kind == 1, onClick = { kind = 1 }, label = { Text("درآمدها") }, shape = RoundedCornerShape(50))
            }
        }
        if (byTag.isEmpty()) {
            item {
                Text(
                    "داده‌ای برای نمایش نیست",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        items(byTag) { (tag, sum) ->
            val c = tagColor(tag)
            val frac = (sum.toFloat() / total.coerceAtLeast(1L).toFloat()).coerceIn(0.02f, 1f)
            val percent = (sum * 100 / total.coerceAtLeast(1L)).toInt()
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(c, CircleShape))
                        Text(tag, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        formatMoney(sum, toman) + "  (" + toFa(percent.toString()) + "٪)",
                        fontSize = 12.sp
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(c, RoundedCornerShape(50)))
                }
            }
        }
        item {
            Text(
                "تراکنشی که چند تگ دارد، در هر تگ جداگانه حساب می‌شود.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ───────────── تب تنظیمات ─────────────
@Composable
fun SettingsTab(tick: Int, toman: Boolean, onToman: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).dao() }
    val tags by dao.observeTags().collectAsState(initial = emptyList())
    var popup by remember { mutableStateOf(Prefs.popup(ctx)) }
    var permTick by remember { mutableIntStateOf(0) }
    var newTagName by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }

    val smsOk = remember(tick, permTick) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }
    val notifOk = remember(tick, permTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val overlayOk = remember(tick, permTick) { Settings.canDrawOverlays(ctx) }

    val addTag: () -> Unit = {
        val n = newTagName.replace("|", " ").trim().take(24)
        if (n.isNotEmpty()) scope.launch { dao.insertTag(Tag(n)) }
        newTagName = ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        GradientHeader {
            Text("تنظیمات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("مجوزها، برچسب‌ها و نمایش", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        SectionCard("مجوزها") {
            PermRow("دریافت پیامک", smsOk) {
                launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
            }
            PermRow("اعلان‌ها", notifOk) {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            PermRow("نمایش آیکون شناور روی سایر برنامه‌ها", overlayOk) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("باز کردن تنظیمات برنامه") }
            Text(
                "اگر اندروید گزینه‌ی پیامک را مسدود کرد: تنظیمات برنامه ← منوی سه‌نقطه ← «Allow restricted settings» و سپس دوباره مجوز را بدهید.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        SectionCard("نمایش") {
            SwitchRow("آیکون شناور هنگام دریافت پیامک", popup) { popup = it; Prefs.setPopup(ctx, it) }
            SwitchRow("نمایش مبالغ به تومان (خاموش = ریال)", toman) { Prefs.setToman(ctx, it); onToman(it) }
        }

        SectionCard("مدیریت تگ‌ها") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tg ->
                    val c = tagColor(tg.name)
                    Row(
                        Modifier
                            .background(c.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#" + tg.name, color = c, fontSize = 12.sp)
                        Text(
                            "✕",
                            color = c,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { scope.launch { dao.deleteTag(tg.name) } }
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("تگ جدید") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = addTag, shape = RoundedCornerShape(14.dp)) { Text("افزودن") }
            }
        }

        SectionCard("آزمایش") {
            Button(
                onClick = {
                    scope.launch {
                        val inn = Random.nextBoolean()
                        val amt = Random.nextInt(2, 400) * 50_000L
                        val body = (if (inn) "واریز: " else "برداشت: ") +
                            String.format(Locale.US, "%,d", amt) + (if (inn) "+" else "-") +
                            "\nمانده: 12,450,000\n1405/07/12 14:35"
                        withContext(Dispatchers.IO) {
                            Pipeline.handle(ctx.applicationContext, "TEST", body, System.currentTimeMillis(), force = true)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("شبیه‌سازی یک پیامک بانکی") }
        }
    }
}

@Composable
fun PermRow(label: String, ok: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (ok) {
            Box(
                Modifier
                    .background(Income.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("فعال ✓", color = Income, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            FilledTonalButton(onClick = onGrant, shape = RoundedCornerShape(50)) { Text("فعال‌سازی") }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ───────────── افزودن دستی ─────────────
@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toman = remember { Prefs.toman(ctx) }
    var amount by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("تراکنش دستی", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("برداشت") }, shape = RoundedCornerShape(50))
                    FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("واریز") }, shape = RoundedCornerShape(50))
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = SmsParser.normalize(it).filter { c -> c.isDigit() } },
                    label = { Text(if (toman) "مبلغ (تومان)" else "مبلغ (ریال)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toLongOrNull()?.let { v ->
                    scope.launch {
                        val rial = if (toman) v * 10 else v
                        val id = AppDb.get(ctx).dao().insertTxn(
                            Txn(
                                type = if (isIn) "IN" else "OUT",
                                amount = rial,
                                sender = "manual",
                                time = System.currentTimeMillis()
                            )
                        )
                        onCreated(id)
                    }
                }
            }) { Text("ادامه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
