package com.tirup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tirup.app.data.local.entity.DeviceInstallationEntity

@Dao
interface DeviceInstallationDao {
    @Query("SELECT * FROM device_installations WHERE deviceType = :deviceType LIMIT 1")
    suspend fun getDeviceInstallation(deviceType: String): DeviceInstallationEntity?

    @Query("SELECT * FROM device_installations WHERE deviceType = :deviceType LIMIT 1")
    fun getDeviceInstallationSync(deviceType: String): DeviceInstallationEntity?

    @Query("SELECT * FROM device_installations")
    suspend fun getAllDeviceInstallations(): List<DeviceInstallationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DeviceInstallationEntity)
}
