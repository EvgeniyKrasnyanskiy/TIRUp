package com.tirup.app

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.tirup.app.data.importer.StreamingGlucoseImporter
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.receiver.DexdripBroadcastReceiver
import com.tirup.app.data.repository.GlucoseRepositoryImpl
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

class TirupApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var glucoseRepository: GlucoseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var streamingImporter: StreamingGlucoseImporter
        private set

    private val dynamicReceiver = DexdripBroadcastReceiver()

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        glucoseRepository = GlucoseRepositoryImpl(database)
        settingsRepository = SettingsRepositoryImpl(this)
        streamingImporter = StreamingGlucoseImporter(this, database)

        registerDynamicReceivers()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            com.tirup.app.data.backup.AutoBackupManager.maybeTriggerAutoBackup(this@TirupApplication, database, settingsRepository)
        }
    }

    private fun registerDynamicReceivers() {
        val filter = IntentFilter().apply {
            addAction("com.eveningoutpost.dexdrip.BgEstimate")
            addAction("com.eveningoutpost.dexdrip.BgEstimate.NEW_DATA")
            addAction("com.eveningoutpost.dexdrip.ACTION_NEW_BG_ESTIMATE")
            addAction("com.eveningoutpost.dexdrip.ACTION_NEW_BG")
            addAction("glucodata.Minute")
            addAction("de.michelinside.glucodatahandler.GLUCODATA")
            addAction("de.michelinside.glucodatahandler.MINUTE")
            addAction("info.nightscout.android.NEW_SGV")
            addAction("info.nightscout.android.EXTRA_DATA")
            addAction("com.juggluco.action.NEW_GLUCOSE")
            addAction("com.dexcom.g6.ACTION_NEW_BG")
            addAction("com.dexcom.cgm.ACTION_NEW_BG")
        }

        try {
            ContextCompat.registerReceiver(
                this,
                dynamicReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        lateinit var instance: TirupApplication
            private set
    }
}
