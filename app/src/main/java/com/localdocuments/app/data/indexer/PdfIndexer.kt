package com.localdocuments.app.data.indexer

import android.content.Context
import android.net.Uri
import com.localdocuments.app.data.db.AppDatabase
import com.localdocuments.app.data.db.PdfIndexStatusEntity
import com.localdocuments.app.data.db.PdfPageFtsEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfIndexer(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).searchDao()

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun indexPdf(uri: Uri, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages

            dao.insertOrUpdateStatus(
                PdfIndexStatusEntity(
                    uri = uri.toString(),
                    fileName = fileName,
                    pageCount = totalPages
                )
            )

            for (pageIdx in 1..totalPages) {
                stripper.startPage = pageIdx
                stripper.endPage = pageIdx
                val extractedText = stripper.getText(document).trim()

                dao.insertPageText(
                    PdfPageFtsEntity(
                        uri = uri.toString(),
                        pageNumber = pageIdx,
                        pageText = extractedText.ifEmpty { "" }
                    )
                )

                dao.updateIndexedCount(uri.toString(), pageIdx)
            }

            dao.markIndexed(uri.toString(), totalPages)

            document.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeFromIndex(uri: Uri) = withContext(Dispatchers.IO) {
        dao.deletePagesForUri(uri.toString())
        dao.deleteStatus(uri.toString())
    }
}
