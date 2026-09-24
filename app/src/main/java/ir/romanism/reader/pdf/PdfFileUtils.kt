package ir.romanism.reader.pdf

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * فایل‌های PDF که بعضی سرورها با پسوند .bin می‌فرستند را هم پشتیبانی می‌کند.
 * اگر محتوای فایل واقعاً PDF باشد، فقط نام/مسیر آن به .pdf تبدیل می‌شود؛
 * هیچ تبدیل باینریِ دیگری لازم نیست و داده‌ها بدون تغییر باقی می‌مانند.
 */
object PdfFileUtils {

    private const val PDF_HEADER = "%PDF-"

    fun isPdfContent(file: File): Boolean {
        if (!file.exists() || file.length() < PDF_HEADER.length) return false
        return file.inputStream().use { input ->
            val header = ByteArray(PDF_HEADER.length)
            val read = input.read(header)
            read == header.size && String(header, Charsets.US_ASCII) == PDF_HEADER
        }
    }

    fun copyAsPdf(context: Context, source: Uri, displayName: String): Uri {
        val safeName = displayName.ifBlank { "document.pdf" }
        val pdfName = safeName.substringBeforeLast('.', safeName) + ".pdf"
        val dest = File(context.cacheDir, "pdf_${System.currentTimeMillis()}_$pdfName")

        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "امکان خواندن فایل وجود ندارد" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }

        if (!isPdfContent(dest)) {
            dest.delete()
            throw IllegalArgumentException("این فایل BIN در واقع PDF نیست و امکان تبدیل خودکار آن وجود ندارد")
        }

        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            dest
        )
    }

    fun normalizeDownloadedFile(file: File): File {
        val lower = file.name.lowercase()
        if (!lower.endsWith(".bin")) return file

        if (!isPdfContent(file)) {
            throw IllegalArgumentException("فایل BIN دانلودشده محتوای PDF ندارد")
        }

        val pdfName = file.nameWithoutExtension + ".pdf"
        val pdfFile = File(file.parentFile, pdfName)
        if (pdfFile.exists()) pdfFile.delete()
        if (!file.renameTo(pdfFile)) {
            file.copyTo(pdfFile, overwrite = true)
            file.delete()
        }
        return pdfFile
    }
}
