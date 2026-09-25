package ir.romanism.reader.pdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * چون بعضی سرورها/فایل‌های محلی پسوند اشتباه (مثل .bin به‌جای .pdf) دارن،
 * این کلاس مطمئن می‌شه فایلی که به ریدر PDF می‌رسه همیشه اسم/پسوند درست داره.
 */
object PdfFileUtils {

    /**
     * فایل دانلودشده از سایت رو، اگه اسمش پسوند pdf. نداشته باشه، به یه فایل
     * هم‌نام با پسوند .pdf تبدیل می‌کنه (rename، یا در صورت شکست، کپی).
     */
    fun normalizeDownloadedFile(file: File): File {
        if (file.name.lowercase().endsWith(".pdf")) return file

        val newName = file.nameWithoutExtension + ".pdf"
        val renamed = File(file.parentFile, newName)
        if (file.renameTo(renamed)) return renamed

        file.copyTo(renamed, overwrite = true)
        return renamed
    }

    /**
     * فایلی که از انتخابگر سیستم (Storage Access Framework) اومده رو، حتی اگه
     * اسم اصلیش پسوند pdf نداشته باشه، توی حافظه‌ی موقت اپ با اسم درست کپی
     * می‌کنه و یه content Uri قابل‌استفاده برای ریدر برمی‌گردونه.
     */
    fun copyAsPdf(context: Context, source: Uri, originalName: String): Uri {
        val baseName = originalName.substringBeforeLast('.', originalName).ifBlank { "document" }
        val destFile = File(context.cacheDir, "$baseName.pdf")

        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("امکان خواندن فایل انتخاب‌شده نبود")

        input.use { inStream ->
            destFile.outputStream().use { outStream ->
                inStream.copyTo(outStream)
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
    }
}
