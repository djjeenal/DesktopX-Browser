package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.Bookmark
import com.example.database.DownloadItem
import com.example.database.HistoryItem
import com.example.database.VirtualFile
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DesktopXBackground
                ) {
                    DesktopXAppWrapper()
                }
            }
        }
    }
}

@Composable
fun DesktopXAppWrapper() {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel()
    
    val activeScreen by mainViewModel.activeScreen.collectAsState()
    val isAppLocked by mainViewModel.isAppLocked.collectAsState()
    val isBiometricLockEnabled by mainViewModel.isBiometricLockEnabled.collectAsState()
    val toastMsg by mainViewModel.toastMsg.collectAsState()

    // Handle initial lock check
    LaunchedEffect(isBiometricLockEnabled) {
        if (isBiometricLockEnabled) {
            mainViewModel.toggleAppLock(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isAppLocked) {
            PasscodeLockScreen(
                onUnlock = { mainViewModel.toggleAppLock(false) }
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    DesktopXNavBar(
                        activeScreen = activeScreen,
                        onScreenSelected = { mainViewModel.navigateTo(it) }
                    )
                },
                contentWindowInsets = WindowInsets.navigationBars
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DesktopXBackground, Color(0xFF070709))
                            )
                        )
                ) {
                    DesktopStatusBar(mainViewModel)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = activeScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                "Home" -> HomeScreen(mainViewModel)
                                "Tabs" -> BrowserTabsScreen(mainViewModel)
                                "Workspace" -> DesktopWorkspaceScreen(mainViewModel)
                                "Downloads" -> DownloadsScreen(mainViewModel)
                                "Settings" -> SettingsScreen(mainViewModel)
                            }
                        }
                    }
                }
            }
        }

        // Global responsive HUD toast feedback
        toastMsg?.let { msg ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 30.dp, end = 30.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xEB111420)),
                border = BorderStroke(1.dp, DesktopXPrimary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "HUD Sparkle",
                        tint = DesktopXPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        color = DesktopXTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Premium PC-Mode Top Sleek Telemetry Status Bar
@Composable
fun DesktopStatusBar(viewModel: MainViewModel) {
    val isPcMode by viewModel.isRealPcMode.collectAsState()
    val resolution by viewModel.simulatedResolution.collectAsState()
    val zoomPercent by viewModel.globalZoomPercent.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        color = Color(0x99000000), // bg-black/60 glass shadow
        border = BorderStroke(width = 0.5.dp, color = Color(0x1BFFFFFF)) // white/10 razor separator
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DesktopX 2.4.0",
                    color = DesktopXTextPrimary.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(if (isPcMode) pulseAlpha else 0.4f)
                            .background(
                                color = if (isPcMode) DesktopXPrimary else DesktopXAccent,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (isPcMode) "PC MODE ACTIVE" else "MOBILE REPL",
                        color = if (isPcMode) DesktopXPrimary else DesktopXTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "$resolution Emulated",
                    color = DesktopXTextPrimary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Zoom: $zoomPercent%",
                    color = DesktopXTextPrimary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// Windows 11 Premium Bottom Navigation Dock
@Composable
fun DesktopXNavBar(
    activeScreen: String,
    onScreenSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = Color(0xFF050505), // Pitch black sleek nav body
        border = BorderStroke(width = 1.dp, color = DesktopXSurfaceBorder)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.height(68.dp)
        ) {
            val items = listOf(
                Triple("Home", Icons.Filled.Home, "Hub"),
                Triple("Tabs", Icons.Filled.Web, "Browser"),
                Triple("Workspace", Icons.Filled.Dashboard, "Workspace"),
                Triple("Downloads", Icons.Filled.Folder, "Explorer"),
                Triple("Settings", Icons.Filled.Settings, "Control")
            )

            items.forEach { (route, icon, label) ->
                val isSelected = activeScreen == route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onScreenSelected(route) },
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) DesktopXPrimary else DesktopXTextSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("nav_icon_$route")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) DesktopXTextPrimary else DesktopXTextSecondary
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = DesktopXPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_btn_$route")
                )
            }
        }
    }
}

