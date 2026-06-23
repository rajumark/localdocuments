package com.localdocuments.app.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "pdf_pages_fts")
data class PdfPageFtsEntity(
    val uri: String,
    val pageNumber: Int,
    val pageText: String
)
