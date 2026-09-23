package ir.romanism.reader.network

import ir.romanism.reader.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

// همون آدرس posts.json که سایت هم ازش استفاده می‌کنه
private const val POSTS_URL = "https://raw.githubusercontent.com/binerri/chanel-files/main/posts.json"

object PostsRepository {

    private val client = OkHttpClient()

    suspend fun fetchPosts(): List<Post> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(POSTS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("خطا در دریافت اطلاعات: ${response.code}")
            val body = response.body?.string() ?: "[]"
            val arr = JSONArray(body)
            val list = mutableListOf<Post>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Post(
                        fileName = obj.optString("file_name", ""),
                        description = obj.optString("description", obj.optString("text", "")),
                        fileUrl = obj.optString("file_url", "")
                    )
                )
            }
            list.reversed()
        }
    }

    suspend fun downloadPdf(url: String, destPath: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("خطا در دانلود فایل: ${response.code}")
            val bytes = response.body?.bytes() ?: throw Exception("فایل خالی است")
            java.io.File(destPath).writeBytes(bytes)
        }
        destPath
    }
}
