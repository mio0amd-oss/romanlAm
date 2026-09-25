package ir.romanism.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.romanism.reader.model.Post
import ir.romanism.reader.network.PostsRepository
import ir.romanism.reader.pdf.PdfFileUtils
import ir.romanism.reader.pdf.PdfViewerActivity
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

private const val CRASH_LOG_FILE = "crash_log.txt"
private const val DONATION_URL = "https://daramet.com/Romanismm"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashHandler(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot()
            }
        }
    }
}

private fun installCrashHandler(context: Context) {
    val appContext = context.applicationContext
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        try {
            File(appContext.filesDir, CRASH_LOG_FILE)
                .writeText(Log.getStackTraceString(throwable))
        } catch (_: Exception) {
        }
        previousHandler?.uncaughtException(Thread.currentThread(), throwable)
    }
}

private fun readAndClearCrashLog(context: Context): String? {
    val file = File(context.filesDir, CRASH_LOG_FILE)
    if (!file.exists()) return null
    val content = try { file.readText() } catch (_: Exception) { null }
    file.delete()
    return content
}

private fun openPdfViewer(context: Context, uri: Uri, title: String) {
    val intent = Intent(context, PdfViewerActivity::class.java).apply {
        putExtra(PdfViewerActivity.EXTRA_URI, uri)
        putExtra(PdfViewerActivity.EXTRA_TITLE, title)
    }
    context.startActivity(intent)
}

private fun openDonationPage(context: Context) {
    val uri = Uri.parse(DONATION_URL)
    val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.android.chrome")
    }
    try {
        context.startActivity(chromeIntent)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun safeTitle(fileName: String): String {
    return fileName
        .substringBeforeLast('.', fileName)
        .replace("_", " ")
        .trim()
        .ifBlank { "رمان" }
}

private fun downloadDirectory(context: Context): File =
    File(context.filesDir, "downloads").apply { mkdirs() }

private fun urlHash(url: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }.take(20)
}

private fun localFileFor(context: Context, post: Post): File {
    val original = post.fileName.ifBlank { "novel.pdf" }
    val clean = original.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(downloadDirectory(context), "${urlHash(post.fileUrl)}_$clean")
}

private fun findExistingLocalFile(context: Context, post: Post): File? {
    val preferred = localFileFor(context, post)
    if (preferred.exists() && preferred.length() > 0) return preferred

    val base = preferred.name.substringBeforeLast('.', preferred.name)
    val dir = downloadDirectory(context)
    return dir.listFiles()?.firstOrNull {
        it.name.startsWith("$base.") && it.length() > 0
    }
}

private fun formatCount(count: Int): String = "$count رمان"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var allPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var activeTag by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var crashLog by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        crashLog = readAndClearCrashLog(context)
    }

    val pickLocalPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        try {
            val name = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "document.pdf"

            if (name.lowercase().endsWith(".bin")) {
                val pdfUri = PdfFileUtils.copyAsPdf(context, uri, name)
                openPdfViewer(context, pdfUri, name.substringBeforeLast('.') + ".pdf")
            } else {
                openPdfViewer(context, uri, name)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                e.message ?: "فایل قابل باز کردن نیست",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    suspend fun load(showSpinner: Boolean = true) {
        if (showSpinner) loading = true
        error = null

        try {
            val fresh = PostsRepository.fetchPosts(context)
            allPosts = fresh
        } catch (e: Exception) {
            val cached = PostsRepository.loadCachedPosts(context)
            if (cached.isNotEmpty()) {
                allPosts = cached
                error = null
            } else {
                error = e.message ?: "خطای ناشناخته"
            }
        } finally {
            loading = false
            refreshing = false
        }
    }

    // هر بار برنامه دوباره به حالت فعال برگردد، فهرست تازه می‌شود.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            load(showSpinner = allPosts.isEmpty())
        }
    }

    val filtered = remember(allPosts, query, activeTag) {
        val q = query.trim().lowercase()
        val tag = activeTag?.lowercase()

        allPosts.filter { post ->
            val text = "${post.fileName} ${post.description}".lowercase()
            (q.isEmpty() || text.contains(q)) &&
                (tag == null || text.contains(tag))
        }
    }

    selectedPost?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedPost = null },
            title = {
                Text(
                    text = safeTitle(post.fileName),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = post.description.ifBlank { "متنی برای این مورد ثبت نشده است." },
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    textAlign = TextAlign.Right,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedPost = null }) {
                    Text("بستن")
                }
            }
        )
    }

    crashLog?.let { log ->
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("اپ دفعه‌ی قبل کرش کرده بود") },
            text = {
                Text(
                    log.take(4000),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash log", log))
                    crashLog = null
                }) {
                    Text("کپی متن خطا")
                }
            },
            dismissButton = {
                TextButton(onClick = { crashLog = null }) {
                    Text("بستن")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رمانیسم") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                refreshing = true
                                load(showSpinner = allPosts.isEmpty())
                            }
                        },
                        enabled = !refreshing
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "به‌روزرسانی")
                    }

                    IconButton(onClick = {
                        pickLocalPdf.launch(
                            arrayOf("application/pdf", "application/octet-stream", "*/*")
                        )
                    }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "باز کردن فایل")
                    }

                    IconButton(onClick = { openDonationPage(context) }) {
                        Text("💰", fontSize = 21.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    activeTag = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = { Text("جستجو...", maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = "جستجو")
                },
                trailingIcon = {
                    Text(
                        text = formatCount(filtered.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("عاشقانه", "مافیایی").forEach { tag ->
                    FilterChip(
                        selected = activeTag == tag,
                        onClick = {
                            activeTag = if (activeTag == tag) null else tag
                            query = ""
                        },
                        label = { Text(tag, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Text(
                text = if (query.isBlank() && activeTag == null) {
                    "مجموع: ${formatCount(allPosts.size)}"
                } else {
                    "نتیجه جستجو: ${formatCount(filtered.size)}"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )

            Spacer(Modifier.height(5.dp))

            when {
                loading && allPosts.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null && allPosts.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { scope.launch { load() } }) {
                            Text("تلاش دوباره")
                        }
                    }
                }

                filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("رمانی مطابق جستجو پیدا نشد.")
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 3.dp, bottom = 16.dp)
                ) {
                    items(
                        items = filtered,
                        key = { "${it.fileName}|${it.fileUrl}" }
                    ) { post ->
                        PostCard(
                            post = post,
                            isDownloading = downloadingUrl == post.fileUrl,
                            onShowSummary = { selectedPost = post },
                            onOpen = {
                                scope.launch {
                                    downloadingUrl = post.fileUrl
                                    try {
                                        val existing = findExistingLocalFile(context, post)
                                        val pdfFile = if (existing != null) {
                                            PdfFileUtils.normalizeDownloadedFile(existing)
                                        } else {
                                            val dest = localFileFor(context, post)
                                            PostsRepository.downloadPdf(post.fileUrl, dest.absolutePath)
                                            PdfFileUtils.normalizeDownloadedFile(dest)
                                        }

                                        val fileUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            pdfFile
                                        )
                                        openPdfViewer(context, fileUri, pdfFile.name)
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        downloadingUrl = null
                                    }
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
fun PostCard(
    post: Post,
    isDownloading: Boolean,
    onShowSummary: () -> Unit,
    onOpen: () -> Unit
) {
    ElevatedCard(
        onClick = onShowSummary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = safeTitle(post.fileName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = post.description.ifBlank { "برای مطالعه کلیک کنید." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onOpen,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("در حال آماده‌سازی…")
                } else {
                    Text("مطالعه")
                }
            }
        }
    }
}
