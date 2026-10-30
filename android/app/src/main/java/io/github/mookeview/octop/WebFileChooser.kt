package io.github.mookeview.octop

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.Toast

/** Grants the web page access only to documents the user selects. No storage permission. */
class WebFileChooser(private val activity: Activity) {
    private var pending: ValueCallback<Array<Uri>>? = null

    @Suppress("DEPRECATION")
    fun show(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        cancel()
        if (params.mode != FileChooserParams.MODE_OPEN &&
            params.mode != FileChooserParams.MODE_OPEN_MULTIPLE
        ) {
            callback.onReceiveValue(null)
            return true
        }
        pending = callback
        val intent = params.createIntent().apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            cancel()
            Toast.makeText(activity, R.string.no_file_picker, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) return
        val callback = pending ?: return
        pending = null
        callback.onReceiveValue(selectedContentUris(resultCode, data))
    }

    fun cancel() {
        pending?.onReceiveValue(null)
        pending = null
    }

    /**
     * OEM document providers do not all populate the same Intent field for a
     * multi-select result. WebView's helper only reads one of those fields, so
     * collect all supported forms before handing the URIs back to the page.
     */
    @Suppress("DEPRECATION")
    private fun selectedContentUris(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null

        val uris = buildList {
            data.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
            data.data?.let(::add)
            data.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
        }
            .filter { it.scheme == "content" }
            .distinct()

        return uris.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private companion object {
        const val REQUEST_CODE = 2001
    }
}
