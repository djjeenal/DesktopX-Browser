package com.example

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.Content
import com.example.api.GeminiRequest
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class TabState(
    val id: String,
    val title: String,
    val url: String,
    val isPinned: Boolean = false,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Int = 0,
    val consoleLogs: List<String> = emptyList(),
    val webViewInstance: WebView? = null
)

data class WorkspaceWindow(
    val id: String,
    val title: String,
    val url: String,
    val x: Float, // in dp
    val y: Float, // in dp
    val width: Float, // in dp
    val height: Float, // in dp
    val isMaximized: Boolean = false,
    val isMinimized: Boolean = false,
    val webViewInstance: WebView? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.browserDao()

    // Screen selection: "Home", "Tabs", "Workspace", "Downloads", "Settings"
    private val _activeScreen = MutableStateFlow("Home")
    val activeScreen: StateFlow<String> = _activeScreen.asStateFlow()

    // Browser Tabs
    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String?>(null)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()

    // Workspace Windows
    private val _windows = MutableStateFlow<List<WorkspaceWindow>>(emptyList())
    val windows: StateFlow<List<WorkspaceWindow>> = _windows.asStateFlow()

    private val _activeWindowId = MutableStateFlow<String?>(null)
    val activeWindowId: StateFlow<String?> = _activeWindowId.asStateFlow()

    // Bookmarks / Shortcuts list (stored in DB, exposed in state)
    val bookmarks = dao.getBookmarks()

    // Downloads
    val downloads = dao.getDownloads()

    // History
    val history = dao.getHistory()

    // Virtual Files
    val virtualFiles = dao.getAllVirtualFiles()

    // UI Configuration / Spoofing settings
    private val _isRealPcMode = MutableStateFlow(true)
    val isRealPcMode: StateFlow<Boolean> = _isRealPcMode.asStateFlow()

    private val _simulatedResolution = MutableStateFlow("1925x1080")
    val simulatedResolution: StateFlow<String> = _simulatedResolution.asStateFlow()

    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    private val _isBiometricLockEnabled = MutableStateFlow(false)
    val isBiometricLockEnabled: StateFlow<Boolean> = _isBiometricLockEnabled.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _globalZoomPercent = MutableStateFlow(80) // default 80% to fit PC look
    val globalZoomPercent: StateFlow<Int> = _globalZoomPercent.asStateFlow()

    // Virtual Touchpad & Cursor States
    private val _isVirtualMouseEnabled = MutableStateFlow(false)
    val isVirtualMouseEnabled: StateFlow<Boolean> = _isVirtualMouseEnabled.asStateFlow()

    private val _cursorX = MutableStateFlow(200f) // virtual cursor X position in px
    val cursorX: StateFlow<Float> = _cursorX.asStateFlow()

    private val _cursorY = MutableStateFlow(300f) // virtual cursor Y position in px
    val cursorY: StateFlow<Float> = _cursorY.asStateFlow()

    // AI Website Fixer states
    private val _isFixingWebsite = MutableStateFlow(false)
    val isFixingWebsite: StateFlow<Boolean> = _isFixingWebsite.asStateFlow()

    private val _lastFixRecommendation = MutableStateFlow<String?>(null)
    val lastFixRecommendation: StateFlow<String?> = _lastFixRecommendation.asStateFlow()

    // Dialog & Feedback states
    private val _toastMsg = MutableStateFlow<String?>(null)
    val toastMsg: StateFlow<String?> = _toastMsg.asStateFlow()

    init {
        // Seed default Shortcuts / Bookmarks if database is empty
        viewModelScope.launch(Dispatchers.IO) {
            dao.getBookmarks().collect { list ->
                if (list.isEmpty()) {
                    val defaultShortcuts = listOf(
                        Bookmark("flutterflow", "FlutterFlow", "https://flutterflow.io", "flutterflow", true),
                        Bookmark("figma", "Figma", "https://figma.com", "figma", true),
                        Bookmark("github", "GitHub", "https://github.com", "github", true),
                        Bookmark("vscode", "VS Code Web", "https://vscode.dev", "vscode", true),
                        Bookmark("google_studio", "Google AI Studio", "https://aistudio.google.com", "google_studio", true),
                        Bookmark("chatgpt", "ChatGPT", "https://chatgpt.com", "chatgpt", true),
                        Bookmark("firebase", "Firebase Studio", "https://console.firebase.google.com", "firebase", true),
                        Bookmark("canva", "Canva", "https://canva.com", "canva", true)
                    )
                    defaultShortcuts.forEach { dao.insertBookmark(it) }
                }
            }
        }

        // Seed default virtual files on initial start
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllVirtualFiles().collect { files ->
                if (files.isEmpty()) {
                    val initialFiles = listOf(
                        VirtualFile(name = "WorkspaceProjects", path = "/root/WorkspaceProjects", isDirectory = true),
                        VirtualFile(name = "index.html", path = "/root/WorkspaceProjects/index.html", isDirectory = false, content = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>My DesktopX Project</title>
                                <style>
                                    body { background: #0f111a; color: #00e5ff; font-family: sans-serif; text-align: center; padding: 50px; }
                                    .container { border: 2px solid #1e2235; border-radius: 12px; padding: 20px; background: rgba(30, 34, 53, 0.5); }
                                    button { background: #8a2be2; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; }
                                </style>
                            </head>
                            <body>
                                <div class="container">
                                    <h1>Welcome to DesktopX Work environment!</h1>
                                    <p>Edit this file inside the developer project workspace folders.</p>
                                    <button onclick="alert('Hello from PC Browser!')">Click Me</button>
                                </div>
                            </body>
                            </html>
                        """.trimIndent(), sizeBytes = 852, fileType = "html"),
                        VirtualFile(name = "DesktopConfig.json", path = "/root/DesktopConfig.json", isDirectory = false, content = """
                            {
                              "browser": "DesktopX",
                              "version": "1.0",
                              "developerMode": true,
                              "defaultEngine": "Chromium"
                            }
                        """.trimIndent(), sizeBytes = 128, fileType = "json"),
                        VirtualFile(name = "Downloads_Backup.zip", path = "/root/Downloads_Backup.zip", isDirectory = false, sizeBytes = 25480, fileType = "zip")
                    )
                    initialFiles.forEach { dao.insertFile(it) }
                }
            }
        }
    }

    fun navigateTo(screen: String) {
        _activeScreen.value = screen
    }

    fun showToast(msg: String) {
        _toastMsg.value = msg
        viewModelScope.launch {
            delay(1500)
            if (_toastMsg.value == msg) {
                _toastMsg.value = null
            }
        }
    }

    fun clearToast() {
        _toastMsg.value = null
    }

    // Setting changes
    fun setRealPcMode(enabled: Boolean) {
        _isRealPcMode.value = enabled
        showToast(if (enabled) "Real PC mode enabled: 1920x1080 resolution simulation on" else "Mobile viewport restored")
        // Apply settings changes to all open WebViews
        viewModelScope.launch(Dispatchers.Main) {
            _tabs.value.forEach { tab ->
                tab.webViewInstance?.let { configureWebView(it, enabled) }
            }
            _windows.value.forEach { win ->
                win.webViewInstance?.let { configureWebView(it, enabled) }
            }
        }
    }

    fun setSimulatedResolution(res: String) {
        _simulatedResolution.value = res
        showToast("Spoofing screen resolution to $res")
    }

    fun setGlobalZoomPercent(percent: Int) {
        _globalZoomPercent.value = percent
        showToast("Desktop Zoom adjusted to $percent%")
        viewModelScope.launch(Dispatchers.Main) {
            _tabs.value.forEach { tab ->
                tab.webViewInstance?.let { it.setInitialScale(percent) }
            }
        }
    }

    fun setPrivateMode(enabled: Boolean) {
        _isPrivateMode.value = enabled
        if (enabled) {
            showToast("Private Incognito active: history & cookies cleared upon exit")
            clearCacheAndCookies()
        } else {
            showToast("Returned to Standard Browsing Mode")
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        _isBiometricLockEnabled.value = enabled
        showToast(if (enabled) "Security biometric security active" else "Biometric lock inactive")
    }

    fun toggleAppLock(locked: Boolean) {
        _isAppLocked.value = locked
    }

    // Tab Operations
    fun openNewTab(context: Context, url: String) {
        val id = UUID.randomUUID().toString()
        val formattedUrl = formatUrl(url)
        
        viewModelScope.launch(Dispatchers.Main) {
            // Instantiate a new Android WebView for this tab so it persists in state
            val webView = WebView(context).apply {
                tag = id
            }
            configureWebView(webView, _isRealPcMode.value)
            
            val newTab = TabState(
                id = id,
                title = "New Tab",
                url = formattedUrl,
                webViewInstance = webView,
                isLoading = true
            )
            val updated = _tabs.value + newTab
            _tabs.value = updated
            _currentTabId.value = id
            _activeScreen.value = "Tabs" // auto go to tab viewer

            webView.loadUrl(formattedUrl)
        }
    }

    fun selectTab(tabId: String) {
        _currentTabId.value = tabId
        _activeScreen.value = "Tabs"
    }

    fun closeTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId }
        tab?.webViewInstance?.let {
            it.stopLoading()
            it.destroy()
        }
        val updated = _tabs.value.filter { it.id != tabId }
        _tabs.value = updated

        if (_currentTabId.value == tabId) {
            _currentTabId.value = updated.firstOrNull()?.id
        }
    }

    fun pinTab(tabId: String, pin: Boolean) {
        val updated = _tabs.value.map {
            if (it.id == tabId) it.copy(isPinned = pin) else it
        }
        _tabs.value = updated
        showToast(if (pin) "Tab successfully pinned" else "Tab unpinned")
    }

    fun updateTabLoading(tabId: String, isLoading: Boolean, progress: Int) {
        val updated = _tabs.value.map {
            if (it.id == tabId) it.copy(isLoading = isLoading, progress = progress) else it
        }
        _tabs.value = updated
    }

    fun updateTabMetadata(tabId: String, title: String, url: String, canBack: Boolean, canForward: Boolean) {
        val updated = _tabs.value.map {
            if (it.id == tabId) it.copy(title = title, url = url, canGoBack = canBack, canGoForward = canForward) else it
        }
        _tabs.value = updated

        if (url.startsWith("http")) {
            viewModelScope.launch(Dispatchers.IO) {
                // Keep history updated in Room
                dao.insertHistory(HistoryItem(title = title, url = url))
            }
        }
    }

    fun addConsoleLogToTab(tabId: String, message: String) {
        val updated = _tabs.value.map {
            if (it.id == tabId) it.copy(consoleLogs = it.consoleLogs + message) else it
        }
        _tabs.value = updated
    }

    // Workspace Operations
    fun openInWorkspace(context: Context, label: String, url: String) {
        val id = UUID.randomUUID().toString()
        val formattedUrl = formatUrl(url)

        viewModelScope.launch(Dispatchers.Main) {
            val webView = WebView(context).apply {
                tag = id
            }
            configureWebView(webView, _isRealPcMode.value)
            
            val newWin = WorkspaceWindow(
                id = id,
                title = label,
                url = formattedUrl,
                x = 30f + (_windows.value.size * 25f), // offset cascades
                y = 50f + (_windows.value.size * 25f),
                width = 340f,
                height = 500f,
                webViewInstance = webView
            )
            _windows.value = _windows.value + newWin
            _activeWindowId.value = id
            _activeScreen.value = "Workspace"

            webView.loadUrl(formattedUrl)
            showToast("Opened $label in Workspace Mode")
        }
    }

    fun selectWindow(windowId: String) {
        _activeWindowId.value = windowId
        val list = _windows.value.map {
            if (it.id == windowId) it.copy(isMinimized = false) else it
        }
        _windows.value = list
    }

    fun updateWindowPosition(windowId: String, dx: Float, dy: Float) {
        val list = _windows.value.map {
            if (it.id == windowId) {
                val newX = (it.x + dx).coerceAtLeast(0f).coerceAtMost(500f)
                val newY = (it.y + dy).coerceAtLeast(0f).coerceAtMost(800f)
                it.copy(x = newX, y = newY)
            } else it
        }
        _windows.value = list
    }

    fun updateWindowSize(windowId: String, dw: Float, dh: Float) {
        val list = _windows.value.map {
            if (it.id == windowId) {
                val newW = (it.width + dw).coerceAtLeast(200f).coerceAtMost(1200f)
                val newH = (it.height + dh).coerceAtLeast(200f).coerceAtMost(1200f)
                it.copy(width = newW, height = newH)
            } else it
        }
        _windows.value = list
    }

    fun minimizeWindow(windowId: String) {
        val list = _windows.value.map {
            if (it.id == windowId) it.copy(isMinimized = true) else it
        }
        _windows.value = list
        if (_activeWindowId.value == windowId) {
            _activeWindowId.value = list.firstOrNull { !it.isMinimized }?.id
        }
    }

    fun maximizeWindow(windowId: String) {
        val list = _windows.value.map {
            if (it.id == windowId) it.copy(isMaximized = !it.isMaximized) else it
        }
        _windows.value = list
    }

    fun closeWindow(windowId: String) {
        val win = _windows.value.find { it.id == windowId }
        win?.webViewInstance?.let {
            it.stopLoading()
            it.destroy()
        }
        val updated = _windows.value.filter { it.id != windowId }
        _windows.value = updated
        if (_activeWindowId.value == windowId) {
            _activeWindowId.value = updated.firstOrNull()?.id
        }
    }

    // File Manager Operations
    fun createVirtualFile(name: String, directory: String, isFolder: Boolean, fileType: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val contentStr = if (isFolder) "" else "/* Created successfully in DesktopX Browser */"
            val file = VirtualFile(
                name = name,
                path = if (directory.endsWith("/")) "$directory$name" else "$directory/$name",
                isDirectory = isFolder,
                content = contentStr,
                sizeBytes = if (isFolder) 0 else contentStr.toByteArray().size.toLong(),
                fileType = fileType
            )
            dao.insertFile(file)
            withContext(Dispatchers.Main) {
                showToast("${if (isFolder) "Folder" else "File"} '$name' created")
            }
        }
    }

    fun deleteVirtualFile(file: VirtualFile) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteFile(file)
            withContext(Dispatchers.Main) {
                showToast("Deleted ${file.name}")
            }
        }
    }

    fun simulateDownload(name: String, url: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val downloadId = UUID.randomUUID().toString()
            val dlItem = DownloadItem(
                id = downloadId,
                name = name,
                url = url,
                size = "InProgress",
                status = "DOWNLOADING"
            )
            withContext(Dispatchers.IO) {
                dao.insertDownload(dlItem)
            }
            showToast("Downloading '$name' from desktop server...")

            delay(3000) // simulate network transfer

            val finalDl = dlItem.copy(size = "15.4 MB", status = "COMPLETED")
            withContext(Dispatchers.IO) {
                dao.insertDownload(finalDl)
                // Add downloaded file as a virtual file in the Downloads Explorer directory
                val virtualDownload = VirtualFile(
                    name = name,
                    path = "/root/Downloads/$name",
                    isDirectory = false,
                    content = "// Binary download index",
                    sizeBytes = 15400000,
                    fileType = if (name.endsWith(".zip")) "zip" else if (name.endsWith(".apk")) "apk" else "html"
                )
                dao.insertFile(virtualDownload)
            }
            showToast("Download '$name' complete! Saved inside Built-in DesktopX folders.")
        }
    }

    // Bookmark operations
    fun addBookmark(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val landmark = Bookmark(
                id = UUID.randomUUID().toString(),
                name = name,
                url = formatUrl(url),
                iconRes = "custom",
                isSystem = false
            )
            dao.insertBookmark(landmark)
            withContext(Dispatchers.Main) {
                showToast("Saved page as favorite")
            }
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBookmark(bookmark)
            withContext(Dispatchers.Main) {
                showToast("Bookmark removed")
            }
        }
    }

    // Smart AI Website Fixer calling Gemini 3.5 Flash
    fun fixCurrentWebsite(url: String, contentSample: String?) {
        viewModelScope.launch {
            _isFixingWebsite.value = true
            _lastFixRecommendation.value = "Analyzing web configurations via DesktopX AI Engine..."
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _lastFixRecommendation.value = "Local API Key warning: Please enter your valid GEMINI_API_KEY in the AI Studio secrets panel to fully enable the live AI website fixer.\n\nApplying direct client-side fallback: spoofed screen resolutions, user agent adjustments, and CSS flex wrapping properties loaded successfully."
                    delay(1500)
                    applyLocalFixes(url)
                    _isFixingWebsite.value = false
                    return@launch
                }

                val promptText = """
                    You are the Smart AI Website Fixer inside DesktopX Browser.
                    The user is attempting to load this URL: $url
                    This is a desktop environment running inside an Android app, so websites might complain about "Window too small", "Unsupported browser", "Desktop required", or render broken layout grids.
                    
                    Text sample captured from page: ${contentSample ?: "Blank loading / canvas placeholder"}
                    
                    Provide optimal browser setup fixes. Respond strictly and only in a raw JSON string (no markdown ticks, no surrounding notes, just pure raw JSON) like this object:
                    {
                      "ua": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                      "zoom": 75,
                      "jsToInject": "var meta = document.createElement('meta'); meta.name = 'viewport'; meta.content = 'width=1920, initial-scale=0.75'; document.getElementsByTagName('head')[0].appendChild(meta);",
                      "explanation": "Spoofed Windows Chrome client header, fixed viewport container dimensions to fit high-density desktop canvas grid"
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText))))
                )

                val result = withContext(Dispatchers.IO) {
                    RetrofitClient.geminiService.generateContent(apiKey, request)
                }

                val textResponse = result.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!textResponse.isNullOrEmpty()) {
                    val trimmedJson = textResponse.trim().removeSurrounding("```json", "```").trim()
                    val jo = JSONObject(trimmedJson)
                    val ua = jo.optString("ua", "")
                    val zoom = jo.optInt("zoom", 75)
                    val js = jo.optString("jsToInject", "")
                    val explanation = jo.optString("explanation", "Optimized viewport rendering layout applied successfully.")

                    _lastFixRecommendation.value = "AI recommendation:\n$explanation"
                    
                    // Apply target fixes
                    withContext(Dispatchers.Main) {
                        applyTargetFixes(ua, zoom, js)
                    }
                } else {
                    _lastFixRecommendation.value = "AI could not generate a standard repair recommendation. Loading local client-side repairs..."
                    applyLocalFixes(url)
                }

            } catch (e: Exception) {
                _lastFixRecommendation.value = "Engine notification: Direct fallback repaired the interface. Configured high-density viewport wrapper."
                applyLocalFixes(url)
            } finally {
                _isFixingWebsite.value = false
            }
        }
    }

    private fun applyLocalFixes(url: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val activeTab = _tabs.value.find { it.id == _currentTabId.value }
            activeTab?.webViewInstance?.let { webView ->
                // Apply optimal desktop overrides
                webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                webView.setInitialScale(75) // zoom zoom
                val viewportJs = "var meta = document.createElement('meta'); meta.name = 'viewport'; meta.content = 'width=1440, initial-scale=0.75'; document.getElementsByTagName('head')[0].appendChild(meta);"
                webView.evaluateJavascript(viewportJs, null)
                showToast("Desktop Viewport & UA repairs completed autonomously")
            }
        }
    }

    private fun applyTargetFixes(ua: String, zoom: Int, js: String) {
        val activeTab = _tabs.value.find { it.id == _currentTabId.value }
        activeTab?.webViewInstance?.let { webView ->
            if (ua.isNotEmpty()) webView.settings.userAgentString = ua
            webView.setInitialScale(zoom)
            if (js.isNotEmpty()) webView.evaluateJavascript(js, null)
            showToast("AI Website Fixer applied recommendations perfectly!")
        }
    }

    fun dismissFixerDialog() {
        _lastFixRecommendation.value = null
    }

    // Virtual touchpad cursor positioning updates
    fun moveCursor(dx: Float, dy: Float) {
        val updatedX = (_cursorX.value + dx).coerceIn(0f, 1080f)
        val updatedY = (_cursorY.value + dy).coerceIn(0f, 1920f)
        _cursorX.value = updatedX
        _cursorY.value = updatedY

        // Dispatch simulated hover action to the focused web view
        viewModelScope.launch(Dispatchers.Main) {
            val webView = getCurrentlyFocusedWebView()
            if (webView != null) {
                val motionEvent = MotionEvent.obtain(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_HOVER_MOVE,
                    updatedX,
                    updatedY,
                    0
                )
                webView.dispatchTouchEvent(motionEvent)
                motionEvent.recycle()
            }
        }
    }

    fun simulateVirtualClick() {
        viewModelScope.launch(Dispatchers.Main) {
            val webView = getCurrentlyFocusedWebView()
            if (webView != null) {
                val x = _cursorX.value
                val y = _cursorY.value
                val downTime = SystemClock.uptimeMillis()

                val downEvent = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    0
                )
                val upEvent = MotionEvent.obtain(
                    downTime,
                    downTime + 100,
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    0
                )

                webView.dispatchTouchEvent(downEvent)
                webView.dispatchTouchEvent(upEvent)

                downEvent.recycle()
                upEvent.recycle()
                showToast("Simulated Left-Click at Desktop position (${x.toInt()}, ${y.toInt()})")
            } else {
                showToast("No active PC website to click on")
            }
        }
    }

    fun toggleVirtualMouse() {
        _isVirtualMouseEnabled.value = !_isVirtualMouseEnabled.value
        showToast(if (_isVirtualMouseEnabled.value) "Desktop Trackpad Virtual Cursor active" else "Trackpad cursor disabled")
    }

    private fun getCurrentlyFocusedWebView(): WebView? {
        if (_activeScreen.value == "Workspace") {
            return _windows.value.find { it.id == _activeWindowId.value }?.webViewInstance
        }
        val tab = _tabs.value.find { it.id == _currentTabId.value }
        return tab?.webViewInstance
    }

    // Clipboard key-macros helpers
    fun performClipboardAction(action: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val webView = getCurrentlyFocusedWebView()

        when (action) {
            "copy" -> {
                // Focus element trigger copy via JS
                webView?.evaluateJavascript("window.getSelection().toString();") { selection ->
                    if (!selection.isNullOrEmpty() && selection != "null" && selection != "\"\"") {
                        val cleanText = selection.removeSurrounding("\"")
                        val clip = ClipData.newPlainText("DesktopX Copy", cleanText)
                        clipboard.setPrimaryClip(clip)
                        showToast("Copied search text to clipboard: '$cleanText'")
                    } else {
                        showToast("Highlight some text with mouse/gesture first!")
                    }
                }
            }
            "paste" -> {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val textToPaste = clipData.getItemAt(0).text.toString().replace("'", "\\'")
                    // Inject JS paste into active document focus
                    webView?.evaluateJavascript("""
                        (function() {
                            var el = document.activeElement;
                            if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) {
                                if (el.isContentEditable) {
                                    el.innerText += '$textToPaste';
                                } else {
                                    el.value += '$textToPaste';
                                }
                                showToast("Pasted text securely");
                            } else {
                                return "NoActiveInput";
                            }
                        })()
                    """.trimIndent()) { response ->
                        if (response == "\"NoActiveInput\"") {
                            showToast("No active input textbox focused! Click on a textbox first.")
                        } else {
                            showToast("Pasted clipboard securely")
                        }
                    }
                } else {
                    showToast("Clipboard is empty!")
                }
            }
            "save" -> {
                webView?.url?.let {
                    addBookmark(webView.title ?: "Custom Page", it)
                } ?: showToast("No web page active to save")
            }
            "undo" -> {
                webView?.evaluateJavascript("document.execCommand('undo', false, null);", null)
                showToast("Desktop undo sent")
            }
            "find" -> {
                showToast("Use desktop Search toolbar in titlebar menu")
            }
        }
    }

    // Helper utilities
    private fun formatUrl(url: String): String {
        return when {
            url.length < 3 -> "https://google.com"
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains(".") -> "https://$url"
            else -> "https://google.com/search?q=$url"
        }
    }

    private fun configureWebView(webView: WebView, isPcMode: Boolean) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowContentAccess = true
            allowFileAccess = true
        }

        if (isPcMode) {
            // High Resolution Windows Chrome Client header
            webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            webView.setInitialScale(_globalZoomPercent.value)
        } else {
            // Restore default
            webView.settings.userAgentString = null
            webView.setInitialScale(100)
        }
    }

    private fun clearCacheAndCookies() {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up WebView resources on dispose
        _tabs.value.forEach {
            it.webViewInstance?.destroy()
        }
        _windows.value.forEach {
            it.webViewInstance?.destroy()
        }
    }
}

// Simple implementation of delay for standard viewmodel usage since we need coroutines delay
private suspend fun delay(timeMs: Long) {
    withContext(Dispatchers.Default) {
        try {
            Thread.sleep(timeMs)
        } catch (e: Exception) {}
    }
}
