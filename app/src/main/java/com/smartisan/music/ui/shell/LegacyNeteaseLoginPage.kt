package com.smartisan.music.ui.shell

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.smartisan.music.R
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONTokener

internal const val NeteaseLoginUrl = "https://music.163.com/"
internal const val NeteaseLoginDesktopUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val NeteaseQrPollIntervalMillis = 500L
private const val NeteaseQrDataUrlPrefix = "data:image/png;base64,"
private const val MaxNeteaseQrBase64Length = 512 * 1024

private val NeteaseAutoOpenLoginScript =
    """
    (function() {
        if (window.__smartisanLoginAutoOpenScheduled) return;
        window.__smartisanLoginAutoOpenScheduled = true;
        var attemptsRemaining = 60;

        function isVisible(element) {
            if (!element) return false;
            var style = window.getComputedStyle(element);
            var bounds = element.getBoundingClientRect();
            return style.display !== 'none' &&
                style.visibility !== 'hidden' &&
                bounds.width > 0 &&
                bounds.height > 0;
        }

        function findLoginButton() {
            var selectors = [
                '.m-tophead [data-action="login"]',
                '.m-tophead a.link',
                '[data-action="login"]'
            ];
            for (var i = 0; i < selectors.length; i++) {
                var preferred = document.querySelectorAll(selectors[i]);
                for (var j = 0; j < preferred.length; j++) {
                    var candidate = preferred[j];
                    if (isVisible(candidate) && candidate.textContent.trim() === '登录') {
                        return candidate;
                    }
                }
            }

            var candidates = document.querySelectorAll('a, button');
            for (var k = 0; k < candidates.length; k++) {
                var element = candidates[k];
                var bounds = element.getBoundingClientRect();
                if (bounds.top < 160 &&
                    isVisible(element) &&
                    element.textContent.trim() === '登录') {
                    return element;
                }
            }
            return null;
        }

        function openLogin() {
            var loginButton = findLoginButton();
            if (loginButton) {
                loginButton.click();
                return;
            }
            attemptsRemaining -= 1;
            if (attemptsRemaining > 0) window.setTimeout(openLogin, 250);
        }

        openLogin();
    })();
    """.trimIndent()

private val NeteaseExtractQrScript =
    """
    (function() {
        function isVisible(element) {
            var style = window.getComputedStyle(element);
            var bounds = element.getBoundingClientRect();
            return style.display !== 'none' &&
                style.visibility !== 'hidden' &&
                Number(style.opacity) > 0 &&
                bounds.width >= 100 &&
                bounds.height >= 100;
        }

        function hasQrContext(element) {
            var current = element;
            for (var depth = 0; current && depth < 8; depth++) {
                if ((current.textContent || '').indexOf('扫码登录') >= 0) return true;
                current = current.parentElement;
            }
            return false;
        }

        var canvases = document.querySelectorAll('canvas');
        var candidates = [];
        for (var i = 0; i < canvases.length; i++) {
            var canvas = canvases[i];
            if (!isVisible(canvas) || !hasQrContext(canvas)) continue;
            var bounds = canvas.getBoundingClientRect();
            if (Math.abs(bounds.width - bounds.height) > 12 ||
                bounds.width > 420 ||
                canvas.width <= 0 ||
                canvas.height <= 0) {
                continue;
            }
            var identity = (canvas.id + ' ' + canvas.className).toLowerCase();
            var score = identity.indexOf('qr') >= 0 ? 100 : 0;
            score += Math.max(0, 30 - Math.abs(bounds.width - 200) / 5);
            score += Math.max(
                0,
                20 - Math.abs(bounds.left + bounds.width / 2 - innerWidth / 2) / 20
            );
            candidates.push({ canvas: canvas, score: score });
        }
        candidates.sort(function(left, right) { return right.score - left.score; });
        if (candidates.length === 0) return null;
        try {
            return candidates[0].canvas.toDataURL('image/png');
        } catch (error) {
            return null;
        }
    })();
    """.trimIndent()

internal fun isAllowedNeteaseLoginUrl(url: String): Boolean {
    if (url == "about:blank") return true
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.US) ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        (uri.port == -1 || uri.port == 443) &&
        (host == "163.com" || host.endsWith(".163.com"))
}

