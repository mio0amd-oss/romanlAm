package ir.romanism.reader.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.pdf.viewer.fragment.PdfViewerFragment
import ir.romanism.reader.R
import java.io.File

/**
 * ریدر PDF با کتابخونه‌ی رسمی Jetpack (androidx.pdf, نسخه‌ی beta):
 * زوم/اسکرول روان، جست‌وجوی متن، و انتخاب/کپی متن رو خود کتابخونه فراهم می‌کنه
 * (به‌جای رندر دستی صفحه به صفحه).
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
    }

    private var viewer: PdfViewerFragment? = null
    private var documentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        val path = intent.getStringExtra(EXTRA_PATH)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "کتاب"

        if (path == null) {
            toast("فایل PDF پیدا نشد")
            finish()
            return
        }

        findViewById<TextView>(R.id.tvTitle).text = title
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            viewer?.setTextSearchActive(true)
        }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { shareCurrent() }

        val fragment = PdfViewerFragment()
        viewer = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.pdfContainer, fragment, "pdf_viewer")
            .commit()

        val file = File(path)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        documentUri = uri

        // فرگمنت باید کامل attach بشه تا setDocumentUri جواب بده
        Handler(Looper.getMainLooper()).postDelayed({
            fragment.setDocumentUri(uri)
        }, 150)
    }

    private fun shareCurrent() {
        val uri = documentUri
        if (uri == null) {
            toast("فایل هنوز آماده نیست")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "اشتراک‌گذاری PDF"))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
