package com.archery.tracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

private const val SYNC_WORK_NAME = "archery-sync"

fun enqueueSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}
