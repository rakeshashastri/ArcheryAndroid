package com.archery.tracker

import android.app.Application
import androidx.work.Configuration
import com.archery.tracker.di.AppContainer
import com.archery.tracker.sync.SyncWorkerFactory

class ArcheryApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory(container.repository))
            .build()
}
