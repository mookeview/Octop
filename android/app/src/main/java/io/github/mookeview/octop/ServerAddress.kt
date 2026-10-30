package io.github.mookeview.octop

import java.net.URI
import java.util.Locale

/** A configured Octop root, including the optional reverse-proxy subpath. */
data class ServerAddress private constructor(val url: String, val origin: String) {
    fun allowsNavigation(target: String, authenticationOrigins: Collection<String>): Boolean {
        val parsed = runCatching { URI(target) }.getOrNull() ?: return false
        val candidate = httpOrigin(parsed) ?: return false
        return candidate == origin || candidate in authenticationOrigins
    }

    companion object {
        fun parse(input: String): ServerAddress? {
            val value = input.trim()
            if (value.isEmpty()) return null
            val qualified = if ("://" in value) value else "https://$value"
            val uri = runCatching { URI(qualified) }.getOrNull() ?: return null
            val origin = httpOrigin(uri) ?: return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val path = uri.rawPath.orEmpty().trimEnd('/')
            if (path.split('/').any { it == "." || it == ".." }) return null
            return ServerAddress("$origin$path/", origin)
        }

        private fun httpOrigin(uri: URI): String? {
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (host.isBlank() || uri.rawUserInfo != null || uri.port !in -1..65535 || uri.port == 0) {
                return null
            }
            val defaultPort = if (scheme == "https") 443 else 80
            val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
            return "$scheme://$host$port"
        }
    }
}
