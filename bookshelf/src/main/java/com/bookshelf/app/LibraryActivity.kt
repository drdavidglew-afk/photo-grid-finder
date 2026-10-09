package com.bookshelf.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** The reading list: search across every saved book, tick off the ones read, add more by author or title. */
class LibraryActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: Store

    private var all: List<Book> = emptyList()
    private var shown: List<Book> = emptyList()

    private lateinit var searchBox: EditText
    private lateinit var summary: TextView
    private lateinit var emptyView: TextView
    private lateinit var busy: View
    private lateinit var addButton: TextView
    private val adapter = BookAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.header))
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        header.addView(text("Bookshelf", 26f, R.color.on_accent, bold = true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        })
        summary = text("", 13f, R.color.on_accent).apply {
            setTextColor(0xCCFFFFFF.toInt())
        }
        header.addView(summary)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
        }
        searchBox = EditText(this).apply {
            hint = "Search by author or title"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(color(R.color.ink))
            setHintTextColor(color(R.color.muted))
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(R.color.paper, 12, R.color.line)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = applyFilter()
            })
        }
        controls.addView(searchBox, LinearLayout.LayoutParams(-1, dp(48)))
        addButton = primaryButton("Add books by author or title").apply { setOnClickListener { promptAdd() } }
        controls.addView(addButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))

        val listArea = FrameLayout(this)
        val list = ListView(this).apply {
            divider = null
            adapter = this@LibraryActivity.adapter
            clipToPadding = false
            setPadding(dp(16), dp(4), dp(16), dp(16))
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@LibraryActivity, BookActivity::class.java).putExtra("id", shown[pos].id))
            }
        }
        listArea.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyView = text("", 15f, R.color.muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
        }
        listArea.addView(emptyView, FrameLayout.LayoutParams(-1, -1))
        busy = ProgressBar(this).also {
            listArea.addView(it, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
            it.show(false)
        }
        root.addView(listArea, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun reload() {
        all = store.allBooks()
        applyFilter()
    }

    private fun applyFilter() {
        val q = searchBox.text.toString()
        shown = all.filter { matches(it, q) }
        adapter.notifyDataSetChanged()

        val unread = all.count { !it.read }
        summary.text = if (all.isEmpty()) "No books yet" else "$unread unread of ${all.size}"
        emptyView.text = when {
            all.isEmpty() -> "Add an author or a book title to build your list."
            shown.isEmpty() -> "No books match “${q.trim()}”."
            else -> ""
        }
        emptyView.show(shown.isEmpty())
    }

    private fun setBusy(on: Boolean) {
        busy.show(on)
        addButton.isEnabled = !on
        addButton.alpha = if (on) 0.5f else 1f
    }

    // --- adding books -------------------------------------------------------------------------

    private fun promptAdd() {
        val input = EditText(this).apply {
            hint = "e.g. Terry Pratchett or Small Gods"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val frame = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Add books")
            .setMessage("Enter an author’s name or a book title.")
            .setView(frame)
            .setPositiveButton("Search") { _, _ -> lookUp(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lookUp(query: String) {
        if (query.isEmpty()) return
        setBusy(true)
        executor.execute {
            try {
                val candidates = OpenLibrary.findAuthors(query)
                runOnUiThread {
                    setBusy(false)
                    when (candidates.size) {
                        0 -> toast("Nothing found for “$query”. Check the spelling?")
                        1 -> fetchBooks(candidates[0])
                        else -> chooseAuthor(query, candidates)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false); showError(e) }
            }
        }
    }

    /** The text matched more than one author (or a title with several), so ask which is meant. */
    private fun chooseAuthor(query: String, candidates: List<AuthorCandidate>) {
        val rowAdapter = object : BaseAdapter() {
            override fun getCount() = candidates.size
            override fun getItem(position: Int) = candidates[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val c = candidates[position]
                return LinearLayout(this@LibraryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                    addView(text(c.name, 16f, R.color.ink, bold = true))
                    addView(text(c.detail, 13f, R.color.muted))
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Which did you mean by “$query”?")
            .setAdapter(rowAdapter) { _, which -> fetchBooks(candidates[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchBooks(author: AuthorCandidate) {
        setBusy(true)
        executor.execute {
            try {
                val works = OpenLibrary.worksBy(author.key)
                val added = store.addBooks(author.name, works)
                runOnUiThread {
                    setBusy(false)
                    searchBox.setText(author.name)
                    reload()
                    toast(
                        when {
                            works.isEmpty() -> "No books found for ${author.name}."
                            added == 0 -> "All ${works.size} books by ${author.name} were already on your list."
                            else -> "Added $added ${if (added == 1) "book" else "books"} by ${author.name}."
                        }
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false); showError(e) }
            }
        }
    }

    private fun showError(e: Exception) =
        toast("Couldn’t reach the book database. Check your connection and try again.\n(${e.message})")

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // --- list ---------------------------------------------------------------------------------

    private inner class BookAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        private inner class Row(
            val box: CheckBox,
            val title: TextView,
            val sub: TextView,
            val comments: TextView,
        )

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: makeRow()
            val row = view.tag as Row
            val book = shown[position]

            row.title.text = book.title
            row.title.setTextColor(color(if (book.read) R.color.muted else R.color.ink))
            row.sub.text = listOfNotNull(book.author, book.year?.toString()).joinToString(" · ")
            row.comments.text = "💬 ${book.commentCount}"
            row.comments.show(book.commentCount > 0)
            row.box.isChecked = book.read
            row.box.contentDescription = if (book.read) "Read. Tap to mark unread." else "Unread. Tap to mark read."
            row.box.setOnClickListener {
                store.setRead(book.id, row.box.isChecked)
                reload()
            }
            return view
        }

        private fun makeRow(): View {
            val box = CheckBox(this@LibraryActivity).apply {
                isFocusable = false
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val title = text("", 16f, R.color.ink, bold = true)
            val sub = text("", 13f, R.color.muted)
            val comments = text("", 13f, R.color.muted)
            val col = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(sub)
            }
            val card = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(dp(8), dp(6), dp(14), dp(6))
                background = rounded(R.color.paper, 12, R.color.line)
                addView(box)
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                addView(comments)
            }
            // The wrapper gives the gap between cards.
            return FrameLayout(this@LibraryActivity).apply {
                setPadding(0, dp(4), 0, dp(4))
                addView(card, FrameLayout.LayoutParams(-1, -2))
                tag = Row(box, title, sub, comments)
            }
        }
    }
}
