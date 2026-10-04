@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.kaveh.fintag

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF4DB6AC))
        else lightColorScheme(primary = Color(0xFF00695C))
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}

/** فرم افزودن توضیح و تگ — هم در پاپ‌آپ روی صفحه، هم در اپ و هم در Activity اعلان استفاده می‌شود */
@Composable
fun TxnEditor(txnId: Long, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val allTags by dao.observeTags().collectAsState(initial = emptyList())
    var txn by remember { mutableStateOf<Txn?>(null) }
    var note by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var newTag by remember { mutableStateOf("") }
    val toman = remember { Prefs.toman(ctx) }

    LaunchedEffect(txnId) {
        dao.getTxn(txnId)?.let {
            txn = it
            note = it.note
            selected = it.tagList().toSet()
        }
    }

    fun addNew() {
        val n = newTag.replace("|", " ").trim().take(24)
        if (n.isNotEmpty()) selected = selected + n
        newTag = ""
    }

    val t = txn
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
        if (t == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val color = if (t.isIn()) Color(0xFF2E7D32) else Color(0xFFC62828)
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (t.isIn()) "واریز" else "برداشت",
                        color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatMoney(t.amount, toman),
                        color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    buildString {
                        append(formatDate(t.time))
                        t.balance?.let {
                            append("  •  مانده: ")
                            append(formatMoney(it, toman))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات (مثلاً: خرید نان)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("تگ‌ها", style = MaterialTheme.typography.labelLarge)
                val names = allTags.map { it.name } + selected.filter { s -> allTags.none { it.name == s } }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    names.forEach { n ->
                        FilterChip(
                            selected = n in selected,
                            onClick = { selected = if (n in selected) selected - n else selected + n },
                            label = { Text(n) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("تگ جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = { addNew() }) { Text("افزودن") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onFinish) { Text("بعداً") }
                    Button(onClick = {
                        scope.launch {
                            val before = t.tagList().toSet()
                            val tags = selected.map { it.trim() }.filter { it.isNotEmpty() }
                            tags.forEach {
                                dao.insertTag(Tag(it))
                                if (it !in before) dao.bumpTag(it)
                            }
                            dao.updateTxn(t.copy(note = note.trim(), tags = tags.joinToString("|"), reviewed = true))
                            Notifier.cancel(ctx, t.id)
                            onFinish()
                        }
                    }) { Text("ذخیره") }
                }
            }
        }
    }
}
