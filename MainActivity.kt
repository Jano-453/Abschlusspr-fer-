package de.rsho.daftarhisabati

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

data class Entry(val id: Long, val kind: String, val category: String, val amount: Double, val date: String, val note: String)

class LedgerDb(context: android.content.Context) : SQLiteOpenHelper(context, "ledger.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL, category TEXT NOT NULL,
            amount REAL NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO settings(key,value) VALUES('opening_balance','0')")
        db.execSQL("INSERT INTO settings(key,value) VALUES('language','ar')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(kind: String, category: String, amount: Double, date: String, note: String) {
        val cv = ContentValues().apply {
            put("kind", kind); put("category", category); put("amount", amount); put("date", date); put("note", note)
        }
        writableDatabase.insert("entries", null, cv)
    }
    fun all(): List<Entry> {
        val result = mutableListOf<Entry>()
        readableDatabase.rawQuery("SELECT id,kind,category,amount,date,note FROM entries ORDER BY date DESC,id DESC", null).use { c ->
            while (c.moveToNext()) result.add(Entry(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4), c.getString(5)))
        }
        return result
    }
    fun delete(id: Long) { writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) }
    fun opening(): Double = readableDatabase.rawQuery("SELECT value FROM settings WHERE key='opening_balance'", null).use { if (it.moveToFirst()) it.getString(0).toDoubleOrNull() ?: 0.0 else 0.0 }
    fun setOpening(v: Double) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES('opening_balance',?)", arrayOf(v.toString()))
    }
    fun language(): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key='language'", null).use {
        if (it.moveToFirst()) it.getString(0) else "ar"
    }
    fun setLanguage(v: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES('language',?)", arrayOf(v))
    }
    fun totals(): Triple<Double, Double, Double> {
        var income = 0.0; var withdrawals = 0.0
        all().forEach {
            if (it.kind == "مدخول") income += it.amount else withdrawals += it.amount
        }
        return Triple(income, withdrawals, opening() + income - withdrawals)
    }
}

