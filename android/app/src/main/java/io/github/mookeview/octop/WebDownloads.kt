package io.github.mookeview.octop

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.URLUtilCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

/** Only the configured Octop main frame may save bytes to a user-chosen content URI. */
class WebDownloads(private val activity: Activity, private val browser: WebView) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val script = activity.assets.open("downloads.js").bufferedReader().use { it.readText() }
    private var server: ServerAddress? = null
    private var registered = false
    private var pending: Transfer? = null
    private var savedDialog: AlertDialog? = null

    private class Transfer(
        val id: String, val filename: String, val mime: String, val size: Long,
        val reply: JavaScriptReplyProxy,
    ) {
        var uri: Uri? = null
        var sink: DownloadSink? = null
        var savedFilename: String = filename
    }

    fun setServer(address: ServerAddress?) {
        if (server?.origin == address?.origin) return
        cancel()
        if (registered && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(browser, BRIDGE)
        }
        registered = false
        server = address
        if (address == null || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(browser, BRIDGE, setOf(address.origin)) {
                _, message, origin, isMainFrame, reply ->
            if (isMainFrame && trustedPage() &&
                server?.allowsNavigation(origin.toString(), emptyList()) == true
            ) {
                receive(message.data.orEmpty(), reply)
            }
        }
        registered = true
    }

    private fun trustedPage() = server?.allowsNavigation(browser.url.orEmpty(), emptyList()) == true

    fun install() {
        if (registered && trustedPage()) browser.evaluateJavascript(script, null)
    }

    fun downloadUrl(url: String, contentDisposition: String?, mime: String?) {
        if (!registered || !trustedPage()) {
            toast(R.string.download_webview_unavailable)
            return
        }
        val allowed = server?.allowsNavigation(url, emptyList()) == true ||
            url.startsWith("blob:${server?.origin}/") || url.startsWith("data:")
        if (!allowed) {
            toast(R.string.download_failed)
            return
        }
        val filename = DownloadSink.filename(URLUtilCompat.guessFileName(url, contentDisposition, mime))
        browser.evaluateJavascript(
            "window.octopAndroidDownloads?.downloadUrl(${JSONObject.quote(url)}," +
                "${JSONObject.quote(filename)})", null
        )
    }

    private fun receive(raw: String, reply: JavaScriptReplyProxy) {
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = message.optString("id")
        if (message.optString("type") == "urlError") {
            toast(R.string.download_failed)
            return
        }
        if (message.optString("type") == "begin") {
            if (pending != null) {
                respond(reply, id, "error", activity.getString(R.string.download_busy))
                return
            }
            val size = message.optLong("size", -1)
            if (id.isBlank() || id.length > 128 || size < 0) {
                respond(reply, id, "error", activity.getString(R.string.download_failed))
                return
            }
            val filename = DownloadSink.filename(message.optString("name"))
            val suppliedMime = message.optString("mime").substringBefore(';').trim()
            val mime = suppliedMime.takeIf {
                it != "application/octet-stream" && it.matches(Regex("[\\w.+-]+/[\\w.+-]+"))
            }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
                ) ?: "application/octet-stream"
            val transfer = Transfer(id, filename, mime, size, reply)
            pending = transfer
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mime
                        putExtra(Intent.EXTRA_TITLE, filename)
                    }, REQUEST_CODE
                )
            } catch (_: ActivityNotFoundException) {
                pending = null
                respond(reply, id, "error", activity.getString(R.string.no_file_picker))
            }
            return
        }
        val transfer = pending?.takeIf { it.id == id } ?: return
        when (message.optString("type")) {
            "chunk" -> work(transfer) {
                val data = message.getString("data")
                require(data.length <= ((DownloadSink.CHUNK_BYTES + 2) / 3) * 4)
                val bytes = Base64.decode(data, Base64.NO_WRAP)
                requireNotNull(transfer.sink).write(bytes)
                "chunk"
            }
            "end" -> work(transfer) {
                requireNotNull(transfer.sink).finish()
                "saved"
            }
            "abort" -> cancel()
        }
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) return
        val transfer = pending ?: return
        if (resultCode != Activity.RESULT_OK) {
            pending = null
            respond(transfer.reply, transfer.id, "cancel")
            return
        }
        val uri = data?.data
        if (uri?.scheme != "content") {
            pending = null
            respond(transfer.reply, transfer.id, "error", activity.getString(R.string.download_failed))
            return
        }
        transfer.uri = uri
        work(transfer) {
            transfer.savedFilename = runCatching {
                activity.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: transfer.filename
            transfer.sink = DownloadSink(
                requireNotNull(activity.contentResolver.openOutputStream(uri, "w")), transfer.size
            )
            "ready"
        }
    }

    private fun work(transfer: Transfer, action: () -> String) {
        io.execute {
            try {
                val result = action()
                main.post {
                    if (pending === transfer) {
                        respond(transfer.reply, transfer.id, result)
                        if (result == "saved") {
                            pending = null
                            showSaved(transfer)
                        }
                    }
                }
            } catch (_: Exception) {
                discard(transfer)
                main.post {
                    if (pending === transfer) {
                        pending = null
                        respond(transfer.reply, transfer.id, "error", activity.getString(R.string.download_failed))
                    }
                }
            }
        }
    }

    private fun showSaved(transfer: Transfer) {
        if (activity.isFinishing || activity.isDestroyed) return
        savedDialog?.dismiss()
        savedDialog = AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.download_saved, transfer.savedFilename))
            .setPositiveButton(R.string.open_file) { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(transfer.uri, transfer.mime)
                        clipData = ClipData.newRawUri(transfer.savedFilename, transfer.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.no_file_viewer)
                }
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun respond(reply: JavaScriptReplyProxy, id: String, type: String, message: String? = null) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            reply.postMessage(JSONObject().put("id", id).put("type", type).put("message", message).toString())
        }
    }

    private fun toast(stringId: Int) = Toast.makeText(activity, stringId, Toast.LENGTH_LONG).show()

    private fun discard(transfer: Transfer) {
        runCatching { transfer.sink?.close() }
        transfer.uri?.let { uri ->
            runCatching { DocumentsContract.deleteDocument(activity.contentResolver, uri) }
        }
    }

    fun cancel() {
        val transfer = pending ?: return
        pending = null
        respond(transfer.reply, transfer.id, "error", activity.getString(R.string.download_failed))
        io.execute { discard(transfer) }
    }

    fun destroy() {
        cancel()
        savedDialog?.dismiss()
        if (registered && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(browser, BRIDGE)
        }
        io.shutdown()
    }

    private companion object {
        const val BRIDGE = "OctopDownloads"
        const val REQUEST_CODE = 2002
    }
}
