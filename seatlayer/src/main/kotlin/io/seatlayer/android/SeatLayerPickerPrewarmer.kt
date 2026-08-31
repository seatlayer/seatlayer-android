package io.seatlayer.android

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred

/**
 * Auditable result of process-wide Android WebView engine startup.
 *
 * The immutable pinned runtime page may be loaded to warm process/cache state,
 * but no picker session is created. The prewarmer never receives an event key,
 * public key, buyer token, or hold.
 */
public data class SeatLayerPickerPrewarmResult(
    public val totalUiThreadMillis: Long?,
    public val maxUiThreadTaskMillis: Long?,
    public val uiThreadBlockingLocationCount: Int,
    public val backgroundBlockingLocationCount: Int,
    public val rendererPageLoaded: Boolean = false,
) {
    public val engineStarted: Boolean get() = true
    public val pickerSessionCreated: Boolean get() = false
}

/**
 * Starts the process-wide Android WebView engine before a picker is likely.
 *
 * Call this from an application-scoped coroutine before constructing a
 * [SeatLayerView] or [SeatLayerPickerMapView]. Concurrent calls share one
 * startup operation. Cancelling a caller stops only that caller's wait because
 * Android WebView startup itself is process-wide and cannot be rolled back.
 * No Activity, WebView, event identity, credentials, or picker session is
 * retained. The temporary credential-free page is destroyed after load while
 * Android's ordinary process and HTTP cache remain warm.
 */
public object SeatLayerPickerPrewarmer {
    private val lock = Any()
    private var startup: CompletableDeferred<SeatLayerPickerPrewarmResult>? = null

    public suspend fun prewarm(context: Context): SeatLayerPickerPrewarmResult {
        val applicationContext = context.applicationContext
        val (flight, leader) = synchronized(lock) {
            startup?.let { it to false }
                ?: CompletableDeferred<SeatLayerPickerPrewarmResult>().also {
                    startup = it
                }.let { it to true }
        }
        if (leader) start(applicationContext, flight)
        return flight.await()
    }

    internal suspend fun awaitPendingStartup() {
        synchronized(lock) { startup }?.await()
    }

    private fun start(
        context: Context,
        flight: CompletableDeferred<SeatLayerPickerPrewarmResult>,
    ) {
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "SeatLayer-WebView-Startup").apply { isDaemon = true }
        }
        val config = WebViewStartUpConfig.Builder(executor).build()
        try {
            WebViewCompat.startUpWebView(
                context,
                config,
                object : WebViewOutcomeReceiver<
                    WebViewStartUpResult,
                    WebViewStartupException,
                    > {
                    override fun onResult(result: WebViewStartUpResult) {
                        warmPinnedRuntimePage(
                            context = context,
                            engine = SeatLayerPickerPrewarmResult(
                                totalUiThreadMillis = result.totalTimeInUiThreadMillis,
                                maxUiThreadTaskMillis = result.maxTimePerTaskInUiThreadMillis,
                                uiThreadBlockingLocationCount =
                                    result.uiThreadBlockingStartUpLocations.orEmpty().size,
                                backgroundBlockingLocationCount =
                                    result.nonUiThreadBlockingStartUpLocations.orEmpty().size,
                            ),
                            flight = flight,
                            executor = executor,
                        )
                    }

                    override fun onError(error: WebViewStartupException) {
                        completeFailure(flight, executor, error)
                    }
                },
            )
        } catch (error: Throwable) {
            completeFailure(flight, executor, error)
        }
    }

    private fun warmPinnedRuntimePage(
        context: Context,
        engine: SeatLayerPickerPrewarmResult,
        flight: CompletableDeferred<SeatLayerPickerPrewarmResult>,
        executor: ExecutorService,
    ) {
        Handler(Looper.getMainLooper()).post {
            if (flight.isCompleted) {
                executor.shutdown()
                return@post
            }
            val webView = runCatching { WebView(context) }.getOrElse {
                flight.complete(engine)
                executor.shutdown()
                return@post
            }
            var finished = false
            val handler = Handler(Looper.getMainLooper())
            fun complete(pageLoaded: Boolean) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(webView)
                webView.stopLoading()
                webView.destroy()
                flight.complete(
                    engine.copy(
                        totalUiThreadMillis = engine.totalUiThreadMillis,
                    ).let { result ->
                        SeatLayerPickerPrewarmResult(
                            totalUiThreadMillis = result.totalUiThreadMillis,
                            maxUiThreadTaskMillis = result.maxUiThreadTaskMillis,
                            uiThreadBlockingLocationCount =
                                result.uiThreadBlockingLocationCount,
                            backgroundBlockingLocationCount =
                                result.backgroundBlockingLocationCount,
                            rendererPageLoaded = pageLoaded,
                        )
                    },
                )
                executor.shutdown()
            }
            hardenPrewarmWebView(webView, ::complete)
            handler.postAtTime(
                { complete(false) },
                webView,
                android.os.SystemClock.uptimeMillis() + PAGE_WARM_TIMEOUT_MILLIS,
            )
            webView.loadUrl(SEATLAYER_MOBILE_PAGE_URL)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenPrewarmWebView(
        webView: WebView,
        complete: (Boolean) -> Unit,
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = !isAllowedSeatLayerPage(request.url.toString())

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isAllowedSeatLayerPage(url)) complete(false)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (isAllowedSeatLayerPage(url)) complete(true)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) complete(false)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                complete(false)
                return true
            }
        }
    }

    private fun completeFailure(
        flight: CompletableDeferred<SeatLayerPickerPrewarmResult>,
        executor: ExecutorService,
        error: Throwable,
    ) {
        flight.completeExceptionally(
            SeatLayerException.Transport("Android WebView startup failed.", error),
        )
        executor.shutdownNow()
    }

    private const val PAGE_WARM_TIMEOUT_MILLIS: Long = 12_000
}
