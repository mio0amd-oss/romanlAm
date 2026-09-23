package ir.romanism.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.romanism.reader.model.Post
import ir.romanism.reader.network.PostsRepository
import ir.romanism.reader.pdf.PdfViewerActivity
import kotlinx.coroutines.launch
import java.io.File

private val TAGS = listOf("صحنه_دار", "عاشقانه", "درخواستی", "مافیایی")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var allPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var activeTag by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    suspend fun load() {
        loading = true
        error = null
        try {
            allPosts = PostsRepository.fetchPosts()
        } catch (e: Exception) {
            error = e.message ?: "خطای ناشناخته"
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val filtered = remember(allPosts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) allPosts
        else allPosts.filter {
            it.description.lowercase().contains(q) || it.fileName.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("رمانیسم") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(12.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    activeTag = TAGS.firstOrNull { t -> t == it.trim() }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جستجو در توضیحات یا اسم فایل...") },
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TAGS.forEach { tag ->
                    FilterChip(
                        selected = activeTag == tag,
                        onClick = {
                            if (activeTag == tag) {
                                activeTag = null
                                query = ""
                            } else {
                                activeTag = tag
                                query = tag
                            }
                        },
                        label = { Text(tag.replace("_", "‌")) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { scope.launch { load() } }) { Text("تلاش دوباره") }
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("چیزی پیدا نشد.")
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered) { post ->
                        PostCard(
                            post = post,
                            isDownloading = downloadingUrl == post.fileUrl,
                            onOpen = {
                                scope.launch {
                                    downloadingUrl = post.fileUrl
                                    try {
                                        val fileName = post.fileName.ifBlank { "novel.pdf" }
                                        val dest = File(context.cacheDir, fileName)
                                        PostsRepository.downloadPdf(post.fileUrl, dest.absolutePath)
                                        val intent = Intent(context, PdfViewerActivity::class.java)
                                        intent.putExtra(PdfViewerActivity.EXTRA_PATH, dest.absolutePath)
                                        intent.putExtra(PdfViewerActivity.EXTRA_TITLE, post.fileName)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        error = e.message
                                    }
                                    downloadingUrl = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: Post, isDownloading: Boolean, onOpen: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (post.fileName.isNotBlank()) {
                Text(post.fileName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            if (post.description.isNotBlank()) {
                Text(post.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onOpen, enabled = !isDownloading) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("در حال دانلود...")
                } else {
                    Text("دانلود و مطالعه")
                }
            }
        }
    }
}
