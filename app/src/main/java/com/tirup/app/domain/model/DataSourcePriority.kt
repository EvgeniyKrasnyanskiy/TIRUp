package com.tirup.app.domain.model

enum class DataSourcePriority(val rank: Int) {
    LOCAL_XDRIP(4),      // Прямой broadcast от локального xDrip+ / GDH на этом же устройстве
    BLE_BRIDGE(3),       // Прямой локальный радиопакет от аппаратного BLE радиомоста
    WIFI_LAN(2),         // Прямой опрос мастера по локальной сети Wi-Fi / Hotspot
    NIGHTSCOUT_CLOUD(1); // Опрос облачного сервера Nightscout через интернет

    companion object {
        fun isHigherOrEqual(p1: DataSourcePriority, p2: DataSourcePriority): Boolean {
            return p1.rank >= p2.rank
        }
    }
}
