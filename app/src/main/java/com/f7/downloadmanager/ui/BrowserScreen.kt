package com.f7.downloadmanager.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.downloadmanager.F7ViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.engine.Http
import com.f7.downloadmanager.ui.theme.LocalSkin
import com.f7.downloadmanager.util.Links

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0 Safari/537.36"

private sealed interface BrowserError {
    data object Network : BrowserError
    data object Ssl : BrowserError
    data class Http(val code: Int) : BrowserError
    data object Blocked : BrowserError
    data object Render : BrowserError
}

private data class Shortcut(val title: String, val url: String)

private val quickLinks = listOf(
    Shortcut("DuckDuckGo", "https://duckduckgo.com/"),
    Shortcut("Wikipedia", "https://www.wikipedia.org/"),
    Shortcut("GitHub", "https://github.com/"),
    Shortcut("Archive.org", "https://archive.org/"),
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    vm: F7ViewModel,
    homeUrl: String,
    initialUrl: String,
    onUrlChange: (String) -> Unit,
    onDownload: (url: String, headers: Map<String, String>, mime: String?) -> Unit,
    onMessage: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val desktop by vm.settings.browserDesktop.collectAsStateWithLifecycle()
    val historyRaw by vm.settings.browserHistory.collectAsStateWithLifecycle()
    val bookmarksRaw by vm.settings.browserBookmarks.collectAsStateWithLifecycle()
    val history = remember(historyRaw) { parseEntries(historyRaw) }
    val bookmarks = remember(bookmarksRaw) { parseEntries(bookmarksRaw) }

    var urlText by rememberSaveable { mutableStateOf(initialUrl.ifBlank { "" }) }
    var currentUrl by rememberSaveable { mutableStateOf(initialUrl) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<BrowserError?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showStart by rememberSaveable { mutableStateOf(initialUrl.isBlank()) }
    var webGeneration by remember { mutableIntStateOf(0) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    fun updateNav(view: WebView?) {
        canBack = view?.canGoBack() == true
        canForward = view?.canGoForward() == true
    }

    fun go(input: String) {
        val target = Links.normalize(input)
        if (target.isBlank()) return
        error = null
        loading = true
        progress = 0f
        showStart = false
        currentUrl = target
        urlText = target
        onUrlChange(target)
        webView.value?.loadUrl(target)
    }

    fun goHome() {
        error = null
        showStart = true
        currentUrl = ""
        urlText = ""
        pageTitle = ""
        onUrlChange("")
        webView.value?.loadUrl("about:blank")
    }

    BackHandler(enabled = canBack && !showStart) { webView.value?.goBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.browser_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                shape = CircleShape,
                leadingIcon = {
                    Icon(
                        if (urlText.startsWith("https://")) F7Icons.Lock else F7Icons.Search,
                        null,
                        tint = if (urlText.startsWith("https://")) skin.success else skin.muted,
                        modifier = Modifier.size(17.dp),
                    )
                },
                trailingIcon = {
                    if (urlText.isNotEmpty()) {
                        IconCircleButton(
                            icon = F7Icons.Close,
                            contentDescription = "Clear address",
                            onClick = { urlText = "" },
                            size = 30.dp,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                            tint = skin.muted,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(urlText) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = skin.primary,
                    unfocusedBorderColor = skin.hairline,
                    cursorColor = skin.primary,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconCircleButton(
                icon = if (loading) F7Icons.Close else F7Icons.Download,
                contentDescription = androidx.compose.ui.res.stringResource(
                    if (loading) R.string.browser_stop else R.string.browser_open_link,
                ),
                onClick = {
                    if (loading) {
                        webView.value?.stopLoading()
                        loading = false
                    } else {
                        go(urlText.ifBlank { homeUrl })
                    }
                },
                background = skin.primary,
                tint = skin.onPrimary,
                size = 46.dp,
            )
        }

        if (loading && progress < 0.99f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(skin.track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceAtLeast(0.02f))
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(skin.primary),
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(skin.card),
        ) {
            key(webGeneration) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            setGeolocationEnabled(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            mediaPlaybackRequiresUserGesture = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            userAgentString = if (desktop) DESKTOP_UA else Http.USER_AGENT
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: return false
                                return when (scheme) {
                                    "http", "https" -> false
                                    "javascript" -> true
                                    "tel", "mailto", "sms", "smsto", "market" -> {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, uri)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                        true
                                    }
                                    "intent" -> {
                                        val launched = runCatching {
                                            val parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                            context.startActivity(parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }.isSuccess
                                        if (!launched) onMessage(context.getString(R.string.browser_blocked))
                                        true
                                    }
                                    else -> {
                                        onMessage(context.getString(R.string.browser_blocked))
                                        true
                                    }
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                progress = 0f
                                error = null
                                if (url != null && url != "about:blank") {
                                    urlText = url
                                    currentUrl = url
                                    onUrlChange(url)
                                }
                                updateNav(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                progress = 1f
                                updateNav(view)
                                if (url != null && url != "about:blank") {
                                    val title = view?.title.orEmpty()
                                    pageTitle = title
                                    vm.addBrowserHistory(url, title)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame != true) return
                                loading = false
                                progress = 1f
                                error = BrowserError.Network
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                if (request?.isForMainFrame != true) return
                                val code = errorResponse?.statusCode ?: 0
                                if (code >= 400) {
                                    loading = false
                                    error = BrowserError.Http(code)
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                // A renderer kill (low memory on MIUI, OTA update) would otherwise
                                // take the whole app down. Swap in a fresh WebView and explain.
                                view?.let { dead ->
                                    (dead.parent as? ViewGroup)?.removeView(dead)
                                    runCatching { dead.destroy() }
                                }
                                if (webView.value === view) webView.value = null
                                loading = false
                                progress = 1f
                                error = BrowserError.Render
                                webGeneration++
                                return true
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, sslError: SslError?) {
                                // Strict TLS: a failed certificate is never bypassed. Only a failure
                                // on the page itself is fatal, though - a broken image or analytics
                                // host must not blank out a page that loaded fine.
                                handler?.cancel()
                                val failedHost = runCatching { Uri.parse(sslError?.url.orEmpty()).host }.getOrNull()
                                val mainHost = runCatching { Uri.parse(view?.url.orEmpty()).host }.getOrNull()
                                if (failedHost == null || (mainHost != null && failedHost != mainHost)) return
                                loading = false
                                progress = 1f
                                error = BrowserError.Ssl
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                                if (newProgress >= 100) loading = false
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) pageTitle = title
                            }
                        }
                        setDownloadListener { url, userAgent, _, mimeType, _ ->
                            if (url.startsWith("blob:")) {
                                onMessage(context.getString(R.string.browser_blob_blocked))
                            } else {
                                val headers = HashMap<String, String>()
                                if (!userAgent.isNullOrBlank()) headers["User-Agent"] = userAgent
                                CookieManager.getInstance().getCookie(url)?.let { headers["Cookie"] = it }
                                onDownload(url, headers, mimeType)
                            }
                        }
                        if (showStart) loadUrl("about:blank") else loadUrl(currentUrl.ifBlank { initialUrl })
                        webView.value = this
                    }
                },
                update = { view ->
                    if (view.url?.contains("about:blank") == true && currentUrl.isNotBlank() && !showStart) {
                        view.loadUrl(currentUrl)
                    }
                },
            )
            }

            if (showStart) {
                StartPage(
                    history = history,
                    bookmarks = bookmarks,
                    onOpen = { go(it) },
                    onClearHistory = { vm.clearBrowserHistory() },
                )
            }

            val currentError = error
            if (currentError != null) {
                ErrorPage(
                    error = currentError,
                    onRetry = { go(currentUrl.ifBlank { urlText.ifBlank { homeUrl } }) },
                    onExternal = {
                        val target = currentUrl.ifBlank { homeUrl }
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = F7Icons.Back,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_back),
                onClick = { webView.value?.goBack() },
                background = if (canBack) skin.cardAlt else skin.card,
                tint = if (canBack) skin.ink else skin.muted,
            )
            Spacer(Modifier.width(10.dp))
            IconCircleButton(
                icon = F7Icons.Forward,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_forward),
                onClick = { webView.value?.goForward() },
                background = if (canForward) skin.cardAlt else skin.card,
                tint = if (canForward) skin.ink else skin.muted,
            )
            Spacer(Modifier.width(10.dp))
            IconCircleButton(
                icon = F7Icons.Retry,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_reload),
                onClick = {
                    error = null
                    if (currentUrl.isBlank()) go(homeUrl) else webView.value?.reload()
                },
            )
            Spacer(Modifier.width(10.dp))
            IconCircleButton(
                icon = F7Icons.Browser,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.browser_home),
                onClick = { goHome() },
            )
            Spacer(Modifier.width(10.dp))
            Box {
                IconCircleButton(
                    icon = F7Icons.More,
                    contentDescription = "Browser menu",
                    onClick = { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = skin.card,
                ) {
                    DropdownMenuItem(
                        text = { Text(androidx.compose.ui.res.stringResource(R.string.browser_desktop), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuOpen = false
                            vm.setBrowserDesktop(!desktop)
                            if (currentUrl.isNotBlank()) webView.value?.reload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(androidx.compose.ui.res.stringResource(R.string.browser_bookmark), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuOpen = false
                            vm.toggleBrowserBookmark(currentUrl, pageTitle)
                            onMessage(context.getString(R.string.browser_bookmarked))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(androidx.compose.ui.res.stringResource(R.string.browser_copy_url), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(currentUrl.ifBlank { homeUrl }))
                            onMessage(context.getString(R.string.toast_copied))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(androidx.compose.ui.res.stringResource(R.string.browser_external), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            menuOpen = false
                            val target = currentUrl.ifBlank { homeUrl }
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
            }
        }
        Text(
            androidx.compose.ui.res.stringResource(R.string.browser_https_note),
            style = MaterialTheme.typography.labelSmall,
            color = skin.muted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            textAlign = TextAlign.Center,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.value?.stopLoading()
                webView.value?.destroy()
            }
            webView.value = null
        }
    }
}

@Composable
private fun StartPage(
    history: List<Shortcut>,
    bookmarks: List<Shortcut>,
    onOpen: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(
        Modifier
            .fillMaxSize()
            .background(skin.card)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.browser_start_title),
            style = MaterialTheme.typography.headlineSmall,
            color = skin.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.browser_start_body),
            style = MaterialTheme.typography.bodySmall,
            color = skin.muted,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickLinks.take(2).forEach { link ->
                ShortcutChip(link, Modifier.weight(1f)) { onOpen(link.url) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickLinks.drop(2).forEach { link ->
                ShortcutChip(link, Modifier.weight(1f)) { onOpen(link.url) }
            }
        }

        if (bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                androidx.compose.ui.res.stringResource(R.string.browser_bookmarks),
                style = MaterialTheme.typography.labelLarge,
                color = skin.ink,
            )
            Spacer(Modifier.height(6.dp))
            bookmarks.take(6).forEach { entry ->
                RecentRow(entry, onOpen)
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.browser_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = skin.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.browser_clear_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = skin.muted,
                    modifier = Modifier
                        .clickable(onClick = onClearHistory)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            history.take(8).forEach { entry ->
                RecentRow(entry, onOpen)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.browser_capture_body),
            style = MaterialTheme.typography.bodySmall,
            color = skin.muted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShortcutChip(link: Shortcut, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(skin.cardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(F7Icons.Browser, null, tint = skin.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            link.title,
            style = MaterialTheme.typography.labelMedium,
            color = skin.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentRow(entry: Shortcut, onOpen: (String) -> Unit) {
    val skin = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(entry.url) }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = skin.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.url.substringAfter("://").substringBefore('/'),
                style = MaterialTheme.typography.labelSmall,
                color = skin.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(F7Icons.Forward, null, tint = skin.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ErrorPage(error: BrowserError, onRetry: () -> Unit, onExternal: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxSize()
            .background(skin.card),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(skin.danger.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(F7Icons.Shield, null, tint = skin.danger, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                androidx.compose.ui.res.stringResource(R.string.browser_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = skin.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (error) {
                    BrowserError.Network -> androidx.compose.ui.res.stringResource(R.string.browser_error_network)
                    BrowserError.Ssl -> androidx.compose.ui.res.stringResource(R.string.browser_error_ssl)
                    is BrowserError.Http -> androidx.compose.ui.res.stringResource(R.string.browser_error_http, error.code)
                    BrowserError.Blocked -> androidx.compose.ui.res.stringResource(R.string.browser_blocked)
                    BrowserError.Render -> androidx.compose.ui.res.stringResource(R.string.browser_error_render)
                },
                style = MaterialTheme.typography.bodySmall,
                color = skin.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.browser_retry),
                onClick = onRetry,
                icon = F7Icons.Retry,
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = androidx.compose.ui.res.stringResource(R.string.browser_external),
                onClick = onExternal,
                icon = F7Icons.External,
            )
        }
    }
}

private fun parseEntries(raw: String?): List<Shortcut> =
    raw.orEmpty().lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val url = line.substringBefore('\t')
        val title = line.substringAfter('\t', url).ifBlank { url }
        if (url.isBlank()) null else Shortcut(title, url)
    }
