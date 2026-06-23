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
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
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
            val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (it.moveToNext()) {
                val id = if (idCol >= 0) it.getLong(idCol) else 0L
                val title = if (titleCol >= 0) it.getString(titleCol) ?: "Untitled" else "Untitled"
                val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                val date = if (dateCol >= 0) it.getLong(dateCol) * 1000L else 0L
                val dataPath = if (dataCol >= 0) it.getString(dataCol) else null

                val uri = if (dataPath != null) {
                    Uri.parse(dataPath)
                } else {
                    Uri.withAppendedPath(
                        MediaStore.Files.getContentUri("external"),
                        id.toString()
                    )
                }

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

    fun renderThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return null
            val renderer = PdfRenderer(fd)
            try {
                if (renderer.pageCount == 0) return null
                val page = renderer.openPage(0)
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

    fun renderThumbnailFromPath(dataPath: String, width: Int, height: Int): Bitmap? {
        return try {
            val file = java.io.File(dataPath)
            if (!file.exists()) return null
            val fd = android.os.ParcelFileDescriptor.open(
                file, android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            val renderer = PdfRenderer(fd)
            try {
                if (renderer.pageCount == 0) return null
                val page = renderer.openPage(0)
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
}
