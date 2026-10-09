package com.bookshelf.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/** One book: read/unread toggle and a dated, optionally located, list of short comments. */
class BookActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: Store
    private lateinit var bookId: String

    private lateinit var readBox: CheckBox
    private lateinit var commentList: LinearLayout
    private lateinit var input: EditText
    private lateinit var addButton: TextView

    private var pendingComment: String? = null
    private var askedForLocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        bookId = intent.getStringExtra("id") ?: return finish()
        val book = store.book(bookId) ?: return finish()
        setContentView(buildUi(book))
        renderComments()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun buildUi(book: Book): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.header))
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        header.addView(text(book.title, 22f, R.color.on_accent, bold = true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        })
        header.addView(
            text(listOfNotNull(book.author, book.year?.toString()).joinToString(" · "), 14f, R.color.on_accent)
                .apply { setTextColor(0xCCFFFFFF.toInt()) }
        )
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        readBox = CheckBox(this).apply {
            text = "I’ve read this book"
            setTextColor(color(R.color.ink))
            textSize = 16f
            isChecked = book.read
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { store.setRead(bookId, isChecked) }
        }
        root.addView(readBox, LinearLayout.LayoutParams(-1, dp(56)).apply {
            leftMargin = dp(12); rightMargin = dp(12)
        })

        commentList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        root.addView(ScrollView(this).apply { addView(commentList) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(color(R.color.paper))
        }
        input = EditText(this).apply {
            hint = "Add a short comment"
            filters = arrayOf(InputFilter.LengthFilter(MAX_COMMENT))
            maxLines = 3
            setTextColor(color(R.color.ink))
            setHintTextColor(color(R.color.muted))
        }
        addButton = primaryButton("Add").apply { setOnClickListener { onAddComment() } }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(addButton, LinearLayout.LayoutParams(-2, dp(48)).apply { leftMargin = dp(8) })
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun renderComments() {
        commentList.removeAllViews()
        val comments = store.comments(bookId)
        if (comments.isEmpty()) {
            commentList.addView(text("No comments yet.", 15f, R.color.muted).apply {
                setPadding(dp(4), dp(8), 0, 0)
            })
            return
        }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        for (c in comments) {
            val where = when {
                c.place != null -> c.place
                c.lat != null && c.lon != null -> String.format(Locale.US, "%.4f, %.4f", c.lat, c.lon)
                else -> null
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(R.color.paper, 12, R.color.line)
                addView(text(c.text, 16f, R.color.ink))
                addView(
                    text(listOfNotNull(fmt.format(Date(c.createdAt)), where).joinToString(" · "), 12f, R.color.muted)
                        .apply { setPadding(0, dp(4), 0, 0) }
                )
                setOnLongClickListener { confirmDelete(c); true }
            }
            commentList.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        commentList.addView(text("Long-press a comment to delete it.", 12f, R.color.muted).apply {
            setPadding(dp(4), dp(12), 0, 0)
        })
    }

    private fun confirmDelete(c: Comment) {
        AlertDialog.Builder(this)
            .setMessage("Delete this comment?")
            .setPositiveButton("Delete") { _, _ -> store.deleteComment(c.id); renderComments() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- adding a comment ----------------------------------------------------------------------

    private fun onAddComment() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (hasLocationPermission() || askedForLocation) {
            saveComment(text)
        } else {
            // Location is optional: ask once, and save the comment whatever the answer.
            askedForLocation = true
            pendingComment = text
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION,
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_LOCATION) return
        pendingComment?.let { saveComment(it) }
        pendingComment = null
    }

    private fun saveComment(text: String) {
        val now = System.currentTimeMillis()
        addButton.isEnabled = false
        addButton.alpha = 0.5f
        executor.execute {
            val loc = currentLocation()
            val place = loc?.let { placeName(it) }
            store.addComment(bookId, text, now, loc?.latitude, loc?.longitude, place)
            runOnUiThread {
                addButton.isEnabled = true
                addButton.alpha = 1f
                input.setText("")
                renderComments()
            }
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix if the phone can give one quickly, else a recent last-known one, else null. */
    @SuppressLint("MissingPermission")
    private fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && providers.isNotEmpty()) {
                val provider =
                    if (LocationManager.NETWORK_PROVIDER in providers) LocationManager.NETWORK_PROVIDER else providers[0]
                val result = AtomicReference<Location?>()
                val done = CountDownLatch(1)
                // Direct executor: this thread is blocked on the latch, so the callback must not queue behind it.
                lm.getCurrentLocation(provider, null, Executor { it.run() }, Consumer { loc ->
                    result.set(loc)
                    done.countDown()
                })
                done.await(FIX_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                result.get()?.let { return it }
            }

            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            }
            best?.takeIf { System.currentTimeMillis() - it.time <= MAX_STALE_MS }
        } catch (e: Exception) {
            null
        }
    }

    /** A readable place such as "Hay-on-Wye, United Kingdom"; null when offline or unavailable. */
    private fun placeName(loc: Location): String? = try {
        if (!Geocoder.isPresent()) null else {
            @Suppress("DEPRECATION")
            val a = Geocoder(this, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            a?.let {
                listOfNotNull(it.subLocality, it.locality ?: it.subAdminArea, it.countryName)
                    .distinct().joinToString(", ").ifEmpty { null }
            }
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_COMMENT = 200
        const val REQ_LOCATION = 1
        const val FIX_TIMEOUT_SECONDS = 8L
        const val MAX_STALE_MS = 2 * 60 * 60 * 1000L
    }
}
