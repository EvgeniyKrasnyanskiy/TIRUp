package com.tirup.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tirup.app.domain.model.Treatment

@Entity(
    tableName = "treatments",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["uuid"])
    ]
)
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "insulin_units")
    val insulinUnits: Double? = null,

    @ColumnInfo(name = "carbs_grams")
    val carbsGrams: Double? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "source")
    val source: String = "XDRIP",

    @ColumnInfo(name = "uuid")
    val uuid: String? = null
) {
    fun toDomain(): Treatment = Treatment(
        id = id,
        timestamp = timestamp,
        insulinUnits = insulinUnits,
        carbsGrams = carbsGrams,
        notes = notes,
        source = source,
        uuid = uuid
    )

    companion object {
        fun fromDomain(domain: Treatment): TreatmentEntity = TreatmentEntity(
            id = domain.id,
            timestamp = domain.timestamp,
            insulinUnits = domain.insulinUnits,
            carbsGrams = domain.carbsGrams,
            notes = domain.notes,
            source = domain.source,
            uuid = domain.uuid
        )
    }
}
