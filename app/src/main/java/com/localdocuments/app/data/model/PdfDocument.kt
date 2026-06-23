package com.localdocuments.app.data.model

import android.net.Uri

data class PdfDocument(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long
) {
    val formattedSize: String
        get() {
            val kb = size / 1024
            val mb = kb / 1024
            return if (mb > 0) "${mb} MB" else "${kb} KB"
        }
}

data class PdfGroup(
    val label: String,
    val documents: List<PdfDocument>
)
