package com.localdocuments.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.localdocuments.app.data.worker.PdfIndexWorker

class LocalDocumentsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleIndexing()
    }

    private fun scheduleIndexing() {
        val work = OneTimeWorkRequestBuilder<PdfIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "pdf_indexing",
                ExistingWorkPolicy.KEEP,
                work
            )
    }
}
