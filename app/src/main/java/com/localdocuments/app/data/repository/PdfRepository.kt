package com.localdocuments.app.data.repository

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import com.localdocuments.app.data.model.PdfDocument

class PdfRepository(private val contentResolver: ContentResolver) {

    fun getPdfDocuments(): List<PdfDocument> {
        val documents = mutableListOf<PdfDocument>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val titleCol = it.getColumnIndex(MediaStore.Files.FileColumns.TITLE)
            val sizeCol = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

            val baseUri = MediaStore.Files.getContentUri("external")

            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getLong(idCol) else 0L
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "Untitled" else "Untitled"
                val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                val date = if (dateCol >= 0) it.getLong(dateCol) * 1000L else 0L

                val uri = Uri.withAppendedPath(baseUri, id.toString())

                documents.add(
                    PdfDocument(
                        uri = uri,
                        name = title,
                        size = size,
                        dateModified = date
                    )
                )
            }
        }
        return documents
    }

    fun getPageCount(uri: Uri): Int {
        return try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return 0
            val renderer = PdfRenderer(fd)
            val count = renderer.pageCount
            renderer.close()
            fd.close()
            count
        } catch (_: Exception) {
            0
        }
    }

    fun renderPage(uri: Uri, pageIndex: Int, width: Int, height: Int): Bitmap? {
        return try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fd)
            try {
                if (pageIndex >= renderer.pageCount) return null
                val page = renderer.openPage(pageIndex)
                try {
                    val ratio = page.width.toFloat() / page.height.toFloat()
                    val w = minOf(width, (height * ratio).toInt())
                    val h = minOf(height, (width / ratio).toInt())
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
                fd.close()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun renderThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        return renderPage(uri, 0, width, height)
    }
}
