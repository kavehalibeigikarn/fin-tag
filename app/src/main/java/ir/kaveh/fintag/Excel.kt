package ir.kaveh.fintag

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.sync.withLock
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** نوشتن و خواندن ساده‌ی فایل xlsx بدون کتابخانه‌ی اضافه */
object Xlsx {
    class Sheet(val name: String, val rows: List<List<Any?>>)

    private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.append('A' + r)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch < ' ' && ch != '\n' && ch != '\t' && ch != '\r' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun write(out: OutputStream, sheets: List<Sheet>) {
        val zip = ZipOutputStream(out)
        fun put(name: String, text: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val ct = StringBuilder(XML_HEAD)
        ct.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        ct.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        ct.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        ct.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sheets.forEachIndexed { i, _ ->
            ct.append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        ct.append("</Types>")
        put("[Content_Types].xml", ct.toString())

        put(
            "_rels/.rels",
            XML_HEAD + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        )

        val wb = StringBuilder(XML_HEAD)
        wb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s ->
            wb.append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        wb.append("</sheets></workbook>")
        put("xl/workbook.xml", wb.toString())

        val rels = StringBuilder(XML_HEAD)
        rels.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.forEachIndexed { i, _ ->
            rels.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        rels.append("</Relationships>")
        put("xl/_rels/workbook.xml.rels", rels.toString())

        sheets.forEachIndexed { si, sheet ->
            val sb = StringBuilder(XML_HEAD)
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><cols><col min="1" max="12" width="20" customWidth="1"/></cols><sheetData>""")
            sheet.rows.forEachIndexed { r, row ->
                sb.append("""<row r="${r + 1}">""")
                row.forEachIndexed { c, v ->
                    if (v != null) {
                        val ref = colName(c) + (r + 1)
                        if (v is Number) {
                            sb.append("""<c r="$ref"><v>$v</v></c>""")
                        } else {
                            sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">""")
                            sb.append(esc(v.toString()))
                            sb.append("</t></is></c>")
                        }
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet${si + 1}.xml", sb.toString())
        }
        zip.finish()
        zip.flush()
    }

    // ───────── خواندن ─────────
    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) {
            if (p.getAttributeName(i).substringAfter(':') == name) return p.getAttributeValue(i)
        }
        return null
    }

    private fun colIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return -1
        return letters.fold(0) { a, ch -> a * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val list = ArrayList<String>()
        var sb: StringBuilder? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if (p.name == "si") sb = StringBuilder()
                else if (p.name == "t") sb?.append(p.nextText())
            } else if (ev == XmlPullParser.END_TAG && p.name == "si") {
                list.add(sb?.toString() ?: "")
                sb = null
            }
            ev = p.next()
        }
        return list
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes)
        val rows = ArrayList<List<String>>()
        var cur: MutableList<String>? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                if (p.name == "row") {
                    cur = ArrayList()
                } else if (p.name == "c") {
                    val ref = attr(p, "r") ?: ""
                    val type = attr(p, "t") ?: ""
                    val depth = p.depth
                    var value = ""
                    while (true) {
                        val e2 = p.next()
                        if (e2 == XmlPullParser.END_DOCUMENT) break
                        if (e2 == XmlPullParser.END_TAG && p.depth == depth) break
                        if (e2 == XmlPullParser.START_TAG && p.name == "v") value = p.nextText()
                        else if (e2 == XmlPullParser.START_TAG && p.name == "t") value += p.nextText()
                    }
                    if (type == "s") value = shared.getOrNull(value.trim().toIntOrNull() ?: -1) ?: ""
                    val row = cur
                    if (row != null) {
                        var col = colIndex(ref)
                        if (col < 0) col = row.size
                        while (row.size <= col) row.add("")
                        row[col] = value
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "row") {
                cur?.let { rows.add(it) }
                cur = null
            }
            ev = p.next()
        }
        return rows
    }

    /** خروجی: لیست (نام شیت، ردیف‌ها) */
    fun read(input: InputStream): List<Pair<String, List<List<String>>>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) files[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        val wb = files["xl/workbook.xml"] ?: throw IllegalArgumentException("فایل اکسل معتبر نیست")
        val relBytes = files["xl/_rels/workbook.xml.rels"]
        val targets = HashMap<String, String>()
        if (relBytes != null) {
            val rp = parser(relBytes)
            var ev = rp.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && rp.name == "Relationship") {
                    val id = attr(rp, "Id")
                    val tg = attr(rp, "Target")
                    if (id != null && tg != null) targets[id] = tg
                }
                ev = rp.next()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()

        val result = ArrayList<Pair<String, List<List<String>>>>()
        val wp = parser(wb)
        var ev = wp.eventType
        var idx = 0
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && wp.name == "sheet") {
                idx++
                val name = attr(wp, "name") ?: "Sheet$idx"
                val rid = attr(wp, "id")
                val target = targets[rid ?: ""] ?: "worksheets/sheet$idx.xml"
                val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                val bytes = files[path]
                if (bytes != null) result.add(name to parseSheet(bytes, shared))
            }
            ev = wp.next()
        }
        return result
    }
}

