package com.tirup.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tirup.app.domain.calculator.MeterReading

@Entity(
    tableName = "historical_meter_readings",
    indices = [Index(value = ["timestamp"])]
)
data class HistoricalMeterReadingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "value_mmol")
    val valueMmol: Double
) {
    fun toDomain(): MeterReading = MeterReading(
        timestamp = timestamp,
        valueMmol = valueMmol
    )

    companion object {
        fun fromDomain(reading: MeterReading): HistoricalMeterReadingEntity = HistoricalMeterReadingEntity(
            timestamp = reading.timestamp,
            valueMmol = reading.valueMmol
        )
    }
}
