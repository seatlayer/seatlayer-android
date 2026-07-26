package io.seatlayer.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

public class SeatLayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    public val controller: SeatLayerController = SeatLayerController()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler(
            "/assets/",
            WebViewAssetLoader.AssetsPathHandler(context),
        )
        .build()

    private val webView = WebView(context)
    private val bridgeSupported: Boolean

    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(
            webView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        hardenWebView()

        bridgeSupported = WebViewFeature.isFeatureSupported(
            WebViewFeature.WEB_MESSAGE_LISTENER,
        )
        if (bridgeSupported) installBridge()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = OVER_SCROLL_NEVER
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
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = request.url.host != APP_HOST

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    controller.failTransport(
                        "SeatLayer page load failed: ${error.description}",
                    )
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                controller.failTransport(
                    "The Android WebView renderer exited.",
                )
                removeView(view)
                view.destroy()
                return true
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun installBridge() {
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_OBJECT,
            setOf(APP_ORIGIN),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (
                isMainFrame &&
                sourceOrigin == Uri.parse(APP_ORIGIN)
            ) {
                message.data?.let(controller::ingest)
            }
        }
    }

    public suspend fun load(configuration: SeatLayerConfiguration): ReadyInfo =
        withContext(Dispatchers.Main.immediate) {
            if (!bridgeSupported) {
                throw SeatLayerException.Transport(
                    "This Android System WebView does not support the secure " +
                        "WEB_MESSAGE_LISTENER bridge. Update Android System WebView.",
                )
            }
            controller.beginHandshake(WebViewChannel(webView), configuration)
            webView.loadUrl(PAGE_URL)
            controller.awaitReady()
        }

    /**
     * Permanently releases this view. Create a new [SeatLayerView] to load again.
     */
    public fun destroy() {
        controller.closeForReload()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        removeView(webView)
        webView.destroy()
    }

    private class WebViewChannel(
        private val webView: WebView,
    ) : BridgeChannel {
        override fun send(envelope: Envelope) {
            val wireLiteral = JsonPrimitive(envelope.encode()).toString()
            webView.post {
                webView.evaluateJavascript(
                    "window.__slBridge&&window.__slBridge.recv($wireLiteral);",
                    null,
                )
            }
        }
    }

    private companion object {
        const val APP_HOST = "appassets.androidplatform.net"
        const val APP_ORIGIN = "https://$APP_HOST"
        const val PAGE_URL = "$APP_ORIGIN/assets/index.html"
        const val BRIDGE_OBJECT = "seatlayerAndroid"
    }
}