data class ImportResult(val added: Int, val duplicates: Int, val newAccounts: Int)

object Backup {
    private val TX_HEADERS = listOf(
        "حساب", "نوع", "مبلغ (ریال)", "مانده (ریال)", "تاریخ شمسی", "زمان (ms)",
        "توضیح", "تگ‌ها", "فرستنده", "متن پیامک", "بررسی‌شده"
    )
    private val ACC_HEADERS = listOf("نام حساب", "فرستنده‌ها", "شناسه تشخیص", "موجودی اول دوره (ریال)", "شروع دوره (ms)")
    private val TAG_HEADERS = listOf("تگ", "تعداد استفاده")

    /** accountId = 0 یعنی همه‌ی حساب‌ها */
    suspend fun export(ctx: Context, out: OutputStream, accountId: Long) {
        val dao = AppDb.get(ctx).dao()
        val accounts = dao.getAccounts().filter { accountId == 0L || it.id == accountId }
        val ids = accounts.map { it.id }.toSet()
        val txns = dao.getAllTxns().filter { it.accountId in ids }
        val nameById = accounts.associate { it.id to it.name }

        val txRows = ArrayList<List<Any?>>()
        txRows.add(TX_HEADERS)
        for (t in txns) {
            txRows.add(
                listOf(
                    nameById[t.accountId] ?: "",
                    if (t.isIn()) "واریز" else "برداشت",
                    t.amount,
                    t.balance,
                    formatDate(t.time),
                    t.time,
                    t.note,
                    t.tagList().joinToString("، "),
                    t.sender,
                    t.rawText,
                    if (t.reviewed) "بله" else "خیر"
                )
            )
        }
        val accRows = ArrayList<List<Any?>>()
        accRows.add(ACC_HEADERS)
        for (a in accounts) accRows.add(listOf(a.name, a.senders, a.hint, a.openingBalance, a.openingTime))
        val tagRows = ArrayList<List<Any?>>()
        tagRows.add(TAG_HEADERS)
        for (g in dao.getAllTags()) tagRows.add(listOf(g.name, g.useCount))

        Xlsx.write(
            out,
            listOf(Xlsx.Sheet("تراکنش ها", txRows), Xlsx.Sheet("حساب ها", accRows), Xlsx.Sheet("تگ ها", tagRows))
        )
    }

    private fun num(s: String?): Long? {
        val t = SmsParser.normalize(s ?: "").trim().replace(",", "")
        if (t.isEmpty()) return null
        return t.toLongOrNull() ?: t.toDoubleOrNull()?.toLong()
    }

