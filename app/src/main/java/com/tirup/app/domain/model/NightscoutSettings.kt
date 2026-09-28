package com.tirup.app.domain.model

data class NightscoutSettings(
    val isEnabled: Boolean = false,
    val serverUrl: String = "",
    val apiSecret: String = "",
    val requireXdripConfirmation: Boolean = true
) {
    val isValidUrl: Boolean
        get() = serverUrl.isNotBlank() && (serverUrl.startsWith("http://", ignoreCase = true) || serverUrl.startsWith("https://", ignoreCase = true))

    fun getCleanBaseUrl(): String {
        return serverUrl.trim().removeSuffix("/")
    }
}
