package com.archery.tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.archery.tracker.data.repository.ArcheryRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: ArcheryRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A full two-way sync: push local changes up, then pull the server's state down and merge,
        // so a device sees sessions created on another device (e.g. the web client).
        return if (repository.sync().isSuccess) Result.success() else Result.retry()
    }
}

class SyncWorkerFactory(private val repository: ArcheryRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) {
            SyncWorker(appContext, workerParameters, repository)
        } else {
            null
        }
}