    private val jalaliRe = Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})(?:\D+(\d{1,2}):(\d{2}))?""")

    private fun parseJalali(s: String): Long? {
        val m = jalaliRe.find(SmsParser.normalize(s)) ?: return null
        val g = m.groupValues
        return jalaliToMillis(
            g[1].toInt(), g[2].toInt(), g[3].toInt(),
            g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0
        )
    }

    suspend fun import(ctx: Context, input: InputStream): ImportResult {
        val sheets = Xlsx.read(input)
        val txSheet = sheets.firstOrNull { it.second.firstOrNull()?.contains("مبلغ (ریال)") == true }?.second
            ?: throw IllegalArgumentException("این فایل، خروجی این برنامه نیست (ستون «مبلغ (ریال)» پیدا نشد)")
        val accSheet = sheets.firstOrNull { it.second.firstOrNull()?.contains("نام حساب") == true }?.second
        val tagSheet = sheets.firstOrNull { it.second.firstOrNull()?.contains("تعداد استفاده") == true }?.second

        val dao = AppDb.get(ctx).dao()
        var added = 0
        var dup = 0
        var newAcc = 0

        Accounts.lock.withLock {
            val accounts = dao.getAccounts().toMutableList()

            // حساب‌ها
            if (accSheet != null) {
                for (r in accSheet.drop(1)) {
                    val name = (r.getOrNull(0) ?: "").trim()
                    if (name.isEmpty()) continue
                    if (accounts.any { it.name.trim().equals(name, true) }) continue
                    val a = Account(
                        name = name,
                        senders = (r.getOrNull(1) ?: "").trim(),
                        hint = (r.getOrNull(2) ?: "").trim(),
                        openingBalance = num(r.getOrNull(3)) ?: 0L,
                        openingTime = num(r.getOrNull(4)) ?: 0L
                    )
                    val id = dao.insertAccount(a)
                    accounts.add(a.copy(id = id))
                    newAcc++
                }
            }

            // تگ‌ها
            if (tagSheet != null) {
                for (r in tagSheet.drop(1)) {
                    val n = (r.getOrNull(0) ?: "").trim()
                    if (n.isNotEmpty()) dao.insertTag(Tag(n, (num(r.getOrNull(1)) ?: 0L).toInt()))
                }
            }

            // تراکنش‌ها
            val head = txSheet[0]
            val iAcc = head.indexOf("حساب")
            val iType = head.indexOf("نوع")
            val iAmt = head.indexOf("مبلغ (ریال)")
            val iBal = head.indexOf("مانده (ریال)")
            val iDate = head.indexOf("تاریخ شمسی")
            val iMs = head.indexOf("زمان (ms)")
            val iNote = head.indexOf("توضیح")
            val iTags = head.indexOf("تگ‌ها")
            val iSender = head.indexOf("فرستنده")
            val iRaw = head.indexOf("متن پیامک")
            val iRev = head.indexOf("بررسی‌شده")

            val seen = HashSet<String>()
            for (t in dao.getAllTxns()) seen.add("${t.accountId}|${t.time}|${t.amount}|${t.type}")

            for (r in txSheet.drop(1)) {
                val typeText = (r.getOrNull(iType) ?: "").trim()
                val amount = num(r.getOrNull(iAmt))
                if (typeText.isEmpty() || amount == null || amount <= 0) continue
                val isIn = typeText.contains("واریز") || typeText.equals("IN", true) || typeText.contains("deposit", true)

                val accName = (r.getOrNull(iAcc) ?: "").trim().ifEmpty { "حساب اصلی" }
                var acc = accounts.firstOrNull { it.name.trim().equals(accName, true) }
                if (acc == null) {
                    val a = Account(name = accName)
                    val id = dao.insertAccount(a)
                    acc = a.copy(id = id)
                    accounts.add(acc)
                    newAcc++
                }

                val time = num(r.getOrNull(iMs))?.takeIf { it > 0 }
                    ?: parseJalali(r.getOrNull(iDate) ?: "")
                    ?: System.currentTimeMillis()

                val type = if (isIn) "IN" else "OUT"
                val key = "${acc.id}|$time|$amount|$type"
                if (!seen.add(key)) {
                    dup++
                    continue
                }

                val note = (r.getOrNull(iNote) ?: "").trim()
                val tags = (r.getOrNull(iTags) ?: "").split("،", ",", "|").map { it.trim() }.filter { it.isNotEmpty() }
                for (tg in tags) dao.insertTag(Tag(tg))
                val revText = (r.getOrNull(iRev) ?: "").trim()
                val reviewed = if (iRev >= 0 && revText.isNotEmpty()) {
                    revText == "بله" || revText.equals("true", true) || revText == "1" || revText.equals("yes", true)
                } else {
                    note.isNotEmpty() || tags.isNotEmpty()
                }

                dao.insertTxn(
                    Txn(
                        type = type,
                        amount = amount,
                        balance = num(r.getOrNull(iBal)),
                        note = note,
                        tags = tags.joinToString("|"),
                        sender = (r.getOrNull(iSender) ?: "").trim(),
                        rawText = r.getOrNull(iRaw) ?: "",
                        time = time,
                        reviewed = reviewed,
                        accountId = acc.id
                    )
                )
                added++
            }
        }
        return ImportResult(added, dup, newAcc)
    }
}