internal fun containsNeteaseAuthenticationCookie(cookieHeader: String?): Boolean {
    return cookieHeader.orEmpty()
        .split(';')
        .asSequence()
        .map(String::trim)
        .mapNotNull { pair ->
            pair.substringBefore('=', missingDelimiterValue = "").takeIf(String::isNotBlank)
        }
        .any { name -> name == "MUSIC_U" || name == "MUSIC_A" }
}

internal fun shouldAutoOpenNeteaseLogin(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        (uri.port == -1 || uri.port == 443) &&
        uri.host.equals("music.163.com", ignoreCase = true)
}

internal fun extractNeteaseQrBase64(evaluatedJavascript: String?): String? {
    if (evaluatedJavascript.isNullOrBlank()) return null
    val dataUrl = runCatching {
        JSONTokener(evaluatedJavascript).nextValue() as? String
    }.getOrNull() ?: return null
    if (!dataUrl.startsWith(NeteaseQrDataUrlPrefix)) return null
    return dataUrl.removePrefix(NeteaseQrDataUrlPrefix)
        .takeIf { encoded -> encoded.isNotEmpty() && encoded.length <= MaxNeteaseQrBase64Length }
}

@Composable
internal fun LegacyNeteaseLoginDialog(
    visible: Boolean,
    onClose: () -> Unit,
    onLoginCookie: suspend (String) -> Boolean,
) {
    val context = LocalContext.current
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnLoginCookie by rememberUpdatedState(onLoginCookie)

    DisposableEffect(visible) {
        if (!visible) return@DisposableEffect onDispose { }
        val dialog = LegacyNeteaseQrLoginDialog(
            context = context,
            onClose = latestOnClose,
            onLoginCookie = latestOnLoginCookie,
        )
        dialog.show()
        onDispose { dialog.dispose() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class LegacyNeteaseQrLoginDialog(
    private val context: Context,
    private val onClose: () -> Unit,
    private val onLoginCookie: suspend (String) -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dialog = Dialog(context, R.style.MmsDialogTheme)
    private val qrImage = ImageView(context)
    private val progress = ProgressBar(context)
    private val statusText = TextView(context)
    private val webView = WebView(context)
    private var disposed = false
    private var submitting = false
    private var qrRequestInFlight = false
    private var lastQrBase64: String? = null
    private var lastAttemptedCookie: String? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (disposed) return
            checkForAuthenticatedSession(webView.url.orEmpty())
            requestQrImage()
            webView.postDelayed(this, NeteaseQrPollIntervalMillis)
        }
    }

    init {
        configureDialog()
        configureWebView()
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.54f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                context.resources.getDimensionPixelSize(R.dimen.revone_global_dialog_content_width),
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        clearNeteaseWebCookies {
            webView.post {
                if (!disposed) {
                    webView.loadUrl(NeteaseLoginUrl)
                    webView.removeCallbacks(pollRunnable)
                    webView.post(pollRunnable)
                }
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        scope.cancel()
        webView.removeCallbacks(pollRunnable)
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
        dialog.setOnCancelListener(null)
        if (dialog.isShowing) dialog.dismiss()
        clearNeteaseWebCookies()
    }

    private fun configureDialog() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener { onClose() }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.revone_global_dialog_shape_background)
        }
        root.addView(
            TextView(context).apply {
                setText(R.string.netease_login)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.status_bar_color_dialog))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dpPx(24), context.dpPx(20), context.dpPx(24), context.dpPx(18))
            setBackgroundResource(R.drawable.revone_global_dialog_message_background)
        }
        val qrContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
        }
        qrContainer.addView(
            webView.apply {
                alpha = 0f
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        qrContainer.addView(
            qrImage.apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(context.dpPx(8), context.dpPx(8), context.dpPx(8), context.dpPx(8))
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        qrContainer.addView(
            progress,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        content.addView(
            qrContainer,
            LinearLayout.LayoutParams(context.dpPx(200), context.dpPx(200)),
        )
        content.addView(
            statusText.apply {
                setText(R.string.netease_qr_loading)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.text_secondary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = context.dpPx(14)
            },
        )
        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            Button(context).apply {
                setText(android.R.string.cancel)
                isAllCaps = false
                gravity = Gravity.CENTER
                setLegacyTextColor(R.drawable.btn_text_color_selector)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.revone_dialog_button_bg_selector)
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )
        dialog.setContentView(root)
    }

    private fun configureWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = NeteaseLoginDesktopUserAgent
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.safeBrowsingEnabled = true
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = request.isForMainFrame &&
                !isAllowedNeteaseLoginUrl(request.url.toString())

            @Deprecated("Deprecated in Android")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                !isAllowedNeteaseLoginUrl(url)

            override fun onPageFinished(view: WebView, url: String) {
                checkForAuthenticatedSession(url)
                if (!submitting && shouldAutoOpenNeteaseLogin(url)) {
                    view.evaluateJavascript(NeteaseAutoOpenLoginScript, null)
                }
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String,
                isReload: Boolean,
            ) {
                checkForAuthenticatedSession(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame || disposed) return
                progress.visibility = View.GONE
                showRetryStatus {
                    progress.visibility = View.VISIBLE
                    webView.reload()
                }
            }
        }
    }

    private fun requestQrImage() {
        if (qrRequestInFlight || disposed || submitting) return
        qrRequestInFlight = true
        webView.evaluateJavascript(NeteaseExtractQrScript) { evaluatedJavascript ->
            qrRequestInFlight = false
            if (disposed) return@evaluateJavascript
            val encodedQr = extractNeteaseQrBase64(evaluatedJavascript) ?: return@evaluateJavascript
            if (encodedQr == lastQrBase64) return@evaluateJavascript
            val bitmap = decodeQrBitmap(encodedQr) ?: return@evaluateJavascript
            lastQrBase64 = encodedQr
            qrImage.setImageBitmap(bitmap)
            progress.visibility = View.GONE
            clearStatusAction()
            statusText.setText(R.string.netease_qr_scan_prompt)
        }
    }

    private fun checkForAuthenticatedSession(url: String) {
        if (disposed || submitting || !isAllowedNeteaseLoginUrl(url)) return
        val cookieManager = CookieManager.getInstance()
        val cookieHeader = sequenceOf(
            cookieManager.getCookie(NeteaseLoginUrl),
            cookieManager.getCookie(url),
        ).firstOrNull(::containsNeteaseAuthenticationCookie) ?: return
        if (cookieHeader == lastAttemptedCookie) return
        lastAttemptedCookie = cookieHeader
        submitting = true
        clearStatusAction()
        statusText.setText(R.string.netease_login_checking)
        scope.launch {
            val loginSucceeded = onLoginCookie(cookieHeader)
            if (disposed) return@launch
            if (loginSucceeded) {
                clearNeteaseWebCookies {
                    webView.post {
                        if (!disposed) {
                            Toast.makeText(
                                context,
                                R.string.netease_login_success,
                                Toast.LENGTH_SHORT,
                            ).show()
                            onClose()
                        }
                    }
                }
            } else {
                submitting = false
                showRetryStatus {
                    lastAttemptedCookie = null
                    checkForAuthenticatedSession(webView.url.orEmpty())
                }
                Toast.makeText(
                    context,
                    R.string.netease_login_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun showRetryStatus(onRetry: () -> Unit) {
        statusText.setText(R.string.netease_login_retry)
        statusText.isClickable = true
        statusText.isFocusable = true
        statusText.setOnClickListener {
            if (disposed || submitting) return@setOnClickListener
            clearStatusAction()
            statusText.setText(R.string.netease_login_checking)
            onRetry()
        }
    }

    private fun clearStatusAction() {
        statusText.isClickable = false
        statusText.isFocusable = false
        statusText.setOnClickListener(null)
    }

    private fun decodeQrBitmap(encodedQr: String): Bitmap? {
        val bytes = runCatching { Base64.decode(encodedQr, Base64.DEFAULT) }.getOrNull()
            ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val hasValidDimensions = bitmap.width in 100..1024 &&
            bitmap.height in 100..1024 &&
            kotlin.math.abs(bitmap.width - bitmap.height) <= 12
        if (!hasValidDimensions) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }
}

internal fun clearNeteaseWebCookies(onCleared: (() -> Unit)? = null) {
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        onCleared?.invoke()
    }
}

private fun TextView.setLegacyTextColor(selectorResource: Int) {
    setTextColor(context.getColorStateList(selectorResource))
}
