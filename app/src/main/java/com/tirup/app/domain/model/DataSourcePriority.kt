package com.tirup.app.domain.model

enum class DataSourcePriority(val rank: Int) {
    LOCAL_XDRIP(4),      // Прямой broadcast от локального xDrip+ / GDH на этом же устройстве (наивысший)
    WIFI_LAN(3),         // Прямой опрос мастера по локальной сети Wi-Fi / Hotspot
    NIGHTSCOUT_CLOUD(2), // Опрос облачного сервера Nightscout через интернет
    BLE_BRIDGE(1);       // Прямой локальный радиопакет от аппаратного BLE радиомоста (резервный)

    companion object {
        fun isHigherOrEqual(p1: DataSourcePriority, p2: DataSourcePriority): Boolean {
            return p1.rank >= p2.rank
        }
    }
}
