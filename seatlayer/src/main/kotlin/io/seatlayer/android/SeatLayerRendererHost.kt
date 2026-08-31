package io.seatlayer.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import kotlinx.serialization.json.JsonPrimitive

/** One hardened renderer host shared by raw and picker surfaces. */
internal class SeatLayerRendererHost(
    context: Context,
    private val parent: ViewGroup,
    private val requireHostedAdapter: Boolean,
    private val onMessage: (String) -> Unit,
    private val onTransportFailure: (String) -> Unit,
) : BridgeChannel {
    private val webView = WebView(context)
    private var destroyed = false

    private val supportsMessageListener = WebViewFeature.isFeatureSupported(
        WebViewFeature.WEB_MESSAGE_LISTENER,
    )
    private val supportsDocumentStart = WebViewFeature.isFeatureSupported(
        WebViewFeature.DOCUMENT_START_SCRIPT,
    )

    val isSupported: Boolean
        get() = supportsMessageListener && (!requireHostedAdapter || supportsDocumentStart)

    init {
        parent.addView(
            webView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        hardenWebView()
        if (supportsMessageListener) installMessageListener()
        if (supportsDocumentStart) installHostedAdapter()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isAllowedSeatLayerPage(request.url.toString())

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!destroyed && !isAllowedSeatLayerPage(url)) {
                    view.stopLoading()
                    onTransportFailure("Blocked unexpected SeatLayer navigation.")
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    onTransportFailure(
                        "SeatLayer page load failed: ${error.description}",
                    )
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                onTransportFailure("The Android WebView renderer exited.")
                parent.removeView(view)
                view.destroy()
                destroyed = true
                return true
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun installMessageListener() {
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_OBJECT,
            setOf(SEATLAYER_MOBILE_ORIGIN),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (
                isMainFrame &&
                isAllowedSeatLayerOrigin(sourceOrigin.toString())
            ) {
                message.data?.let(onMessage)
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun installHostedAdapter() {
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            HOSTED_BRIDGE_ADAPTER,
            setOf(SEATLAYER_MOBILE_ORIGIN),
        )
    }

    fun load() {
        check(!destroyed) { "SeatLayer renderer host was destroyed" }
        webView.loadUrl(SEATLAYER_MOBILE_PAGE_URL)
    }

    override fun send(envelope: Envelope) {
        if (destroyed) throw SeatLayerException.Destroyed()
        val wireLiteral = JsonPrimitive(envelope.encode()).toString()
        webView.post {
            if (!destroyed) {
                webView.evaluateJavascript(
                    "window.__slBridge&&window.__slBridge.recv($wireLiteral);",
                    null,
                )
            }
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        parent.removeView(webView)
        webView.destroy()
    }

    private companion object {
        const val BRIDGE_OBJECT = "seatlayerAndroid"
        val HOSTED_BRIDGE_ADAPTER: String = """
            (() => {
              if (window.SeatLayerNative &&
                  typeof window.SeatLayerNative.post === "function") return;
              Object.defineProperty(window, "SeatLayerNative", {
                configurable: true,
                value: {
                  post(value) {
                    const transport = window.seatlayerAndroid;
                    if (!transport || typeof transport.postMessage !== "function") {
                      throw new Error("SeatLayer Android bridge unavailable");
                    }
                    transport.postMessage(String(value));
                  }
                }
              });
            })();
        """.trimIndent()
    }
}

/** Main-frame navigation is pinned to one immutable versioned document. */
internal fun isAllowedSeatLayerPage(rawUrl: String?): Boolean =
    rawUrl == SEATLAYER_MOBILE_PAGE_URL

/** Accepts equivalent omitted and explicit HTTPS default ports, nothing else. */
internal fun isAllowedSeatLayerOrigin(rawOrigin: String): Boolean = runCatching {
    val expected = URI(SEATLAYER_MOBILE_ORIGIN)
    val actual = URI(rawOrigin)
    val actualPort = if (actual.port == -1) 443 else actual.port
    val expectedPort = if (expected.port == -1) 443 else expected.port
    actual.scheme.equals(expected.scheme, ignoreCase = true) &&
        actual.host.equals(expected.host, ignoreCase = true) &&
        actualPort == expectedPort &&
        actual.rawUserInfo == null &&
        actual.rawPath.orEmpty().isEmpty() &&
        actual.rawQuery == null &&
        actual.rawFragment == null
}.getOrDefault(false)