class MainActivity : Activity() {
    private lateinit var db: LedgerDb
    private lateinit var root: LinearLayout
    private val green = Color.rgb(28, 92, 62)
    private val pale = Color.rgb(239, 247, 241)
    private val red = Color.rgb(160, 55, 55)
    private val money = java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val exportRequest = 4021
    private val translations = mapOf(
        "ar" to mapOf(
            "app" to "دفتر حساباتي", "income_total" to "إجمالي المداخيل", "withdrawals" to "إجمالي المسحوبات",
            "balance" to "الرصيد الحالي", "add_income" to "＋ إضافة مدخول", "add_withdrawal" to "－ إضافة مسحوب",
            "closing" to "إقفال الحساب / الرصيد الافتتاحي", "export" to "تصدير نسخة Excel احتياطية (CSV)",
            "recent" to "آخر الحركات", "empty" to "لا توجد حركات بعد. اضغط «إضافة مدخول» أو «إضافة مسحوب» للبدء.",
            "add_income_title" to "إضافة مدخول", "add_withdrawal_title" to "إضافة مسحوب",
            "amount" to "المبلغ (€)", "type" to "التصنيف", "date" to "التاريخ", "notes" to "ملاحظات (اختياري)",
            "save" to "حفظ الحركة", "back" to "رجوع", "fixed" to "ثابت", "variable" to "متحرك",
            "salary" to "راتب", "sales" to "مبيعات", "transfer" to "تحويل", "other" to "أخرى",
            "opening" to "الرصيد الافتتاحي", "closing_balance" to "الرصيد الختامي الحالي",
            "save_opening" to "حفظ الرصيد الافتتاحي", "home" to "العودة للرئيسية",
            "settings" to "اللغة / Sprache", "language" to "اختر لغة التطبيق",
            "saved" to "تم حفظ الحركة", "updated" to "تم تحديث الحساب", "exported" to "تم تصدير النسخة الاحتياطية",
            "delete_title" to "حذف الحركة؟", "delete_message" to "هل تريد حذف", "delete" to "حذف", "cancel" to "إلغاء",
            "invalid_amount" to "أدخل مبلغاً صحيحاً أكبر من صفر", "invalid_number" to "أدخل رقماً صحيحاً",
            "date_prefix" to "التاريخ: "
        ),
        "de" to mapOf(
            "app" to "Mein Kassenbuch", "income_total" to "Gesamte Einnahmen", "withdrawals" to "Gesamte Ausgaben",
            "balance" to "Aktueller Kontostand", "add_income" to "＋ Einnahme hinzufügen", "add_withdrawal" to "－ Ausgabe hinzufügen",
            "closing" to "Abschluss / Anfangssaldo", "export" to "Excel-Sicherung exportieren (CSV)",
            "recent" to "Letzte Buchungen", "empty" to "Noch keine Buchungen. Fügen Sie eine Einnahme oder Ausgabe hinzu.",
            "add_income_title" to "Einnahme hinzufügen", "add_withdrawal_title" to "Ausgabe hinzufügen",
            "amount" to "Betrag (€)", "type" to "Kategorie", "date" to "Datum", "notes" to "Notiz (optional)",
            "save" to "Buchung speichern", "back" to "Zurück", "fixed" to "Fix", "variable" to "Variabel",
            "salary" to "Gehalt", "sales" to "Verkäufe", "transfer" to "Überweisung", "other" to "Sonstiges",
            "opening" to "Anfangssaldo", "closing_balance" to "Aktueller Endsaldo",
            "save_opening" to "Anfangssaldo speichern", "home" to "Zur Startseite",
            "settings" to "Sprache / اللغة", "language" to "App-Sprache wählen",
            "saved" to "Buchung gespeichert", "updated" to "Kontostand aktualisiert", "exported" to "Sicherung exportiert",
            "delete_title" to "Buchung löschen?", "delete_message" to "Soll dieser Betrag gelöscht werden:", "delete" to "Löschen", "cancel" to "Abbrechen",
            "invalid_amount" to "Bitte einen gültigen Betrag größer als null eingeben", "invalid_number" to "Bitte eine gültige Zahl eingeben",
            "date_prefix" to "Datum: "
        )
    )
    private fun tr(key: String): String = translations[db.language()]?.get(key) ?: translations["ar"]!![key] ?: key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LedgerDb(this)
        window.statusBarColor = green
        window.navigationBarColor = green
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        showHome()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 15f, color: Int = Color.rgb(35, 45, 39), bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
    private fun button(text: String, color: Int = green, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text; setTextColor(Color.WHITE); setBackgroundColor(color)
            isAllCaps = false; setOnClickListener { action() }
        }
    private fun input(hint: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            this.hint = hint; inputType = type; textSize = 16f
            gravity = Gravity.RIGHT; setSingleLine(true)
        }
    private fun format(v: Double) = money.format(v)

