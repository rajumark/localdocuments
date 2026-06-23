package com.localdocuments.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_index_status")
data class PdfIndexStatusEntity(
    @PrimaryKey val uri: String,
    val fileName: String,
    val pageCount: Int = 0,
    val indexedPageCount: Int = 0,
    val isIndexed: Boolean = false,
    val lastIndexed: Long = 0
)
