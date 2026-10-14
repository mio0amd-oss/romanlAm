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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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

// ==================== طراحی و تم «شب رمانیسم» ====================

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val Ink = Color(0xFFF4EEF5)
private val InkSoft = Color(0xFFA89FB4)
private val PlumNight = Color(0xFF0A0810)
private val PlumSurface = Color(0xFF14101D)
private val PlumSurfaceHigh = Color(0xFF1E1830)
private val PlumSurfaceTop = Color(0xFF2A2140)
private val RoseMain = Color(0xFFFF4D7E)
private val RoseDeep = Color(0xFFB3124F)
private val GoldSoft = Color(0xFFE8B44F)
private val OutlineSoft = Color(0xFF3A3049)

private val RomanismTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
        fontSize = 23.sp, lineHeight = 36.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
        fontSize = 16.5.sp, lineHeight = 27.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 27.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Vazirmatn, fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp
    ),
)

@Composable
fun RomanismTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RoseMain,
            onPrimary = Color.White,
            primaryContainer = RoseDeep,
            onPrimaryContainer = Color.White,
            secondary = GoldSoft,
            onSecondary = Color(0xFF241A02),
            background = PlumNight,
            onBackground = Ink,
            surface = PlumSurface,
            onSurface = Ink,
            surfaceVariant = PlumSurfaceHigh,
            onSurfaceVariant = InkSoft,
            outline = OutlineSoft,
            error = Color(0xFFCF6679),
            onError = Color.White,
        ),
        typography = RomanismTypography,
    ) {
        // رابط فارسی است، همیشه راست‌به‌چپ می‌مانیم.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}

private val CoverPalettes = listOf(
    listOf(Color(0xFFFF6B8B), Color(0xFF8E0F3C)),
    listOf(Color(0xFFB388FF), Color(0xFF3B1A8F)),
    listOf(Color(0xFFFFC46B), Color(0xFF8A4B0E)),
    listOf(Color(0xFF6BE3FF), Color(0xFF0B4E70)),
    listOf(Color(0xFF8BF3A0), Color(0xFF0F5F35)),
    listOf(Color(0xFFFF9E9E), Color(0xFF7A1F2B)),
)

private fun coverPalette(seed: String): List<Color> {
    val index = (seed.hashCode().toLong() and 0xFFFFFFFL) % CoverPalettes.size
    return CoverPalettes[index.toInt()]
}

private val PersianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

private fun toPersianDigits(value: Int): String =
    value.toString().map { ch -> if (ch.isDigit()) PersianDigits[ch - '0'] else ch }.joinToString("")

private val TagRegex = Regex("#[\\p{L}\\p{N}_]+")

private fun extractTags(description: String): List<String> =
    TagRegex.findAll(description)
        .map { it.value.removePrefix("#") }
        .filter { it.length in 2..24 }
        .distinct()
        .take(4)
        .toList()

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "shimmerProgress"
    )
    val base = PlumSurfaceHigh
    val span = 900f
    val start = progress * span * 2f - span
    return Brush.linearGradient(
        colors = listOf(base, PlumSurfaceTop, base),
        start = Offset(start, 0f),
        end = Offset(start + span, 0f)
    )
}

// ==================== اکتیویتی ====================

