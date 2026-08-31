package com.tirup.app

import android.app.Application
import com.tirup.app.data.importer.StreamingGlucoseImporter
import com.tirup.app.data.local.AppDatabase
import com.tirup.app.data.repository.GlucoseRepositoryImpl
import com.tirup.app.data.repository.SettingsRepositoryImpl
import com.tirup.app.domain.repository.GlucoseRepository
import com.tirup.app.domain.repository.SettingsRepository

class TirupApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var glucoseRepository: GlucoseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var streamingImporter: StreamingGlucoseImporter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        glucoseRepository = GlucoseRepositoryImpl(database)
        settingsRepository = SettingsRepositoryImpl(this)
        streamingImporter = StreamingGlucoseImporter(this, database, glucoseRepository)
    }

    companion object {
        lateinit var instance: TirupApplication
            private set
    }
}
