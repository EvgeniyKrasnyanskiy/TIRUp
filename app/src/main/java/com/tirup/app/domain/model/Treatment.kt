package com.tirup.app.domain.model

data class Treatment(
    val id: Long = 0L,
    val timestamp: Long,
    val insulinUnits: Double? = null,
    val carbsGrams: Double? = null,
    val notes: String? = null,
    val source: String = "XDRIP"
) {
    val hasInsulin: Boolean get() = insulinUnits != null && insulinUnits > 0.0
    val hasCarbs: Boolean get() = carbsGrams != null && carbsGrams > 0.0
    val isCombo: Boolean get() = hasInsulin && hasCarbs
}
