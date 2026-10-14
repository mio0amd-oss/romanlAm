package ir.romanism.reader.network

import android.content.Context
import ir.romanism.reader.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

private const val CACHE_FILE = "posts_cache.json"

/**
 * منبع فهرست رمان‌ها:
 * ۱) آدرس خود سایت romanism.ir (اگر posts.json را روی دامنه‌ی خودش بگذارد)
 * ۲) همان آدرسی که خود سایت امروز از آن می‌خواند، تا اپ همیشه با سایت هم‌خوان بماند.
 */
private val POSTS_URLS = listOf(
    "https://romanism.ir/posts.json",
    "https://raw.githubusercontent.com/binerri/chanel-files/main/posts.json",
)

object PostsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPosts(context: Context): List<Post> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (url in POSTS_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("پاسخ ناموفق از $url: ${response.code}")
                    }

                    val body = response.body?.string() ?: "[]"

                    // آخرین فهرست موفق را برای حالت آفلاین نگه می‌داریم.
                    File(context.filesDir, CACHE_FILE).writeText(body)

                    return@withContext parsePosts(body)
                }
            } catch (e: Exception) {
                // سراغ منبع بعدی می‌رویم؛ مثلاً وقتی فایل هنوز روی دامنه‌ی سایت نگذاشته است.
                lastError = e
            }
        }

        throw lastError ?: Exception("خطا در دریافت اطلاعات")
    }

    fun loadCachedPosts(context: Context): List<Post> {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return emptyList()

        return try {
            parsePosts(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePosts(body: String): List<Post> {
        val arr = JSONArray(body)
        val list = mutableListOf<Post>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Post(
                    fileName = obj.optString("file_name", ""),
                    description = obj.optString(
                        "description",
                        obj.optString("text", "")
                    ),
                    fileUrl = obj.optString("file_url", "")
                )
            )
        }

        return list.reversed()
    }

    suspend fun downloadPdf(url: String, destPath: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("خطا در دانلود فایل: ${response.code}")
                }

                val bytes = response.body?.bytes()
                    ?: throw Exception("فایل خالی است")

                val destination = File(destPath)
                destination.parentFile?.mkdirs()

                // دانلود را اتمیک‌تر می‌کنیم تا فایل ناقص به عنوان فایل آفلاین شناخته نشود.
                val temporary = File(destination.parentFile, "${destination.name}.part")
                temporary.writeBytes(bytes)

                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
            }

            destPath
        }
}
