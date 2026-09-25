package ir.romanism.reader.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.pdf.viewer.fragment.PdfViewerFragment
import ir.romanism.reader.R

/**
 * ریدر PDF با کتابخونه‌ی رسمی Jetpack (androidx.pdf, نسخه‌ی beta):
 * زوم/اسکرول روان، جست‌وجوی متن، و انتخاب/کپی متن رو خود کتابخونه فراهم می‌کنه
 * (به‌جای رندر دستی صفحه به صفحه).
 *
 * ورودی همیشه یه content Uri‌ـه: چه فایلی که از سایت دانلود و با FileProvider
 * ساخته شده، چه فایلی که مستقیم از حافظه‌ی گوشی با انتخابگر سیستم انتخاب شده.
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
    }

    private var viewer: PdfViewerFragment? = null
    private var documentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        @Suppress("DEPRECATION")
        val uri: Uri? = intent.getParcelableExtra(EXTRA_URI)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "کتاب"

        if (uri == null) {
            toast("فایل PDF پیدا نشد")
            finish()
            return
        }

        findViewById<TextView>(R.id.tvTitle).text = title
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
            documentUri = uri

            val fragment = PdfViewerFragment()
            viewer = fragment
            // commitNow به‌جای commit: تراکنش رو فوری و همزمان اجرا می‌کنه،
            // پس فرگمنت تضمینی attach شده و دیگه نیازی به تأخیر مصنوعی نیست.
            supportFragmentManager.beginTransaction()
                .replace(R.id.pdfContainer, fragment, "pdf_viewer")
                .commitNow()

            fragment.documentUri = uri
        } catch (e: Exception) {
            toast("خطا در باز کردن PDF: ${e.message}")
            e.printStackTrace()
        }
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

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
