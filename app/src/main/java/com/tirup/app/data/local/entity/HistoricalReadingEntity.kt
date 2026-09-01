package com.tirup.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tirup.app.domain.model.GlucoseReading

@Entity(
    tableName = "historical_readings",
    indices = [Index(value = ["timestamp"], unique = true)]
)
data class HistoricalReadingEntity(
    @PrimaryKey
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "value_mmol")
    val valueMmol: Double,

    @ColumnInfo(name = "trend_arrow")
    val trendArrow: String = "→"
) {
    fun toDomain(): GlucoseReading = GlucoseReading(
        timestamp = timestamp,
        valueMmol = valueMmol,
        trendArrow = trendArrow
    )

    companion object {
        fun fromDomain(reading: GlucoseReading): HistoricalReadingEntity = HistoricalReadingEntity(
            timestamp = reading.timestamp,
            valueMmol = reading.valueMmol,
            trendArrow = reading.trendArrow ?: "→"
        )
    }
}
