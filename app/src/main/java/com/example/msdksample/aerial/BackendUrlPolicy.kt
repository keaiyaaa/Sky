package com.example.msdksample.aerial

import java.net.URI

internal object BackendUrlPolicy {

    fun isAllowed(baseUrl: String, isDebug: Boolean): Boolean {
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return false

        if (uri.scheme.equals("https", ignoreCase = true)) return true

        val isLoopbackHost = host.equals("127.0.0.1", ignoreCase = true)
                || host.equals("localhost", ignoreCase = true)
        return isDebug && uri.scheme.equals("http", ignoreCase = true) && isLoopbackHost
    }
}