private const val CRASH_LOG_FILE = "crash_log.txt"
private const val DONATION_URL = "https://daramet.com/Romanismm"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashHandler(this)
        setContent {
            RomanismTheme {
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
    val content = try {
        file.readText()
    } catch (_: Exception) {
        null
    }
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
        .replace("@romanismm", "")
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

private fun formatCount(count: Int): String = "${toPersianDigits(count)} رمان"

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ==================== رابط کاربری ====================

private val BookTags = listOf("همه", "عاشقانه", "صحنه‌دار", "مافیایی", "درخواستی")

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

    suspend fun openPost(post: Post) {
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
        PostDetailsSheet(
            post = post,
            onDismiss = { selectedPost = null },
            onRead = {
                scope.launch {
                    openPost(post)
                    selectedPost = null
                }
            },
            onCopyLink = {
                copyToClipboard(context, "لینک فایل", post.fileUrl)
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
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, "crash log", log)
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
        containerColor = PlumNight,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // هاله‌ی رنگی بالای صفحه، جانِ صفحه‌ی اصلی
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                RoseMain.copy(alpha = 0.16f),
                                RoseDeep.copy(alpha = 0.07f),
                                Color.Transparent
                            ),
                            radius = 900f
                        )
                    )
            )

            Column(modifier = Modifier.fillMaxSize()) {

                ReaderHeader(
                    count = allPosts.size,
                    refreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            load(showSpinner = allPosts.isEmpty())
                        }
                    },
                    onOpenLocal = {
                        pickLocalPdf.launch(
                            arrayOf("application/pdf", "application/octet-stream", "*/*")
                        )
                    },
                    onDonate = { openDonationPage(context) }
                )

                Spacer(Modifier.height(14.dp))

                SearchField(
                    query = query,
                    resultCount = filtered.size,
                    onQueryChange = {
                        query = it
                        activeTag = null
                    }
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BookTags.size) { index ->
                        val tag = BookTags[index]
                        val isAll = tag == "همه"
                        val selected = if (isAll) activeTag == null && query.isBlank() else activeTag == tag
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (isAll) {
                                    activeTag = null
                                } else {
                                    activeTag = if (activeTag == tag) null else tag
                                    query = ""
                                }
                            },
                            label = { Text(tag) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = PlumSurfaceHigh,
                                selectedContainerColor = RoseMain,
                                labelColor = InkSoft,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (query.isBlank() && activeTag == null) {
                        "مجموع: ${formatCount(allPosts.size)}"
                    } else {
                        "نتیجه جستجو: ${formatCount(filtered.size)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    textAlign = TextAlign.Right
                )

                Spacer(Modifier.height(8.dp))

                when {
                    loading && allPosts.isEmpty() -> SkeletonGrid()

                    error != null && allPosts.isEmpty() -> MessageState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "دسترسی به فهرست رمان‌ها ممکن نشد",
                        message = error ?: "",
                        actionText = "تلاش دوباره",
                        onAction = { scope.launch { load() } }
                    )

                    filtered.isEmpty() -> MessageState(
                        icon = Icons.Outlined.SearchOff,
                        title = "رمانی پیدا نشد",
                        message = "واژه‌ی دیگری را امتحان کن یا برچسب را بردار.",
                        actionText = null,
                        onAction = null
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    ) {
                        items(
                            items = filtered,
                            key = { "${it.fileName}|${it.fileUrl}" }
                        ) { post ->
                            BookCard(
                                post = post,
                                isDownloading = downloadingUrl == post.fileUrl,
                                onShowSummary = { selectedPost = post },
                                onOpen = {
                                    scope.launch { openPost(post) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    count: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenLocal: () -> Unit,
    onDonate: () -> Unit,
) {
    val spin = rememberInfiniteTransition(label = "refreshSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "refreshAngle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 10.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RoseMain, RoseDeep))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "رمانیسم",
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            Text(
                text = if (count > 0) {
                    "${formatCount(count)} آماده‌ی مطالعه"
                } else {
                    "در حال همگام‌سازی با romanism.ir…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft
            )
        }

        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "به‌روزرسانی",
                tint = if (refreshing) RoseMain else InkSoft,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (refreshing) angle else 0f
                }
            )
        }

        IconButton(onClick = onOpenLocal) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = "باز کردن فایل",
                tint = InkSoft
            )
        }

        IconButton(onClick = onDonate) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PlumSurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text("💰", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    resultCount: Int,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("جستجو در کتاب‌ها…") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = InkSoft
            )
        },
        trailingIcon = {
            Text(
                text = toPersianDigits(resultCount),
                style = MaterialTheme.typography.labelMedium,
                color = InkSoft,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = RoseMain,
            focusedBorderColor = RoseMain,
            unfocusedBorderColor = OutlineSoft,
            focusedLeadingIconColor = RoseMain,
            unfocusedLeadingIconColor = InkSoft
        )
    )
}

@Composable
private fun BookCard(
    post: Post,
    isDownloading: Boolean,
    onShowSummary: () -> Unit,
    onOpen: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(120),
        label = "cardScale"
    )

    val isBin = post.fileName.lowercase().endsWith(".bin")
    val tags = extractTags(post.description)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onShowSummary
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = PlumSurface),
            border = BorderStroke(1.dp, OutlineSoft.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(coverPalette(post.fileUrl)))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isBin) "BIN" else "PDF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBin) GoldSoft else Color.White
                        )
                    }
                }

                Text(
                    text = safeTitle(post.fileName),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 54.dp)
                )

                Text(
                    text = snippet(post.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 40.dp)
                )

                if (tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tags.size) { index ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(PlumSurfaceHigh)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "#${tags[index]}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSoft,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                GradientPill(
                    text = if (isDownloading) "در حال آماده‌سازی…" else "مطالعه",
                    enabled = !isDownloading,
                    onClick = onOpen
                )
            }
        }
    }
}

private fun snippet(description: String): String {
    val cleaned = description
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return when {
        cleaned.isBlank() -> "برای دیدن توضیحی، کارت را لمس کن."
        cleaned.length <= 90 -> cleaned
        else -> cleaned.take(88).trimEnd() + "…"
    }
}

@Composable
private fun GradientPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        RoseMain.copy(alpha = alpha),
                        RoseDeep.copy(alpha = alpha)
                    )
                )
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = alpha)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailsSheet(
    post: Post,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onCopyLink: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PlumSurface,
        contentColor = Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 22.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = safeTitle(post.fileName),
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isBin = post.fileName.lowercase().endsWith(".bin")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(PlumSurfaceHigh)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isBin) "فایل BIN" else "فایل PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBin) GoldSoft else RoseMain
                    )
                }
                Text(
                    text = "دانلود و مطالعه با همین برنامه",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = post.description.ifBlank { "متنی برای این مورد ثبت نشده است." },
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.92f),
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientPill(
                    text = "مطالعه",
                    enabled = true,
                    onClick = onRead,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = onCopyLink) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = InkSoft,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("کپی لینک", color = InkSoft)
                }
            }
        }
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    actionText: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(PlumSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RoseMain.copy(alpha = 0.9f),
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            textAlign = TextAlign.Center
        )

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                textAlign = TextAlign.Center
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onAction) {
                Text(actionText, color = RoseMain)
            }
        }
    }
}

@Composable
private fun SkeletonGrid() {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        userScrollEnabled = false
    ) {
        items(6) { index ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PlumSurface),
                border = BorderStroke(1.dp, OutlineSoft.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.85f else 0.65f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}
