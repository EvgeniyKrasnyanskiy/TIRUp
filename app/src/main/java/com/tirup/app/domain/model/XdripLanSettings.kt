package com.tirup.app.domain.model

import java.security.MessageDigest

data class XdripLanSettings(
    val isEnabled: Boolean = false,
    val isAutoDiscovery: Boolean = true,
    val masterHost: String = "",
    val port: Int = 17580,
    val apiSecret: String = "",
    val pollIntervalSeconds: Int = 60
) {
    val isValidHost: Boolean
        get() = masterHost.isNotBlank() && port in 1..65535

    val isConfigured: Boolean
        get() = (isAutoDiscovery || isValidHost) && port in 1..65535

    val cleanHost: String
        get() {
            var host = masterHost.trim()
            if (host.startsWith("http://", ignoreCase = true)) {
                host = host.substring(7)
            } else if (host.startsWith("https://", ignoreCase = true)) {
                host = host.substring(8)
            }
            return host.trimEnd('/')
        }

    val baseUrl: String
        get() {
            val host = cleanHost
            return if (host.contains(":")) {
                "http://$host"
            } else {
                "http://$host:$port"
            }
        }

    val hashedSecret: String?
        get() {
            if (apiSecret.isBlank()) return null
            return try {
                val bytes = apiSecret.trim().toByteArray(Charsets.UTF_8)
                val md = MessageDigest.getInstance("SHA-1")
                val digest = md.digest(bytes)
                digest.joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                null
            }
        }
}
