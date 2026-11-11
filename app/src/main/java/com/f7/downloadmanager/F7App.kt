package com.f7.downloadmanager

import android.app.Application
import android.content.Context
import com.f7.downloadmanager.data.DownloadStore
import com.f7.downloadmanager.data.Settings
import com.f7.downloadmanager.data.Storage
import com.f7.downloadmanager.engine.Constraints
import com.f7.downloadmanager.engine.DownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow

class F7App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Process-wide singletons shared by the UI, the service and the receivers. */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = Settings(appContext)
    val store = DownloadStore(appContext)
    val storage = Storage(appContext, settings)
    val constraints = Constraints(appContext)
    val engine = DownloadEngine(appContext, store, settings, storage, constraints)
    /** Link handed over from Android Share; consumed by the add-download sheet. */
    val pendingShare = MutableStateFlow<String?>(null)
}

val Context.graph: AppGraph get() = (applicationContext as F7App).graph
