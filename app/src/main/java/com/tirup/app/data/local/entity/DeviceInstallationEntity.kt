package com.tirup.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_installations")
data class DeviceInstallationEntity(
    @PrimaryKey
    val deviceType: String, // "SENSOR", "PUMP_SET", "LANCET"
    val installedAt: Long,
    val durationDays: Int,
    val lastUsedDurationDays: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
