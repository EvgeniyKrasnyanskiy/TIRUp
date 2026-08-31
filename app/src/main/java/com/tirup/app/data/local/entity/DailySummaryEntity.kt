package com.tirup.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tirup.app.domain.model.DailySummary

@Entity(
    tableName = "daily_summaries",
    indices = [
        Index(value = ["date_timestamp"], unique = true)
    ]
)
data class DailySummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_timestamp")
    val dateTimestamp: Long, // Start of day 00:00

    @ColumnInfo(name = "mean")
    val mean: Double,

    @ColumnInfo(name = "tir")
    val tir: Double,

    @ColumnInfo(name = "ting")
    val ting: Double,

    @ColumnInfo(name = "tbr_very_low")
    val tbrVeryLow: Double,

    @ColumnInfo(name = "tbr_low")
    val tbrLow: Double,

    @ColumnInfo(name = "tar_high")
    val tarHigh: Double,

    @ColumnInfo(name = "tar_very_high")
    val tarVeryHigh: Double,

    @ColumnInfo(name = "sd")
    val sd: Double,

    @ColumnInfo(name = "cv")
    val cv: Double,

    @ColumnInfo(name = "count")
    val count: Int
) {
    fun toDomain(): DailySummary = DailySummary(
        dateTimestamp = dateTimestamp,
        meanMmol = mean,
        tirPercent = tir,
        tingPercent = ting,
        tbrVeryLowPercent = tbrVeryLow,
        tbrLowPercent = tbrLow,
        tarHighPercent = tarHigh,
        tarVeryHighPercent = tarVeryHigh,
        sdMmol = sd,
        cvPercent = cv,
        readingsCount = count
    )

    companion object {
        fun fromDomain(domain: DailySummary): DailySummaryEntity = DailySummaryEntity(
            dateTimestamp = domain.dateTimestamp,
            mean = domain.meanMmol,
            tir = domain.tirPercent,
            ting = domain.tingPercent,
            tbrVeryLow = domain.tbrVeryLowPercent,
            tbrLow = domain.tbrLowPercent,
            tarHigh = domain.tarHighPercent,
            tarVeryHigh = domain.tarVeryHighPercent,
            sd = domain.sdMmol,
            cv = domain.cvPercent,
            count = domain.readingsCount
        )
    }
}
