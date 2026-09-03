package com.tirup.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tirup.app.domain.model.GlucoseReading

@Entity(
    tableName = "glucose_readings",
    indices = [
        Index(value = ["timestamp"], unique = true)
    ]
)
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "value_mmol")
    val valueMmol: Double,

    @ColumnInfo(name = "trend_arrow")
    val trendArrow: String? = null,

    @ColumnInfo(name = "iob", defaultValue = "NULL")
    val iob: Double? = null,

    @ColumnInfo(name = "cob", defaultValue = "NULL")
    val cob: Double? = null
) {
    fun toDomain(): GlucoseReading = GlucoseReading(
        id = id,
        timestamp = timestamp,
        valueMmol = valueMmol,
        trendArrow = trendArrow,
        iob = iob,
        cob = cob
    )

    companion object {
        fun fromDomain(domain: GlucoseReading): GlucoseReadingEntity = GlucoseReadingEntity(
            id = domain.id,
            timestamp = domain.timestamp,
            valueMmol = domain.valueMmol,
            trendArrow = domain.trendArrow,
            iob = domain.iob,
            cob = domain.cob
        )
    }
}
