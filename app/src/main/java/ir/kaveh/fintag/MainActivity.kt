@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.kaveh.fintag

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import java.text.SimpleDateFormat
import java.util.Date
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

// ───────────── انتخاب حساب (هر بانک یک صفحه) ─────────────
@Composable
private fun AccountCard(name: String, balance: Long, selected: Boolean, toman: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.widthIn(min = 110.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(formatMoney(balance, toman), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun AccountBar(stats: List<AccountStat>, selected: Long, toman: Boolean, onSelect: (Long) -> Unit) {
    if (stats.isEmpty()) {
        Text(
            "هنوز حسابی ثبت نشده. با اولین پیامک هر بانک، حساب آن خودکار ساخته می‌شود؛ یا از تنظیمات حساب اضافه کن.",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    } else {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            stats.forEach { s ->
                AccountCard(s.account.name, s.balance, selected == s.account.id, toman) { onSelect(s.account.id) }
            }
            if (stats.size > 1) {
                AccountCard("همه حساب‌ها", stats.sumOf { it.balance }, selected == 0L, toman) { onSelect(0L) }
            }
        }
    }
}

// ───────────── صفحه‌ی اصلی ─────────────
@Composable
fun MainScreen(tick: Int) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val txns by dao.observeTxns().collectAsState(initial = emptyList())
    val accounts by dao.observeAccounts().collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var toman by remember { mutableStateOf(Prefs.toman(ctx)) }
    var editId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var accSel by remember { mutableLongStateOf(-1L) } // -1 = انتخاب نشده (حساب اول)، 0 = همه

    val mStart = remember { monthStart() }
    val stats = remember(accounts, txns) { accounts.map { computeStat(it, txns, mStart) } }
    val accId: Long =
        if (accSel == 0L) 0L
        else accounts.firstOrNull { it.id == accSel }?.id ?: accounts.firstOrNull()?.id ?: 0L
    val scoped = remember(txns, accId) {
        if (accId == 0L) txns else txns.filter { it.accountId == accId }
    }
    val current = stats.firstOrNull { it.account.id == accId }

    // تراکنش‌های قدیمی (بدون حساب) را به حساب‌ها نسبت بده
    LaunchedEffect(txns) {
        if (txns.any { it.accountId == 0L }) {
            withContext(Dispatchers.IO) { Accounts.assignUnassigned(ctx.applicationContext) }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                0 -> HomeTab(scoped, stats, accId, current, toman, onSelect = { accSel = it }) { editId = it }
                1 -> ReportTab(scoped, stats, accId, current, toman) { accSel = it }
                else -> SettingsTab(tick, toman, stats, accId) { toman = it }
            }
        }
    }

    // فرم ویرایش داخل خود صفحه نشان داده می‌شود (نه Dialog) تا دکمه‌ها بالای نوار دکمه‌های اندروید بمانند
    editId?.let { id ->
        BackHandler { editId = null }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { TxnEditor(id, full = true) { editId = null } }
        }
    }
    }
    if (showAdd) {
        ManualAddDialog(
            accounts = accounts,
            defaultAccountId = accId,
            onDismiss = { showAdd = false },
            onCreated = { showAdd = false; editId = it }
        )
    }
}

