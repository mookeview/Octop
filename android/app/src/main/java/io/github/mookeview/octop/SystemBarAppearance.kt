package io.github.mookeview.octop

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.webkit.WebView
import org.json.JSONObject

/** Reads public page appearance without exposing a native JavaScript interface. */
class SystemBarAppearance(
    private val activity: Activity,
    private val root: View,
    private val browser: WebView,
) {
    private var resumed = false
    private var pageVisible = false
    private var generation = 0
    private var color = activity.getColor(R.color.background)
    private val poll = Runnable { readPageTheme() }

    init {
        applyColor(color)
    }

    fun showNative() {
        pageVisible = false
        invalidateRead()
        applyColor(activity.getColor(R.color.background))
    }

    fun showPage() {
        pageVisible = true
        invalidateRead()
        if (resumed) readPageTheme()
    }

    fun resume() {
        resumed = true
        applyColor(color)
        if (pageVisible) readPageTheme()
    }

    fun pause() {
        resumed = false
        invalidateRead()
    }

    private fun invalidateRead() {
        generation++
        browser.removeCallbacks(poll)
    }

    private fun readPageTheme() {
        if (!resumed || !pageVisible) return
        val requestGeneration = generation
        browser.evaluateJavascript(READ_THEME) { result ->
            if (!resumed || !pageVisible || requestGeneration != generation) return@evaluateJavascript
            val value = runCatching { JSONObject(result).optString("color") }.getOrNull()
            if (value != null && HEX_COLOR.matches(value)) {
                val next = Color.parseColor(value)
                if (next != color) applyColor(next)
            }
            browser.removeCallbacks(poll)
            browser.postDelayed(poll, 500)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyColor(next: Int) {
        color = next
        // Android 15 makes the bars transparent; our inset padding supplies their backdrop.
        root.setBackgroundColor(next)
        browser.setBackgroundColor(next)
        activity.window.apply {
            statusBarColor = next
            navigationBarColor = next
            if (Build.VERSION.SDK_INT >= 29) {
                isStatusBarContrastEnforced = false
                isNavigationBarContrastEnforced = false
            }
            val lightBackground = Color.luminance(next) > 0.5f
            if (Build.VERSION.SDK_INT >= 30) {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                insetsController?.setSystemBarsAppearance(if (lightBackground) mask else 0, mask)
            } else {
                val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                decorView.systemUiVisibility = (decorView.systemUiVisibility and mask.inv()) or
                    (if (lightBackground) mask else 0)
            }
        }
    }

    private companion object {
        val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
        // Prefer the actual CSS surface: the dashboard's PWA meta color can differ from it.
        val READ_THEME = """
            (() => {
                const root = document.documentElement;
                if (!root || !['light', 'dark'].includes(root.getAttribute('data-theme'))) return null;
                const surface = getComputedStyle(root).getPropertyValue('--fn-bg-primary').trim();
                const meta = [...document.querySelectorAll('meta[name="theme-color"]')]
                    .find(el => !el.media || matchMedia(el.media).matches);
                const color = surface || meta?.content || '';
                return /^#[0-9a-f]{6}$/i.test(color) ? {color} : null;
            })()
        """.trimIndent()
    }
}
