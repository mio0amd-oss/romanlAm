package ir.romanism.reader.pdf

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.pdf.viewer.fragment.PdfViewerFragment
import ir.romanism.reader.R
import java.io.File

/**
 * PDF reader. It can be launched either by the app itself or directly from
 * Android file managers using ACTION_VIEW for PDF/BIN documents.
 *
 * صفحه‌ی مطالعه با نوار شناور گرادیانی، حالت تمام‌صفحه (با یک ضربه)
 * و فیلتر شب سه‌ مرحله‌ای برای راحیک چشم.
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        private const val MIN_LOADING_MS = 450L
    }

    private var viewer: PdfViewerFragment? = null
    private var documentUri: Uri? = null
    private var temporaryPdf: File? = null

    private lateinit var toolbar: View
    private lateinit var pdfContainer: FrameLayout
    private lateinit var loadingOverlay: View
    private lateinit var dimOverlay: View
    private lateinit var bottomPill: View
    private lateinit var btnNight: ImageButton

    private var chromeVisible = true
    private var nightLevel = 0
    private var statusBarInset = 0
    private var navBarInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        toolbar = findViewById(R.id.toolbar)
        pdfContainer = findViewById(R.id.pdfContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        dimOverlay = findViewById(R.id.dimOverlay)
        bottomPill = findViewById(R.id.bottomPill)
        btnNight = findViewById(R.id.btnNight)

        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.alpha = 1f

        findViewById<TextView>(R.id.tvTitle).isSelected = true
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            try {
                viewer?.isTextSearchActive = true
            } catch (e: Exception) {
                toast("جست‌وجو در دسترس نیست: ${e.message}")
            }
        }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { shareCurrent() }
        btnNight.setOnClickListener { cycleNightMode() }

        setupInsets()
        setupTapToToggleChrome()

        val incoming = readIncomingUri()
        if (incoming == null) {
            toast("فایل PDF پیدا نشد")
            finish()
            return
        }

        try {
            val prepared = prepareIncomingDocument(incoming.first, incoming.second)
            documentUri = prepared.first
            findViewById<TextView>(R.id.tvTitle).text = prepared.second
            showPdf(prepared.first)
        } catch (e: Exception) {
            hideLoading()
            toast("خطا در باز کردن فایل: ${e.message ?: "فایل نامعتبر است"}")
            finish()
            return
        }

        revealAfterMinimumDelay()
    }

    // ---------- پنجره‌ی تمام‌صفحه و حاشیه‌ها ----------

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.readerRoot)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            statusBarInset = bars.top
            navBarInset = bars.bottom
            applyWindowInsets()
            insets
        }
    }

    private fun applyWindowInsets() {
        toolbar.updatePadding(
            top = if (chromeVisible) statusBarInset else 0,
            bottom = toolbar.paddingBottom,
            left = toolbar.paddingLeft,
            right = toolbar.paddingRight
        )
        val density = resources.displayMetrics.density
        val lp = bottomPill.layoutParams as FrameLayout.LayoutParams
        lp.bottomMargin = navBarInset + (14 * density).toInt()
        lp.marginStart = (14 * density).toInt()
        lp.marginEnd = (14 * density).toInt()
        bottomPill.layoutParams = lp
    }

    private fun setupTapToToggleChrome() {
        val detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    setChromeVisible(!chromeVisible)
                    return true
                }
            }
        )

        // روی رویدادها دست نمی‌زنیم (false) تا زوم و اسکرول خود کتابخانه سالم بماند.
        pdfContainer.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun setChromeVisible(visible: Boolean) {
        chromeVisible = visible

        toolbar.animate().cancel()
        toolbar.animate()
            .alpha(if (visible) 1f else 0f)
            .translationY(if (visible) 0f else -toolbar.height.toFloat())
            .setDuration(220)
            .start()

        bottomPill.animate().cancel()
        bottomPill.animate()
            .alpha(if (visible) 1f else 0f)
            .translationY(if (visible) 0f else bottomPill.height.toFloat())
            .setDuration(220)
            .withEndAction { bottomPill.isVisible = visible }
            .start()
        if (visible) bottomPill.isVisible = true

        applyWindowInsets()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---------- حالت شب ----------

    private fun cycleNightMode() {
        nightLevel = (nightLevel + 1) % 3

        val overlayColor = when (nightLevel) {
            1 -> ContextCompat.getColor(this, R.color.night_overlay_soft)
            2 -> ContextCompat.getColor(this, R.color.night_overlay_strong)
            else -> Color.TRANSPARENT
        }
        dimOverlay.setBackgroundColor(overlayColor)

        val tint = if (nightLevel == 0) {
            ContextCompat.getColor(this, R.color.text_secondary)
        } else {
            ContextCompat.getColor(this, R.color.accent)
        }
        btnNight.setColorFilter(tint)

        val message = when (nightLevel) {
            1 -> getString(R.string.reader_night_soft)
            2 -> getString(R.string.reader_night_strong)
            else -> getString(R.string.reader_night_off)
        }
        toast(message)
    }

    // ---------- آماده‌سازی و نمایش ----------

    private fun revealAfterMinimumDelay() {
        // پرده‌ی آماده‌سازی حداقل برای چند لحظه دیده شود تا باز شدن کتاب نپراند.
        pdfContainer.postDelayed({ hideLoading() }, MIN_LOADING_MS)
    }

    private fun hideLoading() {
        if (loadingOverlay.visibility != View.VISIBLE) return
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(260)
            .withEndAction { loadingOverlay.visibility = View.GONE }
            .start()
    }

    /** Returns URI + display name from either our own extras or ACTION_VIEW. */
    private fun readIncomingUri(): Pair<Uri, String?>? {
        val explicitUri: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_URI)
        }

        if (explicitUri != null) {
            return explicitUri to intent.getStringExtra(EXTRA_TITLE)
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            return uri to queryDisplayName(uri)
        }

        return null
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * For .bin (or a non-PDF MIME type), copy the incoming content to cache,
     * verify the PDF magic header, and expose it through our FileProvider.
     * This is important because a .bin PDF is not actually converted; only
     * its filename/container representation is normalized to .pdf.
     */
    private fun prepareIncomingDocument(uri: Uri, suppliedName: String?): Pair<Uri, String> {
        val name = suppliedName ?: queryDisplayName(uri) ?: "document.pdf"
        val mime = contentResolver.getType(uri)?.lowercase().orEmpty()
        val looksLikeBin = name.lowercase().endsWith(".bin") ||
            (mime.isNotEmpty() && mime != "application/pdf")

        if (!looksLikeBin) {
            return uri to name
        }

        val pdfName = name.substringBeforeLast('.', name) + ".pdf"
        val dest = File(cacheDir, "opened_${System.currentTimeMillis()}_$pdfName")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "امکان خواندن فایل وجود ندارد" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }

        require(PdfFileUtils.isPdfContent(dest)) {
            dest.delete()
            "فایل BIN محتوای PDF ندارد"
        }

        temporaryPdf = dest
        val providerUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            dest
        )
        return providerUri to pdfName
    }

    private fun showPdf(uri: Uri) {
        val fragment = PdfViewerFragment()
        viewer = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.pdfContainer, fragment, "pdf_viewer")
            .commitNow()
        fragment.documentUri = uri
    }

    private fun shareCurrent() {
        val uri = documentUri
        if (uri == null) {
            toast("فایل هنوز آماده نیست")
            return
        }
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "اشتراک‌گذاری PDF"))
        } catch (e: Exception) {
            toast("اشتراک‌گذاری ممکن نشد: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cache files are temporary; Android can also clean cache automatically.
        temporaryPdf?.let { if (it.exists()) it.delete() }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
