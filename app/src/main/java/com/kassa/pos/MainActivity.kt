package com.kassa.pos

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var db: KassaDb
    private lateinit var body: LinearLayout
    private lateinit var tabBar: LinearLayout
    private val money = NumberFormat.getNumberInstance(Locale("ru", "KZ"))
    private var selectedPayment = "CASH"
    private var selectedSaleTime = System.currentTimeMillis()

    private val bg = Color.parseColor("#F5F7FB")
    private val ink = Color.parseColor("#111827")
    private val muted = Color.parseColor("#6B7280")
    private val blue = Color.parseColor("#2563EB")
    private val green = Color.parseColor("#059669")
    private val red = Color.parseColor("#DC2626")
    private val line = Color.parseColor("#E5E7EB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = KassaDb(this)
        buildShell()
        showCashier()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        top.addView(text("КАССА", 28f, true, ink))
        top.addView(text("TUMAR HOSTEL • офлайн", 13f, false, muted).apply { setPadding(0, dp(3), 0, 0) })

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(22))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }

        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(7), dp(8), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        addTab("Касса") { showCashier() }
        addTab("История") { showHistory() }
        addTab("Отчёт") { showReport() }

        root.addView(top)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(tabBar)
        setContentView(root)
    }

    private fun addTab(title: String, click: () -> Unit) {
        tabBar.addView(Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 13f
            setTextColor(ink)
            background = rounded(Color.parseColor("#F3F4F6"), 14f)
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
    }

    private fun showCashier() {
        selectedSaleTime = System.currentTimeMillis()
        selectedPayment = "CASH"
        val shift = shiftRange()
        val sum = db.summary(shift.first, shift.second)

        clearBody("Новая продажа", "Смена ${dateTime(shift.first)} → ${dateTime(shift.second)}")
        body.addView(summaryCard(sum))
        body.addView(space(14))

        val amount = EditText(this).apply {
            hint = "0 ₸"
            textSize = 34f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(ink)
            setHintTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = rounded(Color.WHITE, 20f, line)
        }
        body.addView(amount)
        body.addView(space(10))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(5000, 10000, 15000, 20000).forEach { value ->
            quick.addView(Button(this).apply {
                text = money.format(value)
                isAllCaps = false
                textSize = 12f
                setTextColor(ink)
                background = rounded(Color.WHITE, 14f, line)
                setOnClickListener { amount.setText(value.toString()) }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        body.addView(quick)
        body.addView(space(12))

        val comment = EditText(this).apply {
            hint = "Комментарий (необязательно)"
            textSize = 15f
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 16f, line)
        }
        body.addView(comment)
        body.addView(space(12))

        body.addView(text("Оплата", 15f, true, ink))
        body.addView(space(7))
        val paymentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cashBtn = selectButton("Наличные", true)
        val bankBtn = selectButton("Банк", false)
        cashBtn.setOnClickListener {
            selectedPayment = "CASH"
            cashBtn.background = rounded(green, 16f)
            cashBtn.setTextColor(Color.WHITE)
            bankBtn.background = rounded(Color.WHITE, 16f, line)
            bankBtn.setTextColor(ink)
        }
        bankBtn.setOnClickListener {
            selectedPayment = "BANK"
            bankBtn.background = rounded(blue, 16f)
            bankBtn.setTextColor(Color.WHITE)
            cashBtn.background = rounded(Color.WHITE, 16f, line)
            cashBtn.setTextColor(ink)
        }
        paymentRow.addView(cashBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(0, 0, dp(5), 0) })
        paymentRow.addView(bankBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(5), 0, 0, 0) })
        body.addView(paymentRow)
        body.addView(space(12))

        body.addView(text("Дата и время продажи", 15f, true, ink))
        body.addView(space(7))
        val timeBtn = Button(this).apply {
            text = dateTime(selectedSaleTime)
            isAllCaps = false
            textSize = 15f
            setTextColor(ink)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.WHITE, 16f, line)
            setOnClickListener { chooseDateTime(this) }
        }
        body.addView(timeBtn, LinearLayout.LayoutParams(-1, dp(52)))
        body.addView(space(16))

        body.addView(Button(this).apply {
            text = "ПОДТВЕРДИТЬ ПРОДАЖУ"
            isAllCaps = false
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(ink, 18f)
            setOnClickListener {
                val value = amount.text.toString().replace(',', '.').toDoubleOrNull()
                if (value == null || value <= 0) {
                    Toast.makeText(this@MainActivity, "Введите сумму продажи", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Подтвердить продажу?")
                    .setMessage("${cash(value)} ₸ • ${paymentName(selectedPayment)}\n${dateTime(selectedSaleTime)}")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Подтвердить") { _, _ ->
                        val id = db.addSale(comment.text.toString().trim(), value, selectedPayment, selectedSaleTime)
                        val sale = db.sale(id)
                        Toast.makeText(this@MainActivity, "Продажа сохранена", Toast.LENGTH_SHORT).show()
                        if (sale != null) showReceipt(sale)
                        showCashier()
                    }.show()
            }
        }, LinearLayout.LayoutParams(-1, dp(58)))

        body.addView(space(20))
        body.addView(text("Последние продажи", 18f, true, ink))
        body.addView(space(5))
        val recent = db.sales(shift.first, shift.second, 6)
        if (recent.isEmpty()) body.addView(emptyState("В этой смене продаж пока нет"))
        recent.forEach { body.addView(saleCard(it, showDelete = false)) }
    }

    private fun showHistory() {
        clearBody("История", "Нажмите «Чек» для просмотра или удалите ошибочную продажу")
        val all = db.sales(0, Long.MAX_VALUE, 500)
        if (all.isEmpty()) {
            body.addView(emptyState("История пока пустая"))
            return
        }
        all.forEach { body.addView(saleCard(it, showDelete = true)) }
    }

    private fun showReport() {
        val shift = shiftRange()
        val sum = db.summary(shift.first, shift.second)
        val sales = db.sales(shift.first, shift.second, 1000).reversed()

        clearBody("Отчёт за смену", "Полный период: ${dateTime(shift.first)} → ${dateTime(shift.second)}")
        body.addView(summaryCard(sum))
        body.addView(space(12))

        body.addView(Button(this).apply {
            text = "СОЗДАТЬ PDF-ОТЧЁТ"
            isAllCaps = false
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(blue, 18f)
            setOnClickListener {
                if (sales.isEmpty()) {
                    Toast.makeText(this@MainActivity, "В смене нет продаж", Toast.LENGTH_SHORT).show()
                } else {
                    val file = createShiftPdf(shift.first, shift.second, sales, sum)
                    if (file != null) showPdfReady(file)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(58)))

        body.addView(space(18))
        body.addView(text("Продажи этой смены", 18f, true, ink))
        if (sales.isEmpty()) body.addView(emptyState("В смене нет продаж"))
        sales.forEach { body.addView(saleCard(it, showDelete = false)) }
    }

    private fun chooseDateTime(button: Button) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedSaleTime }
        DatePickerDialog(this, { _, year, month, day ->
            val afterDate = Calendar.getInstance().apply {
                timeInMillis = selectedSaleTime
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(this, { _, hour, minute ->
                afterDate.set(Calendar.HOUR_OF_DAY, hour)
                afterDate.set(Calendar.MINUTE, minute)
                afterDate.set(Calendar.SECOND, 0)
                afterDate.set(Calendar.MILLISECOND, 0)
                selectedSaleTime = afterDate.timeInMillis
                button.text = dateTime(selectedSaleTime)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saleCard(sale: Sale, showDelete: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(Color.WHITE, 18f, line)
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
        box.layoutParams = lp

        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        first.addView(text(cash(sale.amount) + " ₸", 19f, true, ink), LinearLayout.LayoutParams(0, -2, 1f))
        first.addView(text(paymentName(sale.payment), 13f, true, if (sale.payment == "CASH") green else blue))
        box.addView(first)
        box.addView(text(dateTime(sale.ts), 13f, false, muted).apply { setPadding(0, dp(3), 0, 0) })
        if (sale.comment.isNotBlank()) box.addView(text(sale.comment, 14f, false, ink).apply { setPadding(0, dp(5), 0, 0) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Чек"
            isAllCaps = false
            textSize = 12f
            setTextColor(ink)
            background = rounded(Color.parseColor("#F3F4F6"), 12f)
            setOnClickListener { showReceipt(sale) }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0, dp(9), if (showDelete) dp(5) else 0, 0) })

        if (showDelete) {
            actions.addView(Button(this).apply {
                text = "Удалить"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.WHITE)
                background = rounded(red, 12f)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Удалить продажу?")
                        .setMessage("${cash(sale.amount)} ₸ • ${dateTime(sale.ts)}\nЭто действие нельзя отменить.")
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton("Удалить") { _, _ ->
                            db.deleteSale(sale.id)
                            showHistory()
                        }.show()
                }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(5), dp(9), 0, 0) })
        }
        box.addView(actions)
        return box
    }

    private fun showReceipt(sale: Sale) {
        val receipt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundColor(Color.WHITE)
        }
        receipt.addView(text("КАССА", 22f, true, ink).apply { gravity = Gravity.CENTER })
        receipt.addView(text("TUMAR HOSTEL", 14f, true, muted).apply { gravity = Gravity.CENTER })
        receipt.addView(text("────────────────────", 14f, false, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) })
        receipt.addView(text("ЧЕК №${sale.id}", 15f, true, ink).apply { gravity = Gravity.CENTER })
        receipt.addView(text(dateTime(sale.ts), 14f, false, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(12)) })
        receipt.addView(receiptLine("Сумма", cash(sale.amount) + " ₸"))
        receipt.addView(receiptLine("Оплата", paymentName(sale.payment)))
        if (sale.comment.isNotBlank()) receipt.addView(receiptLine("Комментарий", sale.comment))
        receipt.addView(text("────────────────────", 14f, false, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(8)) })
        receipt.addView(text("Спасибо", 13f, false, muted).apply { gravity = Gravity.CENTER })

        AlertDialog.Builder(this)
            .setView(receipt)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun receiptLine(left: String, right: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(text(left, 14f, false, muted), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(right, 14f, true, ink))
        return row
    }

    private fun createShiftPdf(from: Long, to: Long, sales: List<Sale>, sum: Summary): File? {
        return try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "KASSA")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "KASSA_${fileDate(from)}_${fileDate(to)}.pdf")
            val doc = PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val width = 595
            val height = 842
            var pageNo = 1
            var index = 0

            while (index < sales.size || pageNo == 1) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
                val canvas = page.canvas
                var y = 48f

                paint.color = ink
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 22f
                canvas.drawText("КАССА — TUMAR HOSTEL", 36f, y, paint)
                y += 28f
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 12f
                canvas.drawText("Отчёт за смену: ${dateTime(from)} → ${dateTime(to)}", 36f, y, paint)
                y += 24f

                if (pageNo == 1) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 13f
                    canvas.drawText("Продаж: ${sum.count}", 36f, y, paint)
                    canvas.drawText("Итого: ${cash(sum.total)} ₸", 180f, y, paint)
                    y += 20f
                    canvas.drawText("Наличные: ${cash(sum.cash)} ₸", 36f, y, paint)
                    canvas.drawText("Банк: ${cash(sum.bank)} ₸", 180f, y, paint)
                    y += 28f
                }

                paint.color = Color.parseColor("#D1D5DB")
                canvas.drawLine(36f, y, 559f, y, paint)
                y += 20f
                paint.color = ink
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 11f
                canvas.drawText("Время", 36f, y, paint)
                canvas.drawText("Оплата", 150f, y, paint)
                canvas.drawText("Комментарий", 250f, y, paint)
                canvas.drawText("Сумма", 485f, y, paint)
                y += 16f
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 10f

                while (index < sales.size && y < 790f) {
                    val s = sales[index]
                    canvas.drawText(dateTime(s.ts), 36f, y, paint)
                    canvas.drawText(paymentName(s.payment), 150f, y, paint)
                    canvas.drawText(shortText(s.comment.ifBlank { "—" }, 30), 250f, y, paint)
                    canvas.drawText(cash(s.amount) + " ₸", 485f, y, paint)
                    y += 16f
                    index++
                }

                paint.color = muted
                paint.textSize = 9f
                canvas.drawText("Страница $pageNo", 500f, 820f, paint)
                doc.finishPage(page)
                pageNo++
                if (sales.isEmpty()) break
            }

            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось создать PDF: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun showPdfReady(file: File) {
        AlertDialog.Builder(this)
            .setTitle("PDF готов")
            .setMessage("Отчёт сохранён:\n${file.absolutePath}")
            .setNegativeButton("Закрыть", null)
            .setPositiveButton("Поделиться") { _, _ -> sharePdf(file) }
            .show()
    }

    private fun sharePdf(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Отправить PDF-отчёт"))
    }

    private fun summaryCard(sum: Summary): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.WHITE, 20f, line)
        }
        box.addView(text("Текущая смена", 14f, true, muted))
        box.addView(text(cash(sum.total) + " ₸", 30f, true, ink).apply { setPadding(0, dp(3), 0, dp(9)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metric("Продаж", sum.count.toString()), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(metric("Наличные", cash(sum.cash) + " ₸"), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(metric("Банк", cash(sum.bank) + " ₸"), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)
        return box
    }

    private fun metric(label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(text(label, 11f, false, muted))
        addView(text(value, 13f, true, ink).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun selectButton(label: String, selected: Boolean): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        background = if (selected) rounded(green, 16f) else rounded(Color.WHITE, 16f, line)
        setTextColor(if (selected) Color.WHITE else ink)
    }

    private fun clearBody(title: String, subtitle: String) {
        body.removeAllViews()
        body.addView(text(title, 24f, true, ink))
        body.addView(text(subtitle, 13f, false, muted).apply { setPadding(0, dp(4), 0, dp(12)) })
    }

    private fun shiftRange(): Pair<Long, Long> {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (now.before(this)) add(Calendar.DAY_OF_YEAR, -1)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun paymentName(code: String) = if (code == "CASH") "Наличные" else "Банк"
    private fun cash(value: Double) = money.format(value)
    private fun dateTime(ts: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "KZ")).format(Date(ts))
    private fun fileDate(ts: Long) = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(ts))
    private fun shortText(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun space(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun emptyState(message: String) = text(message, 14f, false, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(30)) }
    private fun text(value: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
}

data class Sale(val id: Long, val comment: String, val amount: Double, val payment: String, val ts: Long)
data class Summary(val count: Int, val total: Double, val cash: Double, val bank: Double)

class KassaDb(context: Context) : SQLiteOpenHelper(context, "kassa.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT, comment TEXT NOT NULL DEFAULT '', amount REAL NOT NULL, payment TEXT NOT NULL, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            var hasTitle = false
            var hasComment = false
            db.rawQuery("PRAGMA table_info(sales)", null).use { c ->
                while (c.moveToNext()) {
                    when (c.getString(1)) {
                        "title" -> hasTitle = true
                        "comment" -> hasComment = true
                    }
                }
            }
            if (!hasComment) {
                db.execSQL("CREATE TABLE sales_new(id INTEGER PRIMARY KEY AUTOINCREMENT, comment TEXT NOT NULL DEFAULT '', amount REAL NOT NULL, payment TEXT NOT NULL, ts INTEGER NOT NULL)")
                if (hasTitle) {
                    db.execSQL("INSERT INTO sales_new(id,comment,amount,payment,ts) SELECT id,title,amount,payment,ts FROM sales")
                } else {
                    db.execSQL("INSERT INTO sales_new(id,comment,amount,payment,ts) SELECT id,'',amount,payment,ts FROM sales")
                }
                db.execSQL("DROP TABLE sales")
                db.execSQL("ALTER TABLE sales_new RENAME TO sales")
            }
        }
    }

    fun addSale(comment: String, amount: Double, payment: String, ts: Long): Long {
        return writableDatabase.insert("sales", null, ContentValues().apply {
            put("comment", comment)
            put("amount", amount)
            put("payment", payment)
            put("ts", ts)
        })
    }

    fun deleteSale(id: Long) {
        writableDatabase.delete("sales", "id=?", arrayOf(id.toString()))
    }

    fun sale(id: Long): Sale? {
        readableDatabase.rawQuery(
            "SELECT id,comment,amount,payment,ts FROM sales WHERE id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { c ->
            return if (c.moveToFirst()) Sale(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3), c.getLong(4)) else null
        }
    }

    fun sales(from: Long, to: Long, limit: Int): List<Sale> {
        val out = mutableListOf<Sale>()
        readableDatabase.rawQuery(
            "SELECT id,comment,amount,payment,ts FROM sales WHERE ts>=? AND ts<? ORDER BY ts DESC LIMIT ?",
            arrayOf(from.toString(), to.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += Sale(c.getLong(0), c.getString(1), c.getDouble(2), c.getString(3), c.getLong(4))
        }
        return out
    }

    fun summary(from: Long, to: Long): Summary {
        var count = 0
        var total = 0.0
        var cash = 0.0
        var bank = 0.0
        readableDatabase.rawQuery(
            "SELECT payment,amount FROM sales WHERE ts>=? AND ts<?",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                count++
                val payment = c.getString(0)
                val amount = c.getDouble(1)
                total += amount
                if (payment == "CASH") cash += amount else bank += amount
            }
        }
        return Summary(count, total, cash, bank)
    }
}
