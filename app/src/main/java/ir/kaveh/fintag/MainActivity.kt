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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
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
        enableEdgeToEdge()
        setContent { AppTheme { MainScreen(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

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
        topBar = { CenterAlignedTopAppBar(title = { Text("مدیریت مالی") }) },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    "تراکنش‌ها" to Icons.Default.Menu,
                    "گزارش" to Icons.Default.Info,
                    "تنظیمات" to Icons.Default.Settings
                )
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "افزودن")
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TxnList(txns, toman) { editId = it }
                1 -> Report(txns, toman)
                else -> SettingsTab(tick, toman) { toman = it }
            }
        }
    }

    editId?.let { id ->
        Dialog(
            onDismissRequest = { editId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.padding(16.dp)) { TxnEditor(id) { editId = null } }
        }
    }
    if (showAdd) {
        ManualAddDialog(
            onDismiss = { showAdd = false },
            onCreated = { showAdd = false; editId = it }
        )
    }
}

@Composable
fun TxnList(list: List<Txn>, toman: Boolean, onClick: (Long) -> Unit) {
    var onlyPending by remember { mutableStateOf(false) }
    val pendingCount = list.count { !it.reviewed }
    val shown = if (onlyPending) list.filter { !it.reviewed } else list
    Column {
        Row(Modifier.padding(horizontal = 12.dp)) {
            FilterChip(
                selected = onlyPending,
                onClick = { onlyPending = !onlyPending },
                label = { Text("فقط بدون توضیح (${toFa(pendingCount.toString())})") }
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("هنوز تراکنشی ثبت نشده", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.id }) { t -> TxnRow(t, toman) { onClick(t.id) } }
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, toman: Boolean, onClick: () -> Unit) {
    val color = if (t.isIn()) Color(0xFF2E7D32) else Color(0xFFC62828)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (t.isIn()) "واریز  " else "برداشت  ") + formatMoney(t.amount, toman),
                    color = color, fontWeight = FontWeight.Bold
                )
                Text(formatDate(t.time), style = MaterialTheme.typography.bodySmall)
            }
            if (t.note.isNotBlank()) Text(t.note)
            if (t.tagList().isNotEmpty()) {
                Text(
                    t.tagList().joinToString("  ") { "#" + it.replace(' ', '_') },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!t.reviewed) {
                Text("نیازمند توضیح و تگ", color = Color(0xFFEF6C00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun Report(list: List<Txn>, toman: Boolean) {
    val start = remember { monthStart() }
    val month = list.filter { it.time >= start }
    val income = month.filter { it.isIn() }.sumOf { it.amount }
    val expense = month.filter { !it.isIn() }.sumOf { it.amount }
    val byTag = month.filter { !it.isIn() }
        .flatMap { t -> t.tagList().ifEmpty { listOf("بدون تگ") }.map { it to t.amount } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ماه جاری", style = MaterialTheme.typography.titleMedium)
                    Text("درآمد: " + formatMoney(income, toman), color = Color(0xFF2E7D32))
                    Text("هزینه: " + formatMoney(expense, toman), color = Color(0xFFC62828))
                    Text(
                        "تراز: " + formatMoney(income - expense, toman),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item { Text("هزینه‌ها بر اساس تگ", style = MaterialTheme.typography.titleSmall) }
        items(byTag) { (tag, sum) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tag)
                    Text(formatMoney(sum, toman))
                }
                LinearProgressIndicator(
                    progress = { (sum.toFloat() / expense.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Text(
                "تراکنشی که چند تگ دارد، در هر تگ جداگانه حساب می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SettingsTab(tick: Int, toman: Boolean, onToman: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var popup by remember { mutableStateOf(Prefs.popup(ctx)) }
    var permTick by remember { mutableIntStateOf(0) }
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

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("مجوزها", style = MaterialTheme.typography.titleMedium)
        PermRow("دریافت پیامک", smsOk) {
            launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
        }
        PermRow("اعلان‌ها", notifOk) {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        PermRow("نمایش پاپ‌آپ روی سایر برنامه‌ها", overlayOk) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        OutlinedButton(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            )
        }) { Text("باز کردن تنظیمات برنامه") }
        Text(
            "اگر اندروید گزینه‌ی پیامک را خاکستری/مسدود کرد (برنامه‌ی نصب‌شده خارج از فروشگاه): " +
                "تنظیمات برنامه ← منوی سه‌نقطه ← «Allow restricted settings» و سپس دوباره مجوز را بدهید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        HorizontalDivider()
        Text("تنظیمات", style = MaterialTheme.typography.titleMedium)
        SwitchRow("نمایش پاپ‌آپ هنگام دریافت پیامک", popup) { popup = it; Prefs.setPopup(ctx, it) }
        SwitchRow("نمایش مبالغ به تومان (خاموش = ریال)", toman) { Prefs.setToman(ctx, it); onToman(it) }

        HorizontalDivider()
        Text("آزمایش", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
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
        }) { Text("شبیه‌سازی یک پیامک بانکی") }
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
        if (ok) Text("فعال ✓", color = Color(0xFF2E7D32))
        else FilledTonalButton(onClick = onGrant) { Text("فعال‌سازی") }
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

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toman = remember { Prefs.toman(ctx) }
    var amount by remember { mutableStateOf("") }
    var isIn by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تراکنش دستی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isIn, onClick = { isIn = false }, label = { Text("برداشت") })
                    FilterChip(selected = isIn, onClick = { isIn = true }, label = { Text("واریز") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = SmsParser.normalize(it).filter { c -> c.isDigit() } },
                    label = { Text(if (toman) "مبلغ (تومان)" else "مبلغ (ریال)") },
                    singleLine = true,
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
