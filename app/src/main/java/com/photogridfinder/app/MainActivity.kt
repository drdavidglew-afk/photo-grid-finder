package com.photogridfinder.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

class MainActivity : Activity() {

    private data class PhotoResult(
        val name: String,
        val thumb: Bitmap?,
        val lat: Double?,
        val lon: Double?,
        val reason: String,
    )

    private val executor = Executors.newSingleThreadExecutor()
    private var pendingAction: (() -> Unit)? = null

    private lateinit var card: View
    private lateinit var thumbView: ImageView
    private lateinit var nameView: TextView
    private lateinit var noLocation: View
    private lateinit var reasonView: TextView
    private lateinit var found: View
    private lateinit var latLonView: TextView
    private lateinit var dmsView: TextView
    private lateinit var gridView: TextView
    private lateinit var enView: TextView
    private lateinit var mapsButton: Button
    private lateinit var copyGridButton: Button

    private var lastLat = 0.0
    private var lastLon = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        card = findViewById(R.id.card)
        thumbView = findViewById(R.id.thumb)
        nameView = findViewById(R.id.name)
        noLocation = findViewById(R.id.no_location)
        reasonView = findViewById(R.id.reason)
        found = findViewById(R.id.found)
        latLonView = findViewById(R.id.latlon)
        dmsView = findViewById(R.id.dms)
        gridView = findViewById(R.id.grid)
        enView = findViewById(R.id.en)
        mapsButton = findViewById(R.id.open_maps)
        copyGridButton = findViewById(R.id.copy_grid)

        findViewById<Button>(R.id.pick).setOnClickListener { withPermissions { openPicker() } }
        findViewById<Button>(R.id.copy_latlon).setOnClickListener { copy("Lat/long", latLonView.text) }
        copyGridButton.setOnClickListener { copy("Grid reference", gridView.text) }
        mapsButton.setOnClickListener { openMaps() }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // ---- Permissions ----

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 29) {
            list += Manifest.permission.ACCESS_MEDIA_LOCATION
        }
        return list.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
    }

    private fun withPermissions(action: () -> Unit) {
        val missing = neededPermissions()
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            requestPermissions(missing, REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        }
    }

    // ---- Choosing a photo ----

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { process(it) }
        }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) withPermissions { process(uri) }
    }

    // ---- Reading the photo ----

    private fun process(uri: Uri) {
        card.visibility = View.VISIBLE
        nameView.text = getString(R.string.reading)
        noLocation.visibility = View.GONE
        found.visibility = View.GONE
        thumbView.setImageDrawable(null)
        executor.execute {
            val result = readPhoto(uri)
            runOnUiThread { show(result) }
        }
    }

    /** Versions of the photo to try, most original first. */
    private fun candidateUris(uri: Uri, name: String): List<Uri> {
        val list = mutableListOf<Uri>()
        if (Build.VERSION.SDK_INT >= 29) {
            val media: Uri? = try {
                if (uri.authority == MediaStore.AUTHORITY) uri else MediaStore.getMediaUri(this, uri)
            } catch (e: Exception) {
                null
            }
            val byName = if (media == null) findInMediaStore(name) else null
            for (m in listOfNotNull(media, byName)) {
                try {
                    list += MediaStore.setRequireOriginal(m)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        list += uri
        return list
    }

    private fun findInMediaStore(name: String): Uri? {
        if (name.isBlank()) return null
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readExif(uri: Uri): ExifInterface? {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                return ExifInterface(pfd.fileDescriptor)
            }
        } catch (e: Exception) {
            // fall through to stream
        }
        return try {
            contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun readPhoto(uri: Uri): PhotoResult {
        val name = displayName(uri)
        val thumb = loadThumb(uri)
        var sawExif = false
        var sawGpsTags = false

        for (u in candidateUris(uri, name)) {
            val exif = readExif(u) ?: continue
            if (exif.getAttribute(ExifInterface.TAG_MAKE) != null ||
                exif.getAttribute(ExifInterface.TAG_MODEL) != null ||
                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null
            ) {
                sawExif = true
            }
            if (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null) sawGpsTags = true
            val ll = exif.latLong
            if (ll != null && !(ll[0] == 0.0 && ll[1] == 0.0)) {
                return PhotoResult(name, thumb, ll[0], ll[1], "")
            }
        }

        val locationAllowed = Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        val reason = when {
            sawGpsTags && !locationAllowed ->
                "The photo has a location, but this app isn't allowed to read it. Allow \"Photos and videos\" access for Photo Grid Finder in Settings, then try again."
            sawGpsTags ->
                "The photo had a location, but it was blanked out before it reached this app. Try choosing it from \"Browse\" > your phone's storage > DCIM > Camera."
            sawExif ->
                "Camera details are present but no GPS. Location tagging was probably off in the camera app."
            else ->
                "No photo metadata at all. The photo was probably re-saved or shared through an app that strips it."
        }
        return PhotoResult(name, thumb, null, null, reason)
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) ?: "" else ""
        } ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun loadThumb(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 400 || bounds.outHeight / sample > 400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    // ---- Showing the result ----

    private fun show(r: PhotoResult) {
        nameView.text = r.name.ifBlank { "Photo" }
        if (r.thumb != null) thumbView.setImageBitmap(r.thumb)

        if (r.lat == null || r.lon == null) {
            noLocation.visibility = View.VISIBLE
            found.visibility = View.GONE
            reasonView.text = r.reason
            return
        }

        lastLat = r.lat
        lastLon = r.lon
        noLocation.visibility = View.GONE
        found.visibility = View.VISIBLE
        latLonView.text = String.format(Locale.UK, "%.6f, %.6f", r.lat, r.lon)
        dmsView.text = "${dms(r.lat, 'N', 'S')}   ${dms(r.lon, 'E', 'W')}"

        val pos = OsGrid.toEastingNorthing(r.lat, r.lon)
        val ref = OsGrid.gridRef10(pos.easting, pos.northing)
        if (ref != null) {
            gridView.text = ref
            enView.text = "Easting ${pos.easting.roundToLong()}  ·  Northing ${pos.northing.roundToLong()}"
            copyGridButton.visibility = View.VISIBLE
        } else {
            gridView.text = getString(R.string.outside_grid)
            enView.text = ""
            copyGridButton.visibility = View.GONE
        }
    }

    private fun dms(value: Double, pos: Char, neg: Char): String {
        val v = abs(value)
        val d = floor(v).toInt()
        val mf = (v - d) * 60
        val m = floor(mf).toInt()
        val s = (mf - m) * 60
        return String.format(Locale.UK, "%d° %d′ %.1f″ %c", d, m, s, if (value >= 0) pos else neg)
    }

    private fun copy(label: String, text: CharSequence) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun openMaps() {
        val geo = Uri.parse("geo:$lastLat,$lastLon?q=$lastLat,$lastLon")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (e: Exception) {
            Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQ_PICK = 1
        private const val REQ_PERMS = 2
    }
}
