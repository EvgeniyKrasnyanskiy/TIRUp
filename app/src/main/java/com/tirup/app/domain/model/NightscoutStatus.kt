package com.tirup.app.domain.model

data class NightscoutStatus(
    val isEnabled: Boolean = false,
    val masterBattery: Int? = null,
    val lastBatteryTimestamp: Long = 0L,
    val lastSuccessTimestamp: Long = 0L,
    val lastCheckTimestamp: Long = 0L,
    val uploaderDevice: String? = null,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)
