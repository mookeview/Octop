package io.github.mookeview.octop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("connection", MODE_PRIVATE) }
    private val authenticationOrigins by lazy {
        resources.getStringArray(R.array.authentication_origins).toList()
    }
    private lateinit var browser: WebView
    private lateinit var addressInput: EditText
    private lateinit var addressError: TextView
    private lateinit var progress: ProgressBar
    private var server: ServerAddress? = null
    private var pageFailed = false
    private var clearHistoryOnCommit = false
    private var lastRequestedUrl: String? = null
    private lateinit var fileChooser: WebFileChooser
    private lateinit var systemBars: SystemBarAppearance
    private val pageTimeout = Runnable {
        browser.stopLoading()
        showError(getString(R.string.connection_timed_out))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets()
        browser = findViewById(R.id.browser)
        addressInput = findViewById(R.id.server_address)
        addressError = findViewById(R.id.address_error)
        progress = findViewById(R.id.page_progress)
        fileChooser = WebFileChooser(this)
        systemBars = SystemBarAppearance(this, findViewById(R.id.root), browser)
        configureBrowser()

        findViewById<View>(R.id.connect).setOnClickListener { connect() }
        findViewById<View>(R.id.retry).setOnClickListener { retryPage() }
        findViewById<View>(R.id.error_change_server).setOnClickListener { showConnection() }
        addressInput.setOnEditorActionListener { _, _, _ -> connect(); true }

        server = preferences.getString("server_address", null)?.let(ServerAddress::parse)
        addressInput.setText(server?.url ?: getString(R.string.default_server_address))
        val debugAddress = if (BuildConfig.DEBUG) intent.getStringExtra("server_url") else null
        if (debugAddress != null) {
            addressInput.setText(debugAddress)
            connect()
        } else if (server != null) {
            showBrowser()
            val state = savedInstanceState?.getBundle("browser")
            if (state == null || browser.restoreState(state) == null) loadPage(server!!.url)
        } else {
            showConnection()
        }

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { navigateBack() }
        }
    }

    private fun applyInsets() {
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val edges = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                        WindowInsets.Type.ime()
                )
                view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser() {
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            // File input receives only content URIs chosen in the Android document picker.
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString OctopAndroid/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: android.webkit.ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                if (server?.allowsNavigation(view.url.orEmpty(), emptyList()) != true) {
                    callback.onReceiveValue(null)
                    return true
                }
                return fileChooser.show(callback, params)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress == 100 || pageFailed) View.GONE else View.VISIBLE
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val target = request.url.toString()
                if (server?.allowsNavigation(target, authenticationOrigins) == true) return false
                if (request.hasGesture()) openExternal(request.url)
                else showError(getString(R.string.external_redirect, request.url.host.orEmpty()))
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                fileChooser.cancel()
                pageFailed = false
                lastRequestedUrl = url
                findViewById<View>(R.id.connection_failure).visibility = View.GONE
                progress.visibility = View.VISIBLE
                startPageTimeout()
                if (findViewById<View>(R.id.browser_screen).visibility == View.VISIBLE) {
                    systemBars.showPage()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                browser.removeCallbacks(pageTimeout)
                progress.visibility = View.GONE
                if (!pageFailed && findViewById<View>(R.id.browser_screen).visibility == View.VISIBLE) {
                    systemBars.showPage()
                }
                if (clearHistoryOnCommit && !pageFailed) {
                    browser.clearHistory()
                    clearHistoryOnCommit = false
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showError(getString(R.string.certificate_error))
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) showError(getString(R.string.connection_failed))
            }

            override fun onReceivedHttpError(
                view: WebView, request: WebResourceRequest, response: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    showError(getString(R.string.http_error, response.statusCode))
                }
            }
        }
    }

    private fun connect() {
        val parsed = ServerAddress.parse(addressInput.text.toString())
        if (parsed == null || (!BuildConfig.DEBUG && parsed.url.startsWith("http://"))) {
            addressError.text = getString(R.string.invalid_address)
            addressError.visibility = View.VISIBLE
            return
        }
        server = parsed
        addressError.visibility = View.GONE
        preferences.edit().putString("server_address", parsed.url).apply()
        addressInput.setText(parsed.url)
        browser.stopLoading()
        browser.clearHistory()
        clearHistoryOnCommit = true
        showBrowser()
        loadPage(parsed.url)
        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        keyboard.hideSoftInputFromWindow(addressInput.windowToken, 0)
    }

    private fun showConnection() {
        systemBars.showNative()
        fileChooser.cancel()
        browser.removeCallbacks(pageTimeout)
        browser.stopLoading()
        findViewById<View>(R.id.connection_screen).visibility = View.VISIBLE
        findViewById<View>(R.id.browser_screen).visibility = View.GONE
    }

    private fun showBrowser() {
        findViewById<View>(R.id.connection_screen).visibility = View.GONE
        findViewById<View>(R.id.browser_screen).visibility = View.VISIBLE
        if (pageFailed) systemBars.showNative() else systemBars.showPage()
    }

    private fun showError(message: String) {
        systemBars.showNative()
        browser.removeCallbacks(pageTimeout)
        pageFailed = true
        progress.visibility = View.GONE
        findViewById<TextView>(R.id.connection_failure_message).text = message
        findViewById<View>(R.id.connection_failure).visibility = View.VISIBLE
    }

    private fun startPageTimeout() {
        browser.removeCallbacks(pageTimeout)
        browser.postDelayed(pageTimeout, 30_000)
    }

    private fun loadPage(url: String) {
        lastRequestedUrl = url
        pageFailed = false
        findViewById<View>(R.id.connection_failure).visibility = View.GONE
        progress.visibility = View.VISIBLE
        startPageTimeout()
        browser.loadUrl(url)
    }

    private fun retryPage() {
        val target = if (pageFailed) lastRequestedUrl else browser.url
        (target?.takeIf { server?.allowsNavigation(it, authenticationOrigins) == true }
            ?: server?.url)?.let(::loadPage)
    }

    @Deprecated("Platform callback retained for the document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileChooser.onResult(requestCode, resultCode, data)
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme !in listOf("http", "https", "mailto", "tel")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_external_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateBack() {
        if (findViewById<View>(R.id.connection_screen).visibility == View.VISIBLE && server != null) {
            showBrowser()
        } else if (browser.canGoBack()) {
            browser.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    @Deprecated("Used on Android 12 and earlier")
    override fun onBackPressed() = navigateBack()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = Bundle()
        browser.saveState(state)
        outState.putBundle("browser", state)
    }

    override fun onPause() {
        systemBars.pause()
        browser.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        browser.onResume()
        systemBars.resume()
    }

    override fun onDestroy() {
        systemBars.pause()
        fileChooser.cancel()
        browser.removeCallbacks(pageTimeout)
        browser.stopLoading()
        browser.destroy()
        super.onDestroy()
    }
}
