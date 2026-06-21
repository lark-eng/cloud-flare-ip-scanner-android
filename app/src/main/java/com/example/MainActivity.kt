package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SavedIpEntity
import com.example.scanner.ScanResult
import com.example.ui.theme.*
import com.example.viewmodel.*
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

fun applyThemeColors(isDark: Boolean, hue: Float) {
    if (isDark) {
        DarkBg = Color(0xFF070A0F)
        CardBg = Color(0xFF0F1622)
        SurfaceVariantBg = Color(0xFF1B2535)
        TextPrimary = Color(0xFFF1F5F9)
        TextSecondary = Color(0xFF8A99AD)
        GridDivider = Color(0xFF1E293B)
        AccentBlue = Color.hsv(hue, 0.80f, 1.0f)
        AccentCyan = AccentBlue
        AccentTeal = Color.hsv((hue + 30f) % 360f, 0.75f, 1.0f)
        AccentGreen = Color(0xFF10B981)
        AccentYellow = Color(0xFFF59E0B)
    } else {
        DarkBg = Color(0xFFF1F5F9)
        CardBg = Color(0xFFFFFFFF)
        SurfaceVariantBg = Color(0xFFE2E8F0)
        TextPrimary = Color(0xFF0F172A)
        TextSecondary = Color(0xFF475569)
        GridDivider = Color(0xFFCBD5E1)
        AccentBlue = Color.hsv(hue, 0.82f, 0.65f)
        AccentCyan = Color.hsv((hue + 15f) % 360f, 0.85f, 0.58f)
        AccentTeal = Color.hsv((hue + 30f) % 360f, 0.88f, 0.52f)
        AccentGreen = Color(0xFF0D9488)
        AccentYellow = Color(0xFFEA580C)
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load saved theme states and apply them globally
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDarkSaved = prefs.getBoolean("is_dark_theme", true)
        val hueSaved = prefs.getFloat("theme_hue", 210f)
        applyThemeColors(isDarkSaved, hueSaved)
        
        enableEdgeToEdge()
        setContent {
            val isDark = DarkBg != Color(0xFFF8FAFC)
            MyApplicationTheme(darkTheme = isDark) {
                var showSplash by remember { mutableStateOf(true) }

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "SplashToMainTransition"
                ) { isSplash ->
                    if (isSplash) {
                        AppSplashScreen(
                            onTimeout = { showSplash = false }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            ScannerMainScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerMainScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val initialSortName = remember(sharedPrefs) { sharedPrefs.getString("global_sort_option", IpSortOption.NONE.name) ?: IpSortOption.NONE.name }
    val initialSort = remember(initialSortName) {
        try { IpSortOption.valueOf(initialSortName) } catch(e: Exception) { IpSortOption.NONE }
    }
    var globalSortOption by remember { mutableStateOf(initialSort) }
    val onSortOptionChanged: (IpSortOption) -> Unit = { option ->
        globalSortOption = option
        sharedPrefs.edit().putString("global_sort_option", option.name).apply()
    }
    val savedIps by viewModel.savedIps.collectAsState()
    val activeScanResults by viewModel.activeScanResults.collectAsState()
    val scanUiState by viewModel.scanUiState.collectAsState()
    val ipInputList by viewModel.ipInputList.collectAsState()
    val timeoutMs by viewModel.timeoutMs.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val ipPoolSize by viewModel.ipPoolSizeToGenerate.collectAsState()
    val configInput by viewModel.configInput.collectAsState()
    val isCloudflareConfig by viewModel.isCloudflareConfig.collectAsState()
    val customPort by viewModel.customPort.collectAsState()
    val isSpeedTestEnabled by viewModel.isSpeedTestEnabled.collectAsState()
    val speedTestLimit by viewModel.speedTestLimit.collectAsState()

    val parsedConfig = remember(configInput) { viewModel.parseVlessTrojanConfig(configInput) }

    var activeTab by remember { mutableStateOf(0) } // 0 = Scan, 1 = Database History
    var showConfigDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var isPersian by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                if (isPersian) "برای دریافت اعلان زنده پیشرفت اسکن، لطفاً اجازه ارسال اعلان را بدهید" 
                else "Please authorize notifications to see scan progress live", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isPersian) {
        viewModel.isPersianLanguage = isPersian
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- TOP HEADER STYLED WITH A PREMIUM NEON GLOW ---
            val selectedActiveIps by viewModel.selectedActiveIps.collectAsState()
            val selectedSavedIps by viewModel.selectedSavedIps.collectAsState()
            val isSelectionMode = (activeTab == 0 && selectedActiveIps.isNotEmpty()) || (activeTab == 1 && selectedSavedIps.isNotEmpty())
            val selectionCount = if (activeTab == 0) selectedActiveIps.size else selectedSavedIps.size
            val selectedIps = if (activeTab == 0) selectedActiveIps else selectedSavedIps

            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isPersian) "رکوردهای انتخاب شده: $selectionCount" else "$selectionCount items selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (activeTab == 0) viewModel.clearActiveIpSelection() else viewModel.clearSavedIpSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Small C button to copy replaced configs
                        IconButton(
                            onClick = {
                                if (parsedConfig != null) {
                                    val configs = selectedIps.map { ip -> parsedConfig.copyWithIp(ip) }
                                    copyToClipboard(
                                        context = context,
                                        text = configs.joinToString("\n"),
                                        toastMessage = if (isPersian) "${configs.size} کانفیگ با آی‌پی جدید کپی شد!" else "${configs.size} configs with new IPs copied!",
                                        viewModel = viewModel
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        if (isPersian) "ابتدا یک کانفیگ معتبر در تنظیمات به عنوان الگو ثبت کنید" else "Please first import a valid config in settings",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AccentTeal.copy(alpha = 0.15f))
                                    .border(1.dp, AccentTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "c",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        lineHeight = 13.sp
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Copy IPs button
                        IconButton(
                            onClick = {
                                val text = selectedIps.joinToString("\n")
                                copyToClipboard(
                                    context = context,
                                    text = text,
                                    toastMessage = if (isPersian) "${selectedIps.size} آدرس آی‌پی کپی شد!" else "${selectedIps.size} IP addresses copied!",
                                    viewModel = viewModel
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy selected IPs",
                                tint = AccentCyan
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            // Dynamically load the generated blue cloud logo directly!
                            Image(
                                painter = painterResource(id = R.drawable.img_blue_cloud),
                                contentDescription = "Cloudflare Blue Cloud Logo",
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, AccentCyan, RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "یابنده آی‌پی" else "IP Finder",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isPersian) "یابنده آی‌پی تمیز کلودفلر" else "CF Clean IP Finder",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = AccentCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = if (isPersian) "برنامه از @COD_LARK" else "app by @COD_LARK",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { exportApkToDownloads(context, viewModel) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "ذخیره APK",
                                tint = AccentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.testTag("config_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Config parameters",
                                tint = AccentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // --- SMOOTH GLASSMORPHIC TAB NAVIGATION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceVariantBg)
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = if (isPersian) "اسکنر زنده" else "Live Scanner",
                        icon = Icons.Default.Search,
                        selected = activeTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTab = 0
                            viewModel.clearSavedIpSelection()
                        }
                    )
                    TabButton(
                        text = if (isPersian) "ذخیره شده (${savedIps.size})" else "Saved (${savedIps.size})",
                        icon = Icons.Default.Star,
                        selected = activeTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTab = 1
                            viewModel.clearActiveIpSelection()
                        }
                    )
                }
            }



            // --- ANIMATED TAB NAVIGATION TRANSITION VIEW ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing))
                            .togetherWith(fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing)))
                    },
                    label = "TabContentTransition"
                ) { targetTab ->
                    if (targetTab == 0) {
                        LiveScannerSection(
                            viewModel = viewModel,
                            scanUiState = scanUiState,
                            ipInputList = ipInputList,
                            activeScanResults = activeScanResults,
                            ipPoolSize = ipPoolSize,
                            configInput = configInput,
                            parsedConfig = parsedConfig,
                            customPort = customPort,
                            context = context,
                            isPersian = isPersian,
                            sortOption = globalSortOption,
                            onSortOptionChanged = onSortOptionChanged
                        )
                    } else {
                        SavedHistorySection(
                            viewModel = viewModel,
                            savedIps = savedIps,
                            parsedConfig = parsedConfig,
                            context = context,
                            isPersian = isPersian,
                            sortOption = globalSortOption,
                            onSortOptionChanged = onSortOptionChanged
                        )
                    }
                }
            }
        }

        // --- PROGRESS FLOATING CARD (WITH BEAUTIFUL BOUNCING FADE) ---
        AnimatedVisibility(
            visible = scanUiState is ScanUiState.Scanning,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val sState = scanUiState as? ScanUiState.Scanning
            if (sState != null) {
                Card(
                    modifier = Modifier
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AccentBlue, AccentCyan))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isPersian) "در حال اسکن کاندیداها... (${sState.scanned}/${sState.total})" else "Scanning Candidates... (${sState.scanned}/${sState.total})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Text(
                                text = "${(sState.progressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = AccentCyan
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = sState.progressPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentBlue,
                            trackColor = SurfaceVariantBg
                        )
                    }
                }
            }
        }

        // --- OUTLINED PREMIUM PARAMETERS CONFIG DIALOG (WITH POP-IN SPRING ANIMATION ON CUSTOM DIALOG CARD) ---
        if (showConfigDialog) {
            Dialog(
                onDismissRequest = { showConfigDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                var animateTrigger by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    animateTrigger = true
                }
                
                val scale by animateFloatAsState(
                    targetValue = if (animateTrigger) 1f else 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                val alpha by animateFloatAsState(
                    targetValue = if (animateTrigger) 1f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .border(1.5.dp, AccentBlue, RoundedCornerShape(28.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPersian) "تنظیمات پارامترهای اسکن" else "Scan Configuration Parameters",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = { isPersian = !isPersian }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "تغییر زبان / Toggle Language",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                if (isPersian) "مشخصات کانفیگ VLESS / Trojan" else "VLESS / Trojan Configuration Profile",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = configInput,
                                onValueChange = {
                                    viewModel.updateConfigInput(it)
                                },
                                placeholder = {
                                    Text("vless://uuid@host:port?query#remark", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (parsedConfig != null) {
                                Text(
                                    text = if (isPersian) "✓ معتبر: پروتکل ${parsedConfig.scheme.uppercase()} روی پورت ${parsedConfig.port} استخراج شد" else "✓ Valid: ${parsedConfig.scheme.uppercase()} configuration parsed on Port ${parsedConfig.port}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = AccentTeal, fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                when (isCloudflareConfig) {
                                    true -> {
                                        Text(
                                            text = if (isPersian) "☁️ این کانفیگ متعلق به کلودفلر (شبکه هدف) است." else "☁️ This configuration belongs to Cloudflare (target network).",
                                            style = MaterialTheme.typography.bodySmall.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    false -> {
                                        Text(
                                            text = if (isPersian) "⚠️ هشدار: آدرس کانفیگ متعلق به کلودفلر نیست!" else "⚠️ Warning: Config address does NOT belong to Cloudflare!",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    null -> {
                                        Text(
                                            text = if (isPersian) "⏳ در حال بررسی تعلق آدرس کانفیگ به کلودفلر..." else "⏳ Checking address Cloudflare status...",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        )
                                    }
                                }
                            } else if (configInput.isNotEmpty()) {
                                Text(
                                    text = if (isPersian) "⚠️ فرمت نامعتبر! اسکن با پورت پیش‌فرض انجام می‌شود." else "⚠️ Invalid format! Scanning fallback to the main screen port.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF43F5E), fontWeight = FontWeight.SemiBold)
                                )
                            } else {
                                Text(
                                    text = if (isPersian) "کانفیگ فعال را وارد کنید. در غیر این صورت اسکن روی پورت انتخابی خواهد بود." else "Insert active profile. Scanning fallback to the main screen port.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Column {
                            Text(
                                if (isPersian) "محدودیت زمان انتظار ارزیابی (میلی‌ثانیه)" else "Probe Timeout Limit (milliseconds)",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = timeoutMs.toString(),
                                onValueChange = {
                                    val checked = it.toIntOrNull() ?: 1500
                                    viewModel.updateTimeout(checked)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        Column {
                            Text(
                                if (isPersian) "میزان همزمانی (حداکثر سوکت‌های موازی)" else "Concurrency Level (maximum parallel sockets)",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = concurrency.toString(),
                                onValueChange = {
                                    val checked = it.toIntOrNull() ?: 15
                                    viewModel.updateConcurrency(checked)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        // Automatic Speed Test controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "تست سرعت خودکار" else "Automatic Speed Test",
                                    style = MaterialTheme.typography.labelMedium.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    if (isPersian) "اندازه‌گیری و تخمین سرعت آی‌پی‌ها هنگام اسکن" else "Estimate speed of IPs during scan",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Switch(
                                checked = isSpeedTestEnabled,
                                onCheckedChange = { viewModel.updateSpeedTestEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentBlue,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = SurfaceVariantBg
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { showThemeDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Customize ThemeStyle",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text(
                                    text = "v1.1.0",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            TextButton(onClick = { showConfigDialog = false }) {
                                Text(if (isPersian) "اعمال تغییرات" else "Apply Settings", color = AccentBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showThemeDialog) {
        ThemeCustomizerDialog(
            isPersian = isPersian,
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) AccentBlue else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) (if (AccentBlue.isLight()) Color.Black else Color.White) else TextSecondary,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            )
        }
    }
}

@Composable
fun LiveScannerSection(
    viewModel: ScannerViewModel,
    scanUiState: ScanUiState,
    ipInputList: String,
    activeScanResults: List<ScanResult>,
    ipPoolSize: Int,
    configInput: String,
    parsedConfig: VlessTrojanConfig?,
    customPort: Int,
    context: Context,
    isPersian: Boolean = false,
    sortOption: IpSortOption,
    onSortOptionChanged: (IpSortOption) -> Unit
) {
    var isPoolSetupExpanded by remember { mutableStateOf(true) }
    val activeSortOption = sortOption
    var showActiveSortMenu by remember { mutableStateOf(false) }

    val sortedActiveResults = remember(activeScanResults, activeSortOption) {
        sortIpList(activeScanResults, activeSortOption, { it.latency }, { it.speed })
    }

    val selectedActiveIps by viewModel.selectedActiveIps.collectAsState()
    val isAnySelectedActive = selectedActiveIps.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- INPUT SEGMENT WITH MASSIVE CAPACITY ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(24.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(SurfaceVariantBg, AccentCyan))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isPoolSetupExpanded = !isPoolSetupExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "تنظیم استخر آی‌پی کلودفلر" else "Cloudflare IP Pool Setup",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            AnimatedVisibility(
                                visible = !isPoolSetupExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                val countStr = if (ipPoolSize >= 1000) "${ipPoolSize / 1000}k" else ipPoolSize.toString()
                                val portStr = (parsedConfig?.port ?: customPort).toString()
                                Text(
                                    text = "$countStr • $portStr",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedVisibility(
                                visible = isPoolSetupExpanded,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Text(
                                    text = if (isPersian) "اندازه استخر: $ipPoolSize آی‌پی" else "Pool Size: $ipPoolSize IPs",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            
                            val rotationAngle by animateFloatAsState(
                                targetValue = if (isPoolSetupExpanded) 0f else 180f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVariantBg)
                                    .border(0.5.dp, AccentCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle Pool Setup",
                                    tint = AccentCyan,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer(rotationZ = rotationAngle)
                                )
                            }
                        }
                    }
                    
                    AnimatedVisibility(
                        visible = isPoolSetupExpanded,
                        enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                            // --- 50,000+ CLOUDFLARE POOL GENERATOR SIZE PICKER ---
                            Text(
                                text = if (isPersian) "اندازه جمعیت کاندیدای اسکن دقیق را پیکربندی کنید (پشتیبانی تا ۵۰ هزار+):" else "Configure exact scan candidate population size (supports up to 50k+):",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceVariantBg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(100, 1000, 5000, 10000, 50000).forEach { size ->
                                    val isSelected = ipPoolSize == size
                                    val displayStr = if (size >= 1000) "${size / 1000}k" else size.toString()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) AccentCyan else Color.Transparent)
                                            .clickable { viewModel.updateIpPoolSize(size) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayStr,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) (if (AccentCyan.isLight()) Color.Black else Color.White) else TextSecondary
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom or config-derived port chooser
                            Text(
                                text = if (isPersian) "پورت هدف را انتخاب کنید:" else "Select target scanning port:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceVariantBg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (parsedConfig != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AccentTeal.copy(alpha = 0.15f))
                                            .border(1.dp, AccentTeal, RoundedCornerShape(12.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Config Active Override",
                                                tint = AccentTeal,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = if (isPersian) "تغییر پورت طبق کانفیگ فعال است" else "Config Port Override Active",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = AccentTeal, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                )
                                                Text(
                                                    text = if (isPersian) "پورت اسکن: ${parsedConfig.port} از پروفایل فعلی ${parsedConfig.scheme.uppercase()} استخراج شد" else "Scanning Port: ${parsedConfig.port} extracted from current ${parsedConfig.scheme.uppercase()} profile",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 10.sp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // User specifies custom port
                                    listOf(443, 80, 8080, 2053).forEach { port ->
                                        val isSelected = customPort == port
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) AccentBlue else SurfaceVariantBg)
                                                .clickable { viewModel.updateCustomPort(port) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = port.toString(),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) (if (AccentBlue.isLight()) Color.Black else Color.White) else TextSecondary
                                                )
                                            )
                                        }
                                    }

                                    // Manual port dialog
                                    var showPortChooserDialog by remember { mutableStateOf(false) }
                                    val isDefault = customPort in listOf(443, 80, 8080, 2053)
                                    Box(
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (!isDefault) AccentBlue else SurfaceVariantBg)
                                            .clickable { showPortChooserDialog = true }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (!isDefault) "$customPort ✎" else if (isPersian) "سایر پورت‌ها ✎" else "Other ✎",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isDefault) (if (AccentBlue.isLight()) Color.Black else Color.White) else TextSecondary
                                            )
                                        )
                                    }

                                    if (showPortChooserDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showPortChooserDialog = false },
                                            containerColor = CardBg,
                                            shape = RoundedCornerShape(20.dp),
                                            title = { Text(if (isPersian) "وارد کردن پورت سفارشی" else "Enter Custom Port", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) },
                                            text = {
                                                TextField(
                                                    value = customPort.toString(),
                                                    onValueChange = {
                                                        val p = it.toIntOrNull() ?: 443
                                                        viewModel.updateCustomPort(p)
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = SurfaceVariantBg,
                                                        unfocusedContainerColor = SurfaceVariantBg,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent
                                                    )
                                                )
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { showPortChooserDialog = false }) {
                                                    Text(if (isPersian) "ذخیره" else "Save", color = AccentBlue)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextField(
                                value = ipInputList,
                                onValueChange = { viewModel.updateInputList(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .testTag("ip_input_field"),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = TextPrimary
                                ),
                                placeholder = {
                                    Text(
                                        text = if (isPersian) "آی‌پی‌ها/رنج‌های سفارشی را اینجا اضافه کنید یا اجازه دهید اسکنر استخر ساب‌نت کلودفلر را پویا گسترش دهد..." else "Add custom IPs/Ranges here or let the scanner expand Cloudflare subnet pool dynamically...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Dual Status Play/Cancel Buttons with ultra-curved shape design
                    if (scanUiState is ScanUiState.Scanning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("scanning_status_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceVariantBg,
                                    contentColor = TextSecondary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                enabled = false
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPersian) "در حال اسکن..." else "Running Scan...",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = { viewModel.stopScanning() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("cancel_scan_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE11D48),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Scan", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPersian) "لغو" else "Cancel",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScanning() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("start_scan_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = if (AccentBlue.isLight()) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "شروع اسکن آی‌پی تمیز" else "Start Clean IP Scan",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- RESULTS TABLE HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleText = if (isPersian) "آی‌پی‌های تمیز شناسایی شده" else "Detected Clean IPs"
                                val sortSuffix = when (activeSortOption) {
                                    IpSortOption.PING -> if (isPersian) " (پینگ صعودی)" else " (Ping Asc)"
                                    IpSortOption.SPEED -> if (isPersian) " (سرعت نزولی)" else " (Speed Desc)"
                                    IpSortOption.BOTH -> if (isPersian) " (ترکیبی)" else " (Both)"
                                    IpSortOption.NONE -> ""
                                }
                                Text(
                                    text = "$titleText$sortSuffix",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                if (activeScanResults.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            IconButton(onClick = { showActiveSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Sort list",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showActiveSortMenu,
                                onDismissRequest = { showActiveSortMenu = false },
                                modifier = Modifier
                                    .background(CardBg)
                                    .border(1.dp, SurfaceVariantBg, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isPersian) "مرتب‌سازی بر اساس" else "Sort by",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = AccentCyan)
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = activeSortOption == IpSortOption.PING, onClick = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isPersian) "پینگ (کمترین پینگ)" else "Ping (Lowest ping)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        onSortOptionChanged(IpSortOption.PING)
                                        showActiveSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = activeSortOption == IpSortOption.SPEED, onClick = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isPersian) "سرعت (بیشترین سرعت)" else "Speed (Highest speed)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        onSortOptionChanged(IpSortOption.SPEED)
                                        showActiveSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = activeSortOption == IpSortOption.BOTH, onClick = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isPersian) "ترکیبی (پینگ و سرعت)" else "Both (Ping & Speed)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        onSortOptionChanged(IpSortOption.BOTH)
                                        showActiveSortMenu = false
                                    }
                                )
                            }
                        }
                        Text(
                            text = if (isPersian) "تعداد: ${activeScanResults.size}" else "Count: ${activeScanResults.size}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (activeScanResults.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBg)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_blue_cloud),
                        contentDescription = "Cloud Logo Illustration",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isPersian) "آماده برای اسکن استخر کلودفلر" else "Ready to Scan Cloudflare Pool",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPersian) "برای پایش زیرشبکه‌های کاندیدای آی‌پی دکمه را فشار دهید" else "Press button to probe candidate IP subnets",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariantBg),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPersian) "آدرس آی‌پی" else "IP Address", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(if (isPersian) "تاخیر" else "Latency", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.Center)
                        Text(if (isPersian) "سرعت تخمینی" else "Est Speed", modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.End)
                        Spacer(modifier = Modifier.width(if (parsedConfig != null) 90.dp else 48.dp)) // Extra margin for active direct action buttons
                    }
                }
            }

            itemsIndexed(sortedActiveResults) { index, result ->
                val isLast = index == sortedActiveResults.lastIndex
                val bottomShape = if (isLast) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) else RoundedCornerShape(0.dp)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bottomShape)
                        .background(if (selectedActiveIps.contains(result.ip)) AccentBlue.copy(alpha = 0.15f) else CardBg)
                        .border(1.dp, SurfaceVariantBg, bottomShape)
                ) {
                    ResultRow(
                        ip = result.ip,
                        latency = result.latency,
                        speed = result.speed,
                        config = parsedConfig,
                        isPersian = isPersian,
                        isSelected = selectedActiveIps.contains(result.ip),
                        isSelecting = isAnySelectedActive,
                        onRowClick = {
                            if (isAnySelectedActive) {
                                viewModel.toggleActiveIpSelection(result.ip)
                            } else {
                                if (parsedConfig != null) {
                                    val resolved = parsedConfig.copyWithIp(result.ip)
                                    copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                                } else {
                                    copyToClipboard(context, result.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                                }
                            }
                        },
                        onRowLongClick = {
                            viewModel.toggleActiveIpSelection(result.ip)
                        },
                        onCopyClick = {
                            copyToClipboard(context, result.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                        },
                        onCopyConfigClick = if (parsedConfig != null) {
                            {
                                val resolved = parsedConfig.copyWithIp(result.ip)
                                copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                            }
                        } else null,
                        onManualSpeedTest = {
                            viewModel.runManualSpeedTest(result.ip)
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun SavedHistorySection(
    viewModel: ScannerViewModel,
    savedIps: List<SavedIpEntity>,
    parsedConfig: VlessTrojanConfig?,
    context: Context,
    isPersian: Boolean = false,
    sortOption: IpSortOption,
    onSortOptionChanged: (IpSortOption) -> Unit
) {
    val selectedSavedIps by viewModel.selectedSavedIps.collectAsState()
    val retestingSavedIps by viewModel.retestingSavedIps.collectAsState()
    val isAnySelectedSaved = selectedSavedIps.isNotEmpty()

    val savedSortOption = sortOption
    var showSavedSortMenu by remember { mutableStateOf(false) }

    val sortedSavedIps = remember(savedIps, savedSortOption) {
        sortIpList(savedIps, savedSortOption, { it.latency }, { it.speed })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPersian) "پایگاه داده آی‌پی‌های تمیز ذخیره شده" else "Cached Clean IPs Database",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = if (isPersian) "جهت تست مجدد پینگ و سرعت روی مقادیر آن‌ها ضربه بزنید" else "Tap latency/speed values to re-test individually",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentCyan,
                        fontSize = 11.sp
                    )
                )
            }
            if (savedIps.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        IconButton(onClick = { showSavedSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Sort list",
                                tint = AccentCyan
                            )
                        }
                        DropdownMenu(
                            expanded = showSavedSortMenu,
                            onDismissRequest = { showSavedSortMenu = false },
                            modifier = Modifier
                                .background(CardBg)
                                .border(1.dp, SurfaceVariantBg, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isPersian) "مرتب‌سازی بر اساس" else "Sort by",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = AccentCyan)
                                    )
                                },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = savedSortOption == IpSortOption.PING, onClick = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isPersian) "پینگ (کمترین پینگ)" else "Ping (Lowest ping)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSortOptionChanged(IpSortOption.PING)
                                    showSavedSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = savedSortOption == IpSortOption.SPEED, onClick = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isPersian) "سرعت (بیشترین سرعت)" else "Speed (Highest speed)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSortOptionChanged(IpSortOption.SPEED)
                                    showSavedSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = savedSortOption == IpSortOption.BOTH, onClick = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isPersian) "ترکیبی (پینگ و سرعت)" else "Both (Ping & Speed)", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    onSortOptionChanged(IpSortOption.BOTH)
                                    showSavedSortMenu = false
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val copyText = savedIps.joinToString(separator = "\n") { it.ip }
                            copyToClipboard(context, copyText, if (isPersian) "همه آی‌پی‌های ذخیره شده در حافظه موقت کپی شدند!" else "All saved IPs copied to clipboard!", viewModel)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy all saved IPs",
                            tint = AccentCyan
                        )
                    }
                    IconButton(onClick = { viewModel.clearSavedHistory() }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear database", tint = Color(0xFFF43F5E))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedIps.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_blue_cloud),
                    contentDescription = "Cloud Logo Illustration",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.2.dp, AccentCyan, RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isPersian) "پایگاه داده خالی است" else "Database is Empty",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPersian) "آی‌پی‌های تمیز اسکن شده به طور خودکار در اینجا ذخیره و ماندگار می‌شوند" else "Scanned clean IPs will populate and persist here automatically",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantBg),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPersian) "آی‌پی" else "IP", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    Text(if (isPersian) "پینگ" else "Ping", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.Center)
                    Text(if (isPersian) "سرعت" else "Speed", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(if (parsedConfig != null) 130.dp else 90.dp)) // Extra action buttons alignment offset
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, SurfaceVariantBg, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                items(sortedSavedIps) { item ->
                    val isSelected = selectedSavedIps.contains(item.ip)
                    val isRetesting = retestingSavedIps.contains(item.ip)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) AccentBlue.copy(alpha = 0.15f) else CardBg)
                            .combinedClickable(
                                onClick = {
                                    if (isAnySelectedSaved) {
                                        viewModel.toggleSavedIpSelection(item.ip)
                                    } else {
                                        if (parsedConfig != null) {
                                            val resolved = parsedConfig.copyWithIp(item.ip)
                                            copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                                        } else {
                                            copyToClipboard(context, item.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSavedIpSelection(item.ip)
                                }
                            )
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAnySelectedSaved) {
                            Box(
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(AccentCyan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (AccentCyan.isLight()) Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, TextSecondary, CircleShape)
                                    )
                                }
                            }
                        }

                        Text(
                            text = item.ip,
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Retest latency target
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (!isAnySelectedSaved && !isRetesting) {
                                        viewModel.retestSavedIp(item)
                                    }
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRetesting) {
                                Text(
                                    text = if (isPersian) "تست..." else "Testing...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                val pingColor = if (item.latency <= 0) Color.Gray else AccentCyan
                                val pingText = if (item.latency <= 0) "Failed" else "${item.latency} ms"
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = pingText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = pingColor,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (isPersian) "تست پینگ" else "Test Ping",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Retest speed target
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (!isAnySelectedSaved && !isRetesting) {
                                        viewModel.retestSavedIp(item)
                                    }
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (isRetesting) {
                                Text(
                                    text = if (isPersian) "سرعت..." else "Speed...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    textAlign = TextAlign.End
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f MB/s", item.speed),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = AccentCyan,
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                        ),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = if (isPersian) "تست سرعت" else "Test Speed",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        ),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.width(if (parsedConfig != null) 30.dp else 90.dp), // reduced width as re-test clicks are on latency/speed itself
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parsedConfig != null) {
                                IconButton(
                                    onClick = {
                                        val resolved = parsedConfig.copyWithIp(item.ip)
                                        copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(AccentTeal.copy(alpha = 0.15f))
                                            .border(1.dp, AccentTeal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "c",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = AccentTeal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { copyToClipboard(context, item.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy IP",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSavedIp(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ResultRow(
    ip: String,
    latency: Long,
    speed: Double,
    config: VlessTrojanConfig? = null,
    onRowClick: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyConfigClick: (() -> Unit)? = null,
    isPersian: Boolean = false,
    onManualSpeedTest: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelecting: Boolean = false,
    onRowLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) AccentBlue.copy(alpha = 0.15f) else CardBg)
            .combinedClickable(
                onClick = { onRowClick() },
                onLongClick = { onRowLongClick?.invoke() }
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelecting) {
            Box(
                modifier = Modifier.padding(end = 12.dp)
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AccentCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (AccentCyan.isLight()) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, TextSecondary, CircleShape)
                    )
                }
            }
        }
        Text(
            text = ip,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${latency} ms",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (latency <= 0) Color.Gray else AccentCyan
            ),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.weight(1.1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (speed == -1.0) {
                Text(
                    text = if (isPersian) "درحال بررسی..." else "Testing...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentTeal,
                        fontWeight = FontWeight.Normal,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    textAlign = TextAlign.End
                )
            } else if (speed > 0.0) {
                Text(
                    text = String.format(Locale.US, "%.1f MB/s", speed),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.End
                )
            } else {
                Text(
                    text = if (isPersian) "تست سرعت" else "Test Speed",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentTeal,
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    modifier = Modifier
                        .clickable { onManualSpeedTest?.invoke() }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.width(if (config != null) 90.dp else 48.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config != null && onCopyConfigClick != null) {
                IconButton(
                    onClick = onCopyConfigClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.15f))
                            .border(1.dp, AccentTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "c",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                lineHeight = 11.sp
                            )
                        )
                    }
                }
            }
            IconButton(
                onClick = onCopyClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy IP",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LiveActivityNotificationBar(
    viewModel: ScannerViewModel,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    val liveEvents by viewModel.liveEvents.collectAsState()
    val latestEvent = liveEvents.lastOrNull()
    var showLogDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = latestEvent != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        latestEvent?.let { event ->
            // Pick icon and color scheme based on event type
            val (icon, tint) = when (event.type) {
                LiveEventType.SCAN -> Pair(Icons.Default.Search, AccentCyan)
                LiveEventType.DATABASE -> Pair(Icons.Default.Star, AccentTeal)
                LiveEventType.COPY -> Pair(Icons.Default.ContentCopy, AccentBlue)
                LiveEventType.SUCCESS -> Pair(Icons.Default.CheckCircle, AccentGreen)
                LiveEventType.ERROR -> Pair(Icons.Default.Warning, Color(0xFFF43F5E))
                else -> Pair(Icons.Default.Info, AccentCyan)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showLogDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = tint.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Event type",
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    // Display scrolling or sliding text animation
                    AnimatedContent(
                        targetState = event,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height } + fadeOut()
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = "ActivityText"
                    ) { activeEvent ->
                        Text(
                            text = if (isPersian) activeEvent.messageFa else activeEvent.messageEn,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pulse badge for "LIVE"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPersian) "زنده" else "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = tint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    // History log button
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Open Logs",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showLogDialog) {
        val reversedLogs = remember(liveEvents) { liveEvents.reversed() }
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.5.dp, AccentBlue, RoundedCornerShape(24.dp)),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "گزارش عملیات زنده" else "Live Operations Log",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    IconButton(
                        onClick = { 
                            viewModel.logEvent("Cleared logging session", "جلسه گزارشات پاکسازی شد", LiveEventType.INFO)
                            viewModel.logEvent("Session restarted", "شروع مجدد گزارشات", LiveEventType.INFO)
                            showLogDialog = false 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear logs",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    if (reversedLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPersian) "هیچ گزارشی ثبت نشده است" else "No operations logged yet",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(reversedLogs, key = { it.id }) { log ->
                                val (icon, tint) = when (log.type) {
                                    LiveEventType.SCAN -> Pair(Icons.Default.Search, AccentCyan)
                                    LiveEventType.DATABASE -> Pair(Icons.Default.Star, AccentTeal)
                                    LiveEventType.COPY -> Pair(Icons.Default.ContentCopy, AccentBlue)
                                    LiveEventType.SUCCESS -> Pair(Icons.Default.CheckCircle, AccentGreen)
                                    LiveEventType.ERROR -> Pair(Icons.Default.Warning, Color(0xFFF43F5E))
                                    else -> Pair(Icons.Default.Info, AccentCyan)
                                }
                                val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss", Locale.US) }
                                val logTime = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tint.copy(alpha = 0.05f))
                                        .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isPersian) log.messageFa else log.messageEn,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = logTime,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TextSecondary,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text(if (isPersian) "بستن" else "Close", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

fun copyToClipboard(context: Context, text: String, toastMessage: String = "IP Address copied to clipboard", viewModel: ScannerViewModel? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("IP Address", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    
    if (viewModel != null) {
        val ipTruncated = if (text.length > 35) text.take(35) + "..." else text
        viewModel.logEvent(
            "Copied to clipboard: $ipTruncated",
            "در حافظه موقت کپی شد: $ipTruncated",
            LiveEventType.COPY
        )
    }
}

fun exportApkToDownloads(context: Context, viewModel: ScannerViewModel? = null) {
    try {
        val srcFile = File(context.applicationInfo.sourceDir)
        if (!srcFile.exists()) {
            Toast.makeText(context, "فایل منبع یافت نشد", Toast.LENGTH_SHORT).show()
            viewModel?.logEvent("Failed to locate application source package", "فایل نصب منبع برنامه یافت نشد", LiveEventType.ERROR)
            return
        }
        val fileName = "IP_Finder_Universal.apk"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        FileInputStream(srcFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                Toast.makeText(context, "فایل APK یونیورسال با موفقیت در پوشه دانلودها ذخیره شد\n$fileName", Toast.LENGTH_LONG).show()
                viewModel?.logEvent(
                    "Exported Universal APK successfully: $fileName",
                    "فایل نصبی یونیورسال برنامه با موفقیت در پوشه دانلودها ثبت شد: $fileName",
                    LiveEventType.SUCCESS
                )
            } else {
                Toast.makeText(context, "خطا در ساخت فایل در پوشه دانلودها", Toast.LENGTH_SHORT).show()
                viewModel?.logEvent("Failed to create file within public Downloads folder", "خطا در هنگام ایجاد دسترسی فایل درون پوشه دانلودها", LiveEventType.ERROR)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, fileName)
            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(context, "فایل APK یونیورسال با موفقیت در پوشه دانلودها ذخیره شد\n$fileName", Toast.LENGTH_LONG).show()
            viewModel?.logEvent(
                "Exported Universal APK successfully to Downloads folder: $fileName",
                "فایل نصبی یونیورسال برنامه به عنوان بسته خروجی دانلود شد: $fileName",
                LiveEventType.SUCCESS
            )
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در ذخیره کردن فایل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        viewModel?.logEvent(
            "Encountered error while preparing installer APK export: ${e.localizedMessage}",
            "خطا در زمان خروجی گرفتن بسته نصبی برنامه: ${e.localizedMessage}",
            LiveEventType.ERROR
        )
    }
}

@Composable
fun AppSplashScreen(onTimeout: () -> Unit) {
    var animateStart by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animateStart = true
        delay(2200)
        onTimeout()
    }
    
    val scale by animateFloatAsState(
        targetValue = if (animateStart) 1f else 0.75f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (animateStart) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "LogoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (animateStart) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "TextAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080F1E)), // Beautiful Premium Deep Navy Blue (سورمه ای)
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_blue_cloud),
                contentDescription = "Cloudflare Blue Cloud Logo",
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = logoAlpha
                    },
                contentScale = ContentScale.Fit
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "IP Finder",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentCyan, // Accent firoozei
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.graphicsLayer {
                    this.alpha = textAlpha
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "app by @COD_LARK",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.graphicsLayer {
                    this.alpha = textAlpha
                }
            )
        }
    }
}

// --- CUSTOM INTELLECTUAL THEMES AND MULTI-CRITERIA SORTING SYSTEM ---

enum class IpSortOption {
    NONE, PING, SPEED, BOTH
}

fun <T> sortIpList(
    list: List<T>,
    option: IpSortOption,
    getLatency: (T) -> Long,
    getSpeed: (T) -> Double
): List<T> {
    return when (option) {
        IpSortOption.NONE -> list
        IpSortOption.PING -> list.sortedBy { getLatency(it) }
        IpSortOption.SPEED -> list.sortedByDescending { getSpeed(it) }
        IpSortOption.BOTH -> {
            if (list.isEmpty()) return list
            
            // 1. Calculate relative ping score values
            val hasPingUnder200 = list.any { getLatency(it) in 1..199 }
            val pingThresh = if (hasPingUnder200) {
                200L
            } else {
                val sortedPings = list.map { getLatency(it) }.sorted()
                // Take the 10th item (index 9) or the last item if less than 10
                val index = minOf(9, sortedPings.lastIndex)
                if (index >= 0) sortedPings[index] else 200L
            }
            
            // 2. Calculate relative speed score values
            val hasSpeedOverHalf = list.any { getSpeed(it) > 0.5 }
            val maxSpeed = list.maxOfOrNull { getSpeed(it) } ?: 1.0
            val speedThresh = if (hasSpeedOverHalf) {
                0.5
            } else {
                val sortedSpeeds = list.map { getSpeed(it) }.sortedDescending()
                val index = minOf(9, sortedSpeeds.lastIndex)
                if (index >= 0) sortedSpeeds[index] else 0.5
            }

            list.sortedByDescending { item ->
                val p = getLatency(item)
                val v = getSpeed(item)
                
                // Score for ping (0.0 to 1.0)
                val pingScore = if (p <= 0) {
                    0.0
                } else if (p < pingThresh) {
                    val pThreshDouble = if (pingThresh <= 0) 1.0 else pingThresh.toDouble()
                    1.0 - (p.toDouble() / (pThreshDouble + 1.0))
                } else {
                    0.0
                }
                
                // Score for speed (0.0 to 1.0 or higher)
                val speedScore = if (hasSpeedOverHalf) {
                    if (v > 0.5) v / maxSpeed else 0.0
                } else {
                    if (v >= speedThresh && v > 0) v / maxSpeed else 0.0
                }
                
                pingScore + speedScore
            }
        }
    }
}

fun Color.getHue(): Float {
    val hsv = FloatArray(3)
    val colorInt = android.graphics.Color.rgb(
        (this.red * 255).toInt(),
        (this.green * 255).toInt(),
        (this.blue * 255).toInt()
    )
    android.graphics.Color.colorToHSV(colorInt, hsv)
    return hsv[0]
}

@Composable
fun ThemeCustomizerDialog(
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tempIsDark by remember { mutableStateOf(DarkBg != Color(0xFFF8FAFC)) }
    var tempHue by remember { mutableStateOf(AccentBlue.getHue()) }

    val tempColor = if (tempIsDark) {
        Color.hsv(tempHue, 0.80f, 1.0f)
    } else {
        Color.hsv(tempHue, 0.88f, 0.65f)
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .border(2.dp, tempColor, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = if (tempIsDark) Color(0xFF0F1622) else Color.White),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isPersian) "تنظیمات تم برنامه" else "Customize Application Theme",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (tempIsDark) Color.White else Color(0xFF0F172A)
                    )
                )
                
                HorizontalDivider(color = if (tempIsDark) Color(0xFF1B2535) else Color(0xFFEDF2F7))
                
                // Color mode selection: Dark or Light
                Text(
                    text = if (isPersian) "رنگ اصلی برنامه (پس‌زمینه)" else "Primary Background Mode",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tempColor
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dark mode option card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tempIsDark = true }
                            .border(
                                width = if (tempIsDark) 2.dp else 1.dp,
                                color = if (tempIsDark) tempColor else Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0F)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F1622))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tempIsDark) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tempColor))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isPersian) "تیره (مشکی)" else "Dark (Black)",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    
                    // Light mode option card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { tempIsDark = false }
                            .border(
                                width = if (!tempIsDark) 2.dp else 1.dp,
                                color = if (!tempIsDark) tempColor else Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF2F7)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!tempIsDark) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(tempColor))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isPersian) "روشن (سفید)" else "Light (White)",
                                color = Color(0xFF0F172A),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Color wheel / Complementary Color picker
                Text(
                    text = if (isPersian) "رنگ مکمل برنامه (چرخونه رنگ)" else "Complementary Accent Color",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tempColor
                    ),
                    modifier = Modifier.align(Alignment.Start)
                )
                
                // Draw a beautiful circular sweep gradient Color Wheel on Canvas
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val sweepGradient = remember {
                        Brush.sweepGradient(
                            colors = listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    }
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val centerX = size.width.toFloat() / 2f
                                    val centerY = size.height.toFloat() / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    val outerRadius = size.width.toFloat() / 2f
                                    val innerRadius = outerRadius * 0.35f
                                    if (distance >= innerRadius && distance <= outerRadius) {
                                        var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        if (angle < 0) {
                                            angle += 360f
                                        }
                                        tempHue = angle
                                    }
                                }
                            }
                    ) {
                        // Draw outer color wheel donut
                        drawCircle(
                            brush = sweepGradient,
                            radius = size.width / 2f
                        )
                        // Cut out the center to make it a beautiful modern donut color wheel
                        drawCircle(
                            color = if (tempIsDark) Color(0xFF0F1622) else Color.White,
                            radius = size.width * 0.22f
                        )
                        
                        // Draw helper circle
                        drawCircle(
                            color = tempColor,
                            radius = size.width * 0.14f
                        )
                        
                        // Draw selector handle pin on the wheel
                        val rad = Math.toRadians(tempHue.toDouble())
                        val handleDist = size.width * 0.38f
                        val handleX = (size.width / 2f) + (handleDist * kotlin.math.cos(rad)).toFloat()
                        val handleY = (size.height / 2f) + (handleDist * kotlin.math.sin(rad)).toFloat()
                        
                        drawCircle(
                            color = Color.White,
                            radius = 11.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(handleX, handleY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = tempColor,
                            radius = 8.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(handleX, handleY)
                        )
                    }
                }
                
                // Pre-selected vibrant accents row for premium rapid pick
                val presetHues = listOf(210f, 180f, 150f, 120f, 45f, 330f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presetHues.forEach { hue ->
                        val presetColor = Color.hsv(hue, 0.95f, 1.0f)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .clickable { tempHue = hue }
                                .border(
                                    width = if (tempHue == hue) 2.dp else 0.dp,
                                    color = if (tempIsDark) Color.White else Color.Black,
                                    shape = CircleShape
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Action Buttons: Apply & Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isPersian) "لغو" else "Cancel",
                            color = if (tempIsDark) Color.Gray else Color.DarkGray
                        )
                    }
                    
                    Button(
                        onClick = {
                            applyThemeColors(tempIsDark, tempHue)
                            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            sharedPrefs.edit()
                                .putBoolean("is_dark_theme", tempIsDark)
                                .putFloat("theme_hue", tempHue)
                                .apply()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = tempColor),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (isPersian) "اعمال پوسته" else "Apply Theme",
                            color = if (tempIsDark && tempHue in 35f..90f) Color.Black else Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