// Stylized PC Login Clear Screen
@Composable
fun PasscodeLockScreen(onUnlock: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        // High-tech glass overlay
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(listOf(DesktopXPrimary.copy(alpha = 0.4f), Color.Transparent)),
                        CircleShape
                    )
                    .border(2.dp, DesktopXPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Nodes Lock",
                    tint = DesktopXPrimary,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "DesktopX Security Gate",
                color = DesktopXTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Simulated Administrator Clearance Required",
                color = DesktopXTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Text placeholder for bullets
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (i in 0 until 4) {
                    val active = code.length > i
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(1.5.dp, if (showError) DesktopXAccent else DesktopXPrimary, CircleShape)
                            .background(
                                color = if (active) (if (showError) DesktopXAccent else DesktopXPrimary) else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }

            if (showError) {
                Text(
                    text = "Incorrect Security Code. Enter: 1234",
                    color = DesktopXAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Number keyboard pad
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.width(260.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(keys) { key ->
                    Button(
                        onClick = {
                            showError = false
                            when (key) {
                                "C" -> if (code.isNotEmpty()) code = code.dropLast(1)
                                "OK" -> {
                                    if (code == "1234" || code == "") {
                                        onUnlock()
                                    } else {
                                        showError = true
                                        code = ""
                                    }
                                }
                                else -> {
                                    if (code.length < 4) {
                                        code += key
                                        if (code.length == 4) {
                                            if (code == "1234") {
                                                onUnlock()
                                            } else {
                                                showError = true
                                                code = ""
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161B29)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("keypad_$key"),
                        border = BorderStroke(1.dp, DesktopXSurfaceBorder)
                    ) {
                        Text(
                            text = key,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Hint: Default clearance passcode is 1234",
                color = DesktopXTextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ----------------------------------------------------
// SCREEN 1: HOME HUD (Dashboard hub with launcher nodes)
// ----------------------------------------------------
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf("") }
    val bookmarksList by viewModel.bookmarks.collectAsState(initial = emptyList())
    val isPcMode by viewModel.isRealPcMode.collectAsState()
    val resolution by viewModel.simulatedResolution.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Welcome Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DesktopX Console",
                    color = DesktopXTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Premium PC-grade sandboxed browsing",
                    color = DesktopXTextSecondary,
                    fontSize = 13.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF0A0A0B), CircleShape)
                    .border(1.dp, DesktopXPrimary.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DesktopWindows,
                    contentDescription = "PC Status Indicator",
                    tint = DesktopXPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // System Diagnostic HUD Status Gauges (Ultra futuristic aesthetic)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0B)),
            border = BorderStroke(1.dp, DesktopXSurfaceBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ENGINE DIAGNOSTICS",
                    color = DesktopXPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Profile", color = DesktopXTextSecondary, fontSize = 11.sp)
                        Text(if (isPcMode) "Windows X64 Chrome" else "Android Default Mobile", color = DesktopXTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Render Canvas", color = DesktopXTextSecondary, fontSize = 11.sp)
                        Text(resolution, color = DesktopXPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Virtual Cursor", color = DesktopXTextSecondary, fontSize = 11.sp)
                        Text("GPU Safe Mode On", color = DesktopXSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Universal desktop entry search bar
        Text(
            text = "LOAD DESKTOP PORTAL",
            color = DesktopXTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            placeholder = { Text("Enter PC website address or search queries...", color = DesktopXTextSecondary, fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_bar"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0A0A0C),
                unfocusedContainerColor = Color(0xFF070709),
                focusedBorderColor = DesktopXPrimary,
                unfocusedBorderColor = DesktopXSurfaceBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = "Web Search icon", tint = DesktopXTextSecondary)
            },
            trailingIcon = {
                if (inputUrl.isNotEmpty()) {
                    IconButton(onClick = { inputUrl = "" }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear site url input", tint = DesktopXTextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (inputUrl.trim().isNotEmpty()) {
                        viewModel.openNewTab(context, inputUrl)
                        inputUrl = ""
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (inputUrl.trim().isNotEmpty()) {
                    viewModel.openNewTab(context, inputUrl)
                    inputUrl = ""
                } else {
                    viewModel.openNewTab(context, "https://google.com")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("search_launch_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = DesktopXSecondary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Filled.DesktopWindows, contentDescription = "PC node launches")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open inside Browser Engine", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // System Shortcuts Hub Launcher
        Text(
            text = "QUICK DESKTOP SHORTCUTS",
            color = DesktopXTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(380.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookmarksList) { item ->
                ShortcutLauncherCard(item = item, onClick = {
                    viewModel.openNewTab(context, item.url)
                }, onWorkspaceClick = {
                    viewModel.openInWorkspace(context, item.name, item.url)
                })
            }
        }
    }
}

@Composable
fun ShortcutLauncherCard(
    item: Bookmark,
    onClick: () -> Unit,
    onWorkspaceClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shortcut_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0B)),
        border = BorderStroke(1.dp, DesktopXSurfaceBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular stylish launcher icon placeholder with custom logo text
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(DesktopXPrimary.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, DesktopXPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        color = DesktopXPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column {
                    Text(
                        text = item.name,
                        color = DesktopXTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.url.removePrefix("https://").removePrefix("www."),
                        color = DesktopXTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E22)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Tab", fontSize = 11.sp, color = DesktopXPrimary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onWorkspaceClick,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopXSecondary),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Workspace", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 2: BROWSER VIEW (Highly optimized WebView Tabs system)
// ----------------------------------------------------
@Composable
fun BrowserTabsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tabsList by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.currentTabId.collectAsState()
    val isPcMode by viewModel.isRealPcMode.collectAsState()
    val isCursorActive by viewModel.isVirtualMouseEnabled.collectAsState()
    val cursorX by viewModel.cursorX.collectAsState()
    val cursorY by viewModel.cursorY.collectAsState()
    
    val activeTab = tabsList.find { it.id == activeTabId }

    var urlInput by remember { mutableStateOf("") }
    
    // Sync text field to url of loaded page when it changes
    LaunchedEffect(activeTab?.url) {
        activeTab?.url?.let {
            urlInput = it
        }
    }

    if (activeTab == null) {
        // Welcome and Blank Slate browser dashboard screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Web,
                contentDescription = "Empty active browser Node",
                tint = DesktopXTextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "DesktopX Sandboxed WebView Engine",
                color = DesktopXTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open unlimited desktop compatibility portals",
                color = DesktopXTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            Button(
                onClick = { viewModel.openNewTab(context, "https://google.com") },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopXPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add node tap", tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Launch New Tab Instance", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Advanced Web Browser Header Frame (Arc/Chrome Inspired)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0A0A0B),
                    border = BorderStroke(width = 1.dp, color = DesktopXSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Multi-Session top tab tabs listing row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabsList.forEach { tabState ->
                                val isActive = tabState.id == activeTabId
                                Card(
                                    modifier = Modifier
                                        .widthIn(max = 140.dp)
                                        .clickable { viewModel.selectTab(tabState.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) Color(0xFF1A1A1E) else Color(0xFF070709)
                                    ),
                                    border = BorderStroke(1.dp, if (isActive) DesktopXPrimary.copy(alpha = 0.5f) else DesktopXSurfaceBorder),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = tabState.title,
                                            color = if (isActive) Color.White else DesktopXTextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.closeTab(tabState.id) },
                                            modifier = Modifier.size(14.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Dismiss tab session",
                                                tint = DesktopXTextSecondary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Plus icon to add tabs
                            IconButton(
                                onClick = { viewModel.openNewTab(context, "https://google.com") },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF1E1E22), CircleShape)
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add node launcher", tint = DesktopXPrimary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL Search Address Bar and Action items
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { activeTab.webViewInstance?.goBack() },
                                enabled = activeTab.canGoBack,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Search reverse history",
                                    tint = if (activeTab.canGoBack) Color.White else DesktopXTextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { activeTab.webViewInstance?.goForward() },
                                enabled = activeTab.canGoForward,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = "Search forward history",
                                    tint = if (activeTab.canGoForward) Color.White else DesktopXTextSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { activeTab.webViewInstance?.reload() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reload search node",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("browser_address_bar"),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    activeTab.webViewInstance?.loadUrl(urlInput)
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF040405),
                                    unfocusedContainerColor = Color(0xFF070709),
                                    focusedBorderColor = DesktopXPrimary,
                                    unfocusedBorderColor = DesktopXSurfaceBorder
                                ),
                                singleLine = true
                            )

                            // AI FIX CROWN BUTTON
                            IconButton(
                                onClick = {
                                    viewModel.fixCurrentWebsite(activeTab.url, "Broken layout on mobile screen detected. Canvas width overflowed bounds.")
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(DesktopXPrimary, DesktopXSecondary)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .testTag("ai_fixer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AI repair console",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Desktop Device Trackpad toggle button
                            IconButton(
                                onClick = { viewModel.toggleVirtualMouse() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isCursorActive) DesktopXPrimary.copy(alpha = 0.2f) else Color(0xFF1E1E22),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mouse,
                                    contentDescription = "Device Mouse toggle pointer",
                                    tint = if (isCursorActive) DesktopXPrimary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Web Loader progress indicator
                        if (activeTab.isLoading) {
                            LinearProgressIndicator(
                                progress = { activeTab.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp)
                                    .height(2.dp),
                                color = DesktopXPrimary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                // Web rendering frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = {
                            activeTab.webViewInstance ?: WebView(it)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("active_web_view"),
                        update = { webView ->
                            // Ensure standard client callbacks is connected properly
                            webView.webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    viewModel.updateTabLoading(activeTab.id, true, 15)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    viewModel.updateTabLoading(activeTab.id, false, 100)
                                    viewModel.updateTabMetadata(
                                        activeTab.id,
                                        view?.title ?: "Web Page",
                                        url ?: "",
                                        view?.canGoBack() ?: false,
                                        view?.canGoForward() ?: false
                                    )
                                }
                            }
                            webView.webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    viewModel.updateTabLoading(activeTab.id, true, newProgress)
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        viewModel.addConsoleLogToTab(activeTab.id, "[${it.messageLevel()}] ${it.message()}")
                                    }
                                    return true
                                }
                            }
                        }
                    )
                }

                // Keyboard Macro Fast Shortcuts overlay panel
                BrowserKeyMacrosToolbar(viewModel = viewModel)
            }

            // Virtual Mouse on-screen cursor
            if (isCursorActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Don't swallow touch events but intercept for cursor diagnostics if needed
                        }
                ) {
                    // Floating Cursor overlay pointer icon
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .offset { IntOffset(cursorX.toInt(), cursorY.toInt()) }
                            .zIndex(100f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Styled electric arrow cyber vector cursor
                        Icon(
                            imageVector = Icons.Filled.Mouse,
                            contentDescription = "Cursor Arrow Node",
                            tint = DesktopXPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Bottom draggable Floating touchpad trackpad card controller
                    VirtualTrackpadControllerWidget(viewModel = viewModel)
                }
            }

            // Real Time AI Fixing overlay panel
            val fixRecommendation by viewModel.lastFixRecommendation.collectAsState()
            val isGeneratingRecommendation by viewModel.isFixingWebsite.collectAsState()
            
            if (fixRecommendation != null || isGeneratingRecommendation) {
                AIRecommendationPopup(
                    message = fixRecommendation ?: "Scanning HTML elements. Overwriting configurations...",
                    isGenerating = isGeneratingRecommendation,
                    onDismiss = { viewModel.dismissFixerDialog() }
                )
            }
        }
    }
}

// Draggable Floating Keyboard shortcut panels assist toolbar
@Composable
fun BrowserKeyMacrosToolbar(viewModel: MainViewModel) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F1220),
        border = BorderStroke(width = 1.dp, color = DesktopXSurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = "Desktop Macros helper",
                tint = DesktopXPrimary,
                modifier = Modifier.size(16.dp)
            )

            val macros = listOf(
                Pair("copy", "Ctrl+C"),
                Pair("paste", "Ctrl+V"),
                Pair("save", "Ctrl+S"),
                Pair("undo", "Ctrl+Z"),
                Pair("find", "Ctrl+F")
            )

            macros.forEach { (action, label) ->
                Button(
                    onClick = { viewModel.performClipboardAction(action, context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C223B)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("macro_btn_$action")
                ) {
                    Text(text = label, color = DesktopXTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Draggable Virtual Trackpad Joystick Panel Card overlay
@Composable
fun VirtualTrackpadControllerWidget(viewModel: MainViewModel) {
    var ox by remember { mutableStateOf(200f) }
    var oy by remember { mutableStateOf(400f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f)
    ) {
        Card(
            modifier = Modifier
                .offset { IntOffset(ox.toInt(), oy.toInt()) }
                .width(180.dp)
                .height(210.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        ox = (ox + dragAmount.x).coerceAtLeast(0f)
                        oy = (oy + dragAmount.y).coerceAtLeast(0f)
                    }
                }
                .testTag("virtual_mouse_trackpad"),
            colors = CardDefaults.cardColors(containerColor = Color(0xF012162B)),
            border = BorderStroke(1.dp, DesktopXPrimary.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header drag grip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Color(0xFF202742), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("DRAG TRACKPAD DRIVER", color = DesktopXTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mouse circular drag detector touchpad
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color(0xFF0C0E1B), CircleShape)
                        .border(1.5.dp, DesktopXSecondary, CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.moveCursor(dragAmount.x * 12.5f, dragAmount.y * 12.5f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Filled.Mouse, contentDescription = "Mouse move nodes", tint = DesktopXPrimary.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        Text("Touchpad", color = DesktopXTextSecondary, fontSize = 9.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clicking keys
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.simulateVirtualClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopXPrimary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("L-Click", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.showToast("Context menu launched for target coordinate") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263052)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("R-Click", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Real-Time Smart AI Website Fixer dialog
@Composable
fun AIRecommendationPopup(
    message: String,
    isGenerating: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("ai_fixer_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13172E)),
            border = BorderStroke(1.dp, DesktopXPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF231C3D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI repair wizard",
                        tint = DesktopXPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Smart AI Website Fixer",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "DesktopX Gemini Core Engine",
                    color = DesktopXTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F111E), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    if (isGenerating) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DesktopXPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing responsive css tags...", color = DesktopXTextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = message,
                            color = DesktopXTextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopXSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Deactivate Smart Overlay", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 3: WORKSPACE MODE (Draggable floating web windows)
// ----------------------------------------------------
@Composable
fun DesktopWorkspaceScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val windows by viewModel.windows.collectAsState()
    val activeWinId by viewModel.activeWindowId.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E2445), Color(0xFF080A12))
                )
            )
            .testTag("desktop_workspace_screen")
    ) {
        // Futuristic Workspace Grid elements wallpaper UI representation
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Desktop Workspace active",
                color = DesktopXTextSecondary.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Circular shortcuts grid inside desktop background
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val backgroundShortcuts = listOf(
                    Pair("Figma Canvas", "https://figma.com"),
                    Pair("GitHub Explorer", "https://github.com"),
                    Pair("VS Code", "https://vscode.dev"),
                    Pair("Google AI", "https://aistudio.google.com")
                )

                backgroundShortcuts.forEach { (label, url) ->
                    Column(
                        modifier = Modifier
                            .clickable { viewModel.openInWorkspace(context, label, url) }
                            .padding(8.dp)
                            .testTag("desktop_grid_$label"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0x331F2A52), CircleShape)
                                .border(1.dp, DesktopXPrimary.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Filled.DesktopWindows, contentDescription = "Spawn node icon multiplier", tint = DesktopXPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(label, color = DesktopXTextPrimary, fontSize = 10.sp)
                    }
                }
            }
        }

        // Active Floating Web Windows Layer
        windows.forEach { win ->
            val isFocused = win.id == activeWinId
            
            if (!win.isMinimized) {
                FloatingResizableWindowWidget(
                    win = win,
                    isFocused = isFocused,
                    onFocus = { viewModel.selectWindow(win.id) },
                    onDrag = { dx, dy -> viewModel.updateWindowPosition(win.id, dx, dy) },
                    onResize = { dw, dh -> viewModel.updateWindowSize(win.id, dw, dh) },
                    onMinimize = { viewModel.minimizeWindow(win.id) },
                    onMaximize = { viewModel.maximizeWindow(win.id) },
                    onClose = { viewModel.closeWindow(win.id) }
                )
            }
        }

        // Centered elegant Windows 11 Bottom Dock Taskbar Panel
        WorkspaceTaskbarDock(
            windows = windows,
            activeWinId = activeWinId,
            onRestoreWindow = { viewModel.selectWindow(it) }
        )
    }
}

// Draggable Resizable floating box containing its Webview
@Composable
fun FloatingResizableWindowWidget(
    win: WorkspaceWindow,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit
) {
    val zValue = if (isFocused) 10f else 1f
    
    Card(
        modifier = Modifier
            .offset { IntOffset(win.x.toInt(), win.y.toInt()) }
            .width(if (win.isMaximized) 420.dp else win.width.dp)
            .height(if (win.isMaximized) 620.dp else win.height.dp)
            .zIndex(zValue)
            .clickable(onClick = onFocus)
            .testTag("floating_win_${win.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141727)),
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) DesktopXPrimary else DesktopXSurfaceBorder
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Window Header (Draggable Handler)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2036))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DesktopWindows,
                        contentDescription = "Active frame indicator",
                        tint = DesktopXPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = win.title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Minimize widget page", tint = DesktopXTextSecondary, modifier = Modifier.size(12.dp))
                    }
                    IconButton(onClick = onMaximize, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Maximize desktop card", tint = DesktopXTextSecondary, modifier = Modifier.size(12.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "End runtime nodes", tint = DesktopXAccent, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // Real WebView in-frame rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = {
                        win.webViewInstance ?: WebView(it)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Resizing grip handle at bottom corner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0xFF121626)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResize(dragAmount.x, dragAmount.y)
                            }
                        }
                        .background(Color(0xFF232D4C), RoundedCornerShape(bottomEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.Mouse, contentDescription = "Resize drag handler anchor", tint = DesktopXPrimary, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

// Bottom Desktop Dock representing Windows 11 style tasks bar
@Composable
fun WorkspaceTaskbarDock(
    windows: List<WorkspaceWindow>,
    activeWinId: String?,
    onRestoreWindow: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 120.dp, max = 340.dp),
            color = Color(0xF2121522),
            border = BorderStroke(1.dp, DesktopXSurfaceBorder),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (windows.isEmpty()) {
                    Text(
                        text = "Dock idle (Open FIGMA above)",
                        color = DesktopXTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(6.dp)
                    )
                } else {
                    windows.forEach { win ->
                        val isActive = win.id == activeWinId
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(34.dp)
                                .background(
                                    if (isActive) DesktopXPrimary.copy(alpha = 0.25f) else Color(0xFF1B2034),
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) DesktopXPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onRestoreWindow(win.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = win.title.take(1).uppercase(),
                                color = if (isActive) DesktopXPrimary else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 4: FILE SYSTEM AND DOWNLOAD PORTALS PANEL
// ----------------------------------------------------
@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var folderMenuOpen by remember { mutableStateOf(false) }

    val downloadsList by viewModel.downloads.collectAsState(initial = emptyList())
    val virtualFilesList by viewModel.virtualFiles.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Desktop File Hub", color = DesktopXTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Built-in encrypted filesystem explorer", color = DesktopXTextSecondary, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(18.dp))

        // Virtual Directory actions constructor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newFileName,
                onValueChange = { newFileName = it },
                placeholder = { Text("New file name (e.g. app.js)", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("new_file_input"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF101322),
                    unfocusedContainerColor = Color(0xFF101322)
                ),
                singleLine = true
            )
            Button(
                onClick = {
                    if (newFileName.trim().isNotEmpty()) {
                        val fileType = newFileName.substringAfterLast(".", "html")
                        viewModel.createVirtualFile(newFileName, "/root/WorkspaceProjects", false, fileType)
                        newFileName = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopXPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("create_file_btn")
            ) {
                Text("Add File", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Document directory list view items
        Text(
            text = "VIRTUAL FILESYSTEM DIRECTORIES",
            color = DesktopXPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp),
            fontFamily = FontFamily.Monospace
        )

        LazyColumn(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(virtualFilesList) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101322)),
                    border = BorderStroke(1.dp, DesktopXSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Code,
                                contentDescription = "File element",
                                tint = if (file.isDirectory) DesktopXSecondary else DesktopXPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(file.name, color = DesktopXTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(file.path, color = DesktopXTextSecondary, fontSize = 11.sp)
                            }
                        }

                        IconButton(onClick = { viewModel.deleteVirtualFile(file) }) {
                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete virtual registry", tint = DesktopXAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Web Downloads list container
        Text(
            text = "DOWNLOAD CHANNELS QUEUE",
            color = DesktopXTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (downloadsList.isEmpty()) {
                item {
                    Text(
                        "No downloads launched yet. File queues appear here dynamically.",
                        color = DesktopXTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            } else {
                items(downloadsList) { dl ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14172B))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(dl.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Source: ${dl.url}", color = DesktopXTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(dl.size, color = DesktopXPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(dl.status, color = if (dl.status == "COMPLETED") Color.Green else DesktopXAccent, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// SCREEN 5: CONTROL TERMINAL (Settings screen panel UI)
// ----------------------------------------------------
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val isPcMode by viewModel.isRealPcMode.collectAsState()
    val resolution by viewModel.simulatedResolution.collectAsState()
    val isBiometricOn by viewModel.isBiometricLockEnabled.collectAsState()
    val zoomPercent by viewModel.globalZoomPercent.collectAsState()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DesktopX Settings Hub", color = DesktopXTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("System rendering overrides and security parameters", color = DesktopXTextSecondary, fontSize = 12.sp)

        // General PC parameters card overrides
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0B)),
            border = BorderStroke(1.dp, DesktopXSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("REAL PC RENDERING", color = DesktopXPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                // Toggle 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Force Real PC Mode Engine", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Spoof resolution, viewport dimensions, and inject desktop-class header assets", color = DesktopXTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isPcMode,
                        onCheckedChange = { viewModel.setRealPcMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = DesktopXPrimary
                        ),
                        modifier = Modifier.testTag("pc_mode_switch")
                    )
                }

                Divider(color = DesktopXSurfaceBorder)

                // Toggle 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Desktop resolution spoofing preset", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Toggle the simulated physical width parameters", color = DesktopXTextSecondary, fontSize = 11.sp)
                    }

                    val resolutions = listOf("1925x1080", "1440x900", "1280x800")
                    Column(horizontalAlignment = Alignment.End) {
                        resolutions.forEach { res ->
                            Button(
                                onClick = { viewModel.setSimulatedResolution(res) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (resolution == res) DesktopXSecondary else Color(0xFF1E1E22)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .height(28.dp)
                            ) {
                                Text(res, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Divider(color = DesktopXSurfaceBorder)

                // Zoom Percent slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Global Canvas Desktop Zoom override", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("$zoomPercent%", color = DesktopXPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Helps adjust text sizing on high density mobile screen widths", color = DesktopXTextSecondary, fontSize = 11.sp)

                    Slider(
                        value = zoomPercent.toFloat(),
                        onValueChange = { viewModel.setGlobalZoomPercent(it.toInt()) },
                        valueRange = 50f..150f,
                        colors = SliderDefaults.colors(
                            thumbColor = DesktopXPrimary,
                            activeTrackColor = DesktopXPrimary,
                            inactiveTrackColor = DesktopXSurfaceBorder
                        ),
                        modifier = Modifier.testTag("zoom_slider")
                    )
                }
            }
        }

        // Security settings card parameters
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0B)),
            border = BorderStroke(1.dp, DesktopXSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SECURE DECRYPT", color = DesktopXAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Passcode Protection gate", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Triggers locks, pattern clear requests or admin passcode gate inputs", color = DesktopXTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isBiometricOn,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = DesktopXPrimary
                        ),
                        modifier = Modifier.testTag("passcode_switch")
                    )
                }

                Divider(color = DesktopXSurfaceBorder)

                Button(
                    onClick = {
                        viewModel.setPrivateMode(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1CEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, DesktopXAccent.copy(alpha = 0.4f))
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Reset metadata logs", tint = DesktopXAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Purge cached metadata & cookies", color = DesktopXAccent)
                }
            }
        }

        // Simulated workspace stats info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070709)),
            border = BorderStroke(1.dp, Color(0x11FFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("DesktopX Admin Terminal v2.4", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Developed exclusively for high-density Android desktop visualization models on sandboxed chromium platforms. Compatible with Figma Canvas, FlutterFlow, Google AI Studio, and VS Code compilers.", color = DesktopXTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
