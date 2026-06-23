package com.localdocuments.app.data.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localdocuments.app.data.db.AppDatabase
import com.localdocuments.app.data.indexer.PdfIndexer

class PdfIndexWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val indexer = PdfIndexer(applicationContext)

        var processed = 0
        while (processed < 50) {
            val next = db.searchDao().getNextUnindexed() ?: break
            val uri = Uri.parse(next.uri)
            val success = indexer.indexPdf(uri, next.fileName)
            if (!success) {
                db.searchDao().insertOrUpdateStatus(
                    next.copy(isIndexed = true)
                )
            }
            processed++
        }

        return Result.success()
    }
}
