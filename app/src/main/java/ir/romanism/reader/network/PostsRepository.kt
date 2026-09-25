package ir.romanism.reader.network

import android.content.Context
import ir.romanism.reader.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

private const val POSTS_URL = "https://raw.githubusercontent.com/binerri/chanel-files/main/posts.json"
private const val CACHE_FILE = "posts_cache.json"

object PostsRepository {

    private val client = OkHttpClient()

    suspend fun fetchPosts(context: Context): List<Post> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(POSTS_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("خطا در دریافت اطلاعات: ${response.code}")
            }

            val body = response.body?.string() ?: "[]"

            // آخرین فهرست موفق را برای حالت آفلاین نگه می‌داریم.
            File(context.filesDir, CACHE_FILE).writeText(body)

            parsePosts(body)
        }
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
