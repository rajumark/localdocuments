package com.localdocuments.app.data.db

data class PdfSearchResult(
    val uri: String,
    val pageNumber: Int,
    val pageText: String,
    val fileName: String
)
