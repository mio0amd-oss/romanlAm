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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
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
import ir.romanism.reader.model.Post
import ir.romanism.reader.network.PostsRepository
import ir.romanism.reader.pdf.PdfFileUtils
import ir.romanism.reader.pdf.PdfViewerActivity
import kotlinx.coroutines.launch
import java.io.File

private const val CRASH_LOG_FILE = "crash_log.txt"
private const val DONATION_URL = "https://daramet.com/Romanismm"

/**
 * صفحه اصلی: کارت‌های دو ستونه با عنوان و یک خط خلاصه.
 *
 * برای جلوگیری از نمایش مستقیم محتوای صریح، ورودی‌های دارای برچسب «صحنه‌دار»
 * در این نمای عمومی فهرست نمی‌شوند.
 */
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
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            File(appContext.filesDir, CRASH_LOG_FILE)
                .writeText(Log.getStackTraceString(throwable))
        } catch (_: Exception) {
        }
        previousHandler?.uncaughtException(thread, throwable)
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

    // Prefer Chrome when installed; otherwise use the user's default browser.
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

private fun isExplicitPost(post: Post): Boolean {
    val text = "${post.fileName} ${post.description}".lowercase()
    return text.contains("صحنه_دار") ||
        text.contains("صحنه‌دار") ||
        text.contains("صحنه دار")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var allPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var crashLog by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                openPdfViewer(
                    context,
                    pdfUri,
                    name.substringBeforeLast('.') + ".pdf"
                )
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

    val visiblePosts = remember(allPosts) {
        allPosts.filterNot(::isExplicitPost)
    }

    val filtered = remember(visiblePosts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) visiblePosts
        else visiblePosts.filter {
            it.description.lowercase().contains(q) ||
                it.fileName.lowercase().contains(q)
        }
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
                    val cm = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
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
                    IconButton(onClick = {
                        pickLocalPdf.launch(
                            arrayOf(
                                "application/pdf",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "باز کردن فایل"
                        )
                    }

                    IconButton(onClick = { openDonationPage(context) }) {
                        Text(
                            text = "💰",
                            fontSize = 21.sp
                        )
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
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جستجوی رمان...") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                error != null -> Box(
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
                    Text("رمانی پیدا نشد.")
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "رمان‌ها",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }

                    items(
                        items = filtered,
                        key = { "${it.fileName}|${it.fileUrl}" }
                    ) { post ->
                        PostCard(
                            post = post,
                            isDownloading = downloadingUrl == post.fileUrl,
                            onOpen = {
                                scope.launch {
                                    downloadingUrl = post.fileUrl
                                    try {
                                        val fileName = post.fileName.ifBlank {
                                            "novel.pdf"
                                        }
                                        val dest = File(
                                            context.cacheDir,
                                            fileName
                                        )

                                        PostsRepository.downloadPdf(
                                            post.fileUrl,
                                            dest.absolutePath
                                        )

                                        val pdfFile =
                                            PdfFileUtils.normalizeDownloadedFile(dest)

                                        val fileUri =
                                            FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                pdfFile
                                            )

                                        openPdfViewer(
                                            context,
                                            fileUri,
                                            pdfFile.name
                                        )
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
fun PostCard(
    post: Post,
    isDownloading: Boolean,
    onOpen: () -> Unit
) {
    ElevatedCard(
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
                    Text("در حال دانلود…")
                } else {
                    Text("مطالعه")
                }
            }
        }
    }
}