    private fun setup(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 250, 248))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = TextView(this).apply {
            text = title; textSize = 22f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15)); setBackgroundColor(green)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(62)))
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(18)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        currentContent = content
    }
    private lateinit var currentContent: LinearLayout
    private fun card(view: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        box.addView(view)
        val p = LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10)
        currentContent.addView(box, p)
    }
    private fun showHome() {
        setup(tr("app"))
        val (income, withdrawals, balance) = db.totals()
        val totals = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundColor(pale) }
        totals.addView(label("${tr("income_total")}: ${format(income)}", 16f, green, true))
        totals.addView(label("${tr("withdrawals")}: ${format(withdrawals)}", 16f, red, true))
        totals.addView(label("${tr("balance")}: ${format(balance)}", 21f, green, true))
        card(totals)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val addIncome = button(tr("add_income")) { showAdd("مدخول") }
        val addWithdrawal = button(tr("add_withdrawal"), red) { showAdd("مسحوب") }
        actions.addView(addIncome, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(5) })
        actions.addView(addWithdrawal, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(5) })
        card(actions)
        card(button(tr("closing")) { showClosing() })
        card(button(tr("export")) { exportCsv() })
        card(label(tr("recent"), 18f, green, true))
        card(button(tr("settings")) { showLanguage() })
        val entries = db.all()
        if (entries.isEmpty()) {
            card(label(tr("empty"), 15f))
        } else {
            entries.take(100).forEach { e ->
                val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(7), dp(8), dp(7)) }
                val signed = if (e.kind == "مدخول") "+" else "−"
                val color = if (e.kind == "مدخول") green else red
                line.addView(label("$signed${format(e.amount)}  •  ${e.category}", 16f, color, true))
                line.addView(label("${e.date}${if (e.note.isNotBlank()) "  •  ${e.note}" else ""}", 13f, Color.DKGRAY))
                line.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle("حذف الحركة؟").setMessage("هل تريد حذف ${format(e.amount)}؟")
                        .setNegativeButton("إلغاء", null).setPositiveButton("حذف") { _, _ -> db.delete(e.id); showHome() }.show()
                    true
                }
                card(line)
            }
            if (entries.size > 100) card(label("يتم عرض آخر 100 حركة. إجمالي الحركات المحفوظة: ${entries.size}"))
        }
    }


    private fun showLanguage() {
        setup(tr("language"))
        val options = arrayOf("العربية", "Deutsch")
        val codes = arrayOf("ar", "de")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.setSelection(if (db.language() == "de") 1 else 0)
        card(label(tr("language"), 17f, green, true))
        card(spinner)
        card(button(if (db.language() == "de") "Speichern" else "حفظ اللغة") {
            db.setLanguage(codes[spinner.selectedItemPosition])
            showHome()
        })
        card(button(tr("back"), Color.GRAY) { showHome() })
    }

    private fun showAdd(kind: String) {
        setup(if (kind == "مدخول") tr("add_income_title") else tr("add_withdrawal_title"))
        val amount = input(tr("amount"), android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val note = input(tr("notes"))
        var selectedDate = dateFormat.format(Date())
        lateinit var dateButton: Button
        dateButton = button("${tr("date_prefix")}$selectedDate", Color.DKGRAY) {
            val now = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                dateButton.text = "${tr("date_prefix")}$selectedDate"
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }
        val categoryOptions = if (kind == "مدخول") arrayOf(tr("salary"), tr("sales"), tr("transfer"), tr("other")) else arrayOf(tr("fixed"), tr("variable"))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryOptions)
        card(label(tr("amount")))
        card(amount)
        card(label(tr("type")))
        card(spinner)
        card(dateButton)
        card(label(tr("notes")))
        card(note)
        card(button(tr("save")) {
            val value = amount.text.toString().replace(',', '.').toDoubleOrNull()
            if (value == null || value <= 0) {
                amount.error = tr("invalid_amount")
            } else {
                db.add(kind, spinner.selectedItem.toString(), value, selectedDate, note.text.toString().trim())
                Toast.makeText(this, tr("saved"), Toast.LENGTH_SHORT).show()
                showHome()
            }
        })
        card(button(tr("back"), Color.GRAY) { showHome() })
    }

    private fun showClosing() {
        setup(tr("closing"))
        val (income, withdrawals, balance) = db.totals()
        card(label("${tr("income_total")}: ${format(income)}", 16f, green, true))
        card(label("${tr("withdrawals")}: ${format(withdrawals)}", 16f, red, true))
        card(label("${tr("closing_balance")}: ${format(balance)}", 21f, green, true))
        val opening = input("${tr("opening")} (€)", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        opening.setText(String.format(Locale.US, "%.2f", db.opening()))
        card(label(tr("opening"), 16f, green, true))
        card(opening)
        card(button(tr("save_opening")) {
            val v = opening.text.toString().replace(',', '.').toDoubleOrNull()
            if (v == null) opening.error = tr("invalid_number")
            else {
                db.setOpening(v)
                Toast.makeText(this, tr("updated"), Toast.LENGTH_SHORT).show()
                showHome()
            }
        })
        card(button(tr("home"), Color.GRAY) { showHome() })
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "دفتر_حساباتي_نسخة_احتياطية.csv")
        }
        startActivityForResult(intent, exportRequest)
    }

    @Deprecated("Deprecated in Android but supported for this minimal standalone app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == exportRequest && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        writer.write("\uFEFF")
                        writer.write("النوع,التصنيف,المبلغ,التاريخ,ملاحظات\n")
                        db.all().sortedBy { it.date }.forEach { e ->
                            writer.write(listOf(e.kind, e.category, String.format(Locale.US, "%.2f", e.amount), e.date, e.note)
                                .joinToString(",") { csv(it) } + "\n")
                        }
                        writer.write("الرصيد الافتتاحي,${String.format(Locale.US, "%.2f", db.opening())},,,\n")
                        val t = db.totals()
                        writer.write("إجمالي المداخيل,${String.format(Locale.US, "%.2f", t.first)},,,\n")
                        writer.write("إجمالي المسحوبات,${String.format(Locale.US, "%.2f", t.second)},,,\n")
                        writer.write("الرصيد الختامي,${String.format(Locale.US, "%.2f", t.third)},,,\n")
                    }
                }
                Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "تعذر التصدير: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
