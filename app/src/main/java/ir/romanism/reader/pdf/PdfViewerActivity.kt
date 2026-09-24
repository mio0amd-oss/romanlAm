package ir.romanism.reader.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.pdf.viewer.fragment.PdfViewerFragment
import androidx.core.content.FileProvider
import ir.romanism.reader.R
import java.io.File

/**
 * PDF reader. It can be launched either by the app itself or directly from
 * Android file managers using ACTION_VIEW for PDF/BIN documents.
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
    }

    private var viewer: PdfViewerFragment? = null
    private var documentUri: Uri? = null
    private var temporaryPdf: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        val incoming = readIncomingUri()
        if (incoming == null) {
            toast("فایل PDF پیدا نشد")
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            try {
                viewer?.isTextSearchActive = true
            } catch (e: Exception) {
                toast("جست‌وجو در دسترس نیست: ${e.message}")
            }
        }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { shareCurrent() }

        try {
            val prepared = prepareIncomingDocument(incoming.first, incoming.second)
            documentUri = prepared.first
            findViewById<TextView>(R.id.tvTitle).text = prepared.second
            showPdf(prepared.first)
        } catch (e: Exception) {
            toast("خطا در باز کردن فایل: ${e.message ?: "فایل نامعتبر است"}")
            finish()
        }
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