// ───────────── تب تراکنش‌ها ─────────────
@Composable
fun HomeTab(
    txns: List<Txn>,
    stats: List<AccountStat>,
    accId: Long,
    current: AccountStat?,
    toman: Boolean,
    onSelect: (Long) -> Unit,
    onOpen: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val allTags by dao.observeTags().collectAsState(initial = emptyList())
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var preset by remember { mutableIntStateOf(0) }       // 0 همه، 1 این ماه، 2 ماه قبل، 3 هفت روز اخیر، 4 دلخواه
    var filterTags by remember { mutableStateOf(setOf<String>()) }
    var customFrom by remember { mutableLongStateOf(0L) }
    var customTo by remember { mutableLongStateOf(Long.MAX_VALUE) }

    val now = System.currentTimeMillis()
    val start = remember { monthStart() }
    val prevStart = remember { monthStart(start - 1) }
    val month = txns.filter { it.time >= start }
    val income = month.filter { it.isIn() }.sumOf { it.amount }
    val expense = month.filter { !it.isIn() }.sumOf { it.amount }
    val pending = txns.count { !it.reviewed }

    val title = current?.account?.name ?: "همه حساب‌ها"
    val balance = current?.balance ?: stats.sumOf { it.balance }
    val monthStartBalance = current?.monthStartBalance ?: stats.sumOf { it.monthStartBalance }
    val lastSms = current?.lastSmsBalance

    val range: Pair<Long, Long> = when (preset) {
        1 -> start to Long.MAX_VALUE
        2 -> prevStart to (start - 1)
        3 -> (now - 7 * 86_400_000L) to Long.MAX_VALUE
        4 -> customFrom to customTo
        else -> 0L to Long.MAX_VALUE
    }

    val q = SmsParser.normalize(query).trim()
    val shown = txns.filter { t ->
        val byFilter = when (filter) {
            1 -> !t.reviewed
            2 -> t.isIn()
            3 -> !t.isIn()
            else -> true
        }
        val byQuery = q.isEmpty() || t.note.contains(q) || t.tags.contains(q) || t.amount.toString().contains(q)
        val byTag = filterTags.isEmpty() || t.tagList().any { it in filterTags }
        val byDate = t.time >= range.first && t.time <= range.second
        byFilter && byQuery && byTag && byDate
    }
    val groups = shown.groupBy { formatDay(it.time) }
    val filtersActive = filterTags.isNotEmpty() || preset != 0 || q.isNotEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            GradientHeader {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(monthTitle(), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("موجودی فعلی", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Text(formatMoney(balance, toman), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "موجودی اول ماه: " + formatMoney(monthStartBalance, toman),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
                if (lastSms != null) {
                    Text(
                        "آخرین مانده‌ی پیامک بانک: " + formatMoney(lastSms, toman),
                        color = if (lastSms == balance) Color.White.copy(alpha = 0.75f) else Color(0xFFFDE68A),
                        fontSize = 12.sp
                    )
                }
                if (current != null && current.account.openingBalance == 0L) {
                    Text(
                        "برای دقیق‌شدن موجودی، «موجودی اول دوره» را در تنظیمات ← حساب‌های بانکی وارد کن.",
                        color = Color(0xFFFDE68A),
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("درآمد ماه", income, toman, "↓", Color(0xFF86EFAC), Modifier.weight(1f))
                    StatBox("هزینه ماه", expense, toman, "↑", Color(0xFFFCA5A5), Modifier.weight(1f))
                }
            }
        }
        item { AccountBar(stats, accId, toman, onSelect) }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
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
                FilterChip(
                    selected = showFilters || preset != 0 || filterTags.isNotEmpty(),
                    onClick = { showFilters = !showFilters },
                    label = { Text(if (showFilters) "▲ فیلتر تگ و تاریخ" else "🔎 فیلتر تگ و تاریخ") },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        if (showFilters) {
            item {
                Card(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بازه‌ی زمانی", fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("همه", "این ماه", "ماه قبل", "۷ روز اخیر", "بازه‌ی دلخواه").forEachIndexed { i, label ->
                                FilterChip(
                                    selected = preset == i,
                                    onClick = {
                                        preset = i
                                        if (i == 4 && customTo == Long.MAX_VALUE) {
                                            customFrom = start
                                            customTo = now
                                        }
                                    },
                                    label = { Text(label) },
                                    shape = RoundedCornerShape(50)
                                )
                            }
                        }
                        if (preset == 4) {
                            Text("از تاریخ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            DateTimeEditor(initial = customFrom, withTime = false) { customFrom = it }
                            Text("تا تاریخ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            DateTimeEditor(initial = customTo, withTime = false) { customTo = it + 86_399_999L }
                        }
                        Text("برچسب‌ها (تراکنش‌هایی که هر یک از تگ‌های انتخاب‌شده را دارند)", fontWeight = FontWeight.Bold)
                        TagPicker(
                            allTags = allTags,
                            selected = filterTags,
                            onToggle = { n -> filterTags = if (n in filterTags) filterTags - n else filterTags + n }
                        )
                        if (preset != 0 || filterTags.isNotEmpty()) {
                            TextButton(onClick = { preset = 0; filterTags = emptySet() }) { Text("پاک کردن فیلترها") }
                        }
                    }
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
        if (filtersActive) {
            item {
                val sumIn = shown.filter { it.isIn() }.sumOf { it.amount }
                val sumOut = shown.filter { !it.isIn() }.sumOf { it.amount }
                val net = sumIn - sumOut
                Card(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("خلاصه‌ی نتیجه‌ی فیلتر", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تعداد تراکنش")
                            Text(toFa(shown.size.toString()), fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جمع واریزها")
                            Text(formatMoney(sumIn, toman), color = Income, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جمع برداشت‌ها")
                            Text(formatMoney(sumOut, toman), color = Expense, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جمع جبری (واریز − برداشت)", fontWeight = FontWeight.Bold)
                            Text(
                                formatMoney(net, toman),
                                color = if (net >= 0) Income else Expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
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
fun ReportTab(
    list: List<Txn>,
    stats: List<AccountStat>,
    accId: Long,
    current: AccountStat?,
    toman: Boolean,
    onSelect: (Long) -> Unit
) {
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
                Text(
                    (current?.account?.name ?: "همه حساب‌ها") + "  •  " + monthTitle(),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
        }
        item { AccountBar(stats, accId, toman, onSelect) }
        item {
            Card(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (current != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("موجودی اول ماه")
                            Text(formatMoney(current.monthStartBalance, toman), fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("درآمد")
                        Text(formatMoney(income, toman), color = Income, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("هزینه")
                        Text(formatMoney(expense, toman), color = Expense, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                        if (income > 0) Box(Modifier.weight(income.toFloat()).fillMaxHeight().background(Income))
                        if (expense > 0) Box(Modifier.weight(expense.toFloat()).fillMaxHeight().background(Expense))
                        if (income == 0L && expense == 0L) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تراز ماه", fontWeight = FontWeight.Bold)
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
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun SettingsTab(
    tick: Int,
    toman: Boolean,
    stats: List<AccountStat>,
    selectedAccId: Long,
    onToman: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).dao() }
    val tags by dao.observeTags().collectAsState(initial = emptyList())
    var popup by remember { mutableStateOf(Prefs.popup(ctx)) }
    var relaxed by remember { mutableStateOf(Prefs.relaxed(ctx)) }
    val smsLog by dao.observeLog().collectAsState(initial = emptyList())
    var permTick by remember { mutableIntStateOf(0) }
    var newTagName by remember { mutableStateOf("") }
    var editAcc by remember { mutableStateOf<Account?>(null) }
    var newAcc by remember { mutableStateOf(false) }
    var deleteAcc by remember { mutableStateOf<Account?>(null) }
    var exportAcc by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) {
            scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openOutputStream(uri)?.use { Backup.export(ctx.applicationContext, it, exportAcc) }
                        "فایل اکسل ذخیره شد."
                    } catch (e: Exception) {
                        "خطا در ذخیره‌ی فایل: " + (e.message ?: "")
                    }
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        val r = ctx.contentResolver.openInputStream(uri)?.use { Backup.import(ctx.applicationContext, it) }
                        if (r == null) "فایل خوانده نشد."
                        else "${toFa(r.added.toString())} تراکنش اضافه شد، " +
                            "${toFa(r.duplicates.toString())} تراکنش تکراری نادیده گرفته شد، " +
                            "${toFa(r.newAccounts.toString())} حساب جدید ساخته شد."
                    } catch (e: Exception) {
                        "خطا در خواندن فایل: " + (e.message ?: "")
                    }
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
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
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    val selectedName = stats.firstOrNull { it.account.id == selectedAccId }?.account?.name
    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        GradientHeader {
            Text("تنظیمات", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("حساب‌ها، اکسل، مجوزها و برچسب‌ها  •  نسخه " + toFa(versionName), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        SectionCard("حساب‌های بانکی") {
            if (stats.isEmpty()) {
                Text(
                    "هنوز حسابی ثبت نشده. با اولین پیامک هر بانک، حساب آن خودکار ساخته می‌شود.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            stats.forEach { s ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.account.name, fontWeight = FontWeight.Bold)
                        Text(
                            "موجودی: " + formatMoney(s.balance, toman),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (s.account.senders.isNotBlank()) {
                            Text(
                                "فرستنده: " + s.account.senders,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    OutlinedButton(onClick = { editAcc = s.account }, shape = RoundedCornerShape(50)) { Text("ویرایش") }
                    TextButton(onClick = { deleteAcc = s.account }) { Text("حذف", color = Expense) }
                }
                HorizontalDivider()
            }
            FilledTonalButton(onClick = { newAcc = true }, shape = RoundedCornerShape(14.dp)) { Text("＋ حساب جدید") }
        }

        SectionCard("اکسل و پشتیبان‌گیری") {
            Text(
                "فایل اکسل شامل حساب‌ها (با موجودی اول دوره)، تراکنش‌ها (با توضیح، تگ و تاریخ) و تگ‌هاست. " +
                    "با «ورود از اکسل» همین فایل را روی نسخه‌ی جدید برنامه برمی‌گردانی؛ تراکنش‌های تکراری دوباره ثبت نمی‌شوند.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            if (selectedAccId != 0L && selectedName != null) {
                Button(
                    onClick = {
                        exportAcc = selectedAccId
                        exportLauncher.launch("FinTag-account-$stamp.xlsx")
                    },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("خروجی اکسل — فقط «$selectedName»") }
            }
            Button(
                onClick = {
                    exportAcc = 0L
                    exportLauncher.launch("FinTag-all-$stamp.xlsx")
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("خروجی اکسل — همه حساب‌ها") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(14.dp)
            ) { Text("ورود از فایل اکسل") }
        }

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
            OutlinedButton(
                onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("بدون محدودیت باتری (برای ثبت مطمئن پیامک‌ها)") }
            Text(
                "برای اینکه اندروید برنامه را در پس‌زمینه نبندد، در لیست باز‌شده «FinTag/مدیریت مالی» را روی «Don't optimize / بدون محدودیت» بگذار.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "اگر اندروید گزینه‌ی پیامک را مسدود کرد: تنظیمات برنامه ← منوی سه‌نقطه ← «Allow restricted settings» و سپس دوباره مجوز را بدهید.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        SectionCard("نمایش") {
            SwitchRow("آیکون شناور هنگام دریافت پیامک", popup) { popup = it; Prefs.setPopup(ctx, it) }
            SwitchRow("نمایش مبالغ به تومان (خاموش = ریال)", toman) { Prefs.setToman(ctx, it); onToman(it) }
            SwitchRow("پیامک بدون «مانده» هم ثبت شود (پیامک‌های رمز/کد همچنان رد می‌شوند)", relaxed) {
                relaxed = it; Prefs.setRelaxed(ctx, it)
            }
        }

        SectionCard("پیامک‌های دیده‌شده (عیب‌یابی)") {
            Text(
                "اگر تراکنشی ثبت نشد، اینجا می‌بینی پیامکش رسیده یا نه و چرا رد شده. برای پیامکِ ردشده می‌توانی دستی «ثبت» بزنی.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            if (smsLog.isEmpty()) Text("هنوز پیامک مالی دیده نشده.", fontSize = 12.sp)
            smsLog.take(15).forEach { l ->
                val c = when (l.status) { "ثبت شد" -> Income; "ردشد", "خطا" -> Expense; else -> MaterialTheme.colorScheme.outline }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(l.status, color = c, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(formatDate(l.time) + "  •  " + l.sender, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    if (l.reason.isNotBlank()) Text(l.reason, fontSize = 11.sp, color = c)
                    Text(l.body, fontSize = 11.sp, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (l.status == "ردشد" || l.status == "خطا") {
                        TextButton(onClick = {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    Pipeline.handle(
                                        ctx.applicationContext, l.sender, l.body, l.time,
                                        force = true, relaxedOverride = true
                                    )
                                }
                                Toast.makeText(
                                    ctx, if (r != null) "ثبت شد" else "قابل تشخیص نبود؛ از «تراکنش جدید» دستی ثبت کن",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text("ثبت به‌عنوان تراکنش") }
                    }
                }
                HorizontalDivider()
            }
            if (smsLog.isNotEmpty()) {
                TextButton(onClick = { scope.launch { dao.clearLog() } }) { Text("پاک کردن فهرست", color = Expense) }
            }
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
                            Pipeline.handle(
                                ctx.applicationContext, "TEST", body, System.currentTimeMillis(),
                                force = true,
                                forceAccountId = if (selectedAccId != 0L) selectedAccId else null
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("شبیه‌سازی یک پیامک بانکی (روی حساب انتخاب‌شده)") }
        }
    }

    if (editAcc != null || newAcc) {
        AccountEditorDialog(account = editAcc) { editAcc = null; newAcc = false }
    }
    deleteAcc?.let { acc ->
        DeleteAccountDialog(
            account = acc,
            others = stats.map { it.account }.filter { it.id != acc.id }
        ) { deleteAcc = null }
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

// ───────────── حذف حساب ─────────────
@Composable
fun DeleteAccountDialog(account: Account, others: List<Account>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).dao() }
    var moveTo by remember { mutableLongStateOf(0L) } // 0 = حذف تراکنش‌ها همراه حساب
    var count by remember { mutableIntStateOf(-1) }
    LaunchedEffect(account.id) { count = dao.getAllTxns().count { it.accountId == account.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("حذف حساب «${account.name}»", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (count >= 0) Text("این حساب ${toFa(count.toString())} تراکنش دارد.")
                if (others.isNotEmpty()) {
                    Text("با تراکنش‌های این حساب چه شود؟", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FilterChip(
                            selected = moveTo == 0L,
                            onClick = { moveTo = 0L },
                            label = { Text("حذف همراه حساب") },
                            shape = RoundedCornerShape(50)
                        )
                        others.forEach { o ->
                            FilterChip(
                                selected = moveTo == o.id,
                                onClick = { moveTo = o.id },
                                label = { Text("انتقال به " + o.name) },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                } else {
                    Text("تراکنش‌های این حساب هم حذف می‌شوند.", color = Expense)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (moveTo != 0L) dao.moveTxns(account.id, moveTo) else dao.deleteTxnsOfAccount(account.id)
                        dao.deleteAccountById(account.id)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Expense)
            ) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ───────────── ساخت / ویرایش حساب بانکی ─────────────
@Composable
fun AccountEditorDialog(account: Account?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.get(ctx).dao() }
    val toman = remember { Prefs.toman(ctx) }

    var name by remember { mutableStateOf(account?.name ?: "") }
    var senders by remember { mutableStateOf(account?.senders ?: "") }
    var hint by remember { mutableStateOf(account?.hint ?: "") }
    var opening by remember {
        mutableStateOf(
            account?.let {
                if (it.openingBalance == 0L) "" else (if (toman) it.openingBalance / 10 else it.openingBalance).toString()
            } ?: ""
        )
    }
    var period by remember { mutableIntStateOf(if (account == null) 2 else 0) }

    val options = if (account == null) {
        listOf(1 to "همه تراکنش‌ها", 2 to "ابتدای این ماه", 3 to "از همین لحظه")
    } else {
        listOf(0 to "بدون تغییر", 1 to "همه تراکنش‌ها", 2 to "ابتدای این ماه", 3 to "از همین لحظه")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (account == null) "حساب جدید" else "ویرایش حساب", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام حساب (مثلاً: ملت)") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senders, onValueChange = { senders = it },
                    label = { Text("شماره/نام فرستنده‌ی پیامک") },
                    supportingText = { Text("اگر چند شماره دارد با ویرگول جدا کن") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hint, onValueChange = { hint = it },
                    label = { Text("بخشی از شماره حساب/کارت (اختیاری)") },
                    supportingText = { Text("برای وقتی که چند حساب از یک بانک داری") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                MoneyField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = if (toman) "موجودی اول دوره (تومان)" else "موجودی اول دوره (ریال)",
                    toman = toman
                )
                Text("شروع دوره از:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEach { (code, label) ->
                        FilterChip(
                            selected = period == code,
                            onClick = { period = code },
                            label = { Text(label) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                Text(
                    "تراکنش‌های بعد از شروع دوره به موجودی اول دوره اضافه یا از آن کم می‌شوند؛ تراکنش‌های قبل‌تر در محاسبه نمی‌آیند.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        val v = opening.toLongOrNull() ?: 0L
                        val ob = if (toman) v * 10 else v
                        val ot = when (period) {
                            0 -> account?.openingTime ?: 0L
                            1 -> 0L
                            2 -> monthStart()
                            else -> System.currentTimeMillis()
                        }
                        if (account == null) {
                            dao.insertAccount(
                                Account(
                                    name = name.trim(), senders = senders.trim(), hint = hint.trim(),
                                    openingBalance = ob, openingTime = ot
                                )
                            )
                        } else {
                            dao.updateAccount(
                                account.copy(
                                    name = name.trim(), senders = senders.trim(), hint = hint.trim(),
                                    openingBalance = ob, openingTime = ot
                                )
                            )
                        }
                        onDismiss()
                    }
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ───────────── افزودن دستی ─────────────
@Composable
fun ManualAddDialog(
    accounts: List<Account>,
    defaultAccountId: Long,
    onDismiss: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toman = remember { Prefs.toman(ctx) }
    var amount by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(false) }
    var timeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var accountId by remember {
        mutableLongStateOf(if (defaultAccountId != 0L) defaultAccountId else accounts.firstOrNull()?.id ?: 0L)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("تراکنش دستی", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (accounts.isNotEmpty()) {
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
                MoneyField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = if (toman) "مبلغ (تومان)" else "مبلغ (ریال)",
                    toman = toman
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 " + formatDate(timeMs))
                    TextButton(onClick = { showDate = !showDate }) { Text(if (showDate) "بستن" else "تغییر تاریخ") }
                }
                if (showDate) DateTimeEditor(initial = timeMs) { timeMs = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toLongOrNull()?.let { v ->
                    if (v > 0) {
                        scope.launch {
                            val rial = if (toman) v * 10 else v
                            val id = AppDb.get(ctx).dao().insertTxn(
                                Txn(
                                    type = if (isIn) "IN" else "OUT",
                                    amount = rial,
                                    sender = "manual",
                                    time = timeMs,
                                    accountId = accountId
                                )
                            )
                            onCreated(id)
                        }
                    }
                }
            }) { Text("ادامه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
