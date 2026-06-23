package com.localdocuments.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfSearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStatus(status: PdfIndexStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageText(page: PdfPageFtsEntity)

    @Query("UPDATE pdf_index_status SET indexedPageCount = :count WHERE uri = :uri")
    suspend fun updateIndexedCount(uri: String, count: Int)

    @Query("UPDATE pdf_index_status SET isIndexed = 1, indexedPageCount = :pageCount, lastIndexed = :now WHERE uri = :uri")
    suspend fun markIndexed(uri: String, pageCount: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM pdf_index_status WHERE isIndexed = 0")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM pdf_index_status WHERE isIndexed = 0 ORDER BY lastIndexed ASC LIMIT 1")
    suspend fun getNextUnindexed(): PdfIndexStatusEntity?

    @Query("SELECT uri FROM pdf_index_status WHERE isIndexed = 1")
    suspend fun getAllIndexedUris(): List<String>

    @Query("DELETE FROM pdf_pages_fts WHERE uri = :uri")
    suspend fun deletePagesForUri(uri: String)

    @Query("DELETE FROM pdf_index_status WHERE uri = :uri")
    suspend fun deleteStatus(uri: String)

    @Query("""
        SELECT p.uri, p.pageNumber, p.pageText, s.fileName
        FROM pdf_pages_fts p
        JOIN pdf_index_status s ON p.uri = s.uri
        WHERE pdf_pages_fts MATCH :query
        ORDER BY s.fileName, p.pageNumber
    """)
    suspend fun searchPages(query: String): List<PdfSearchResult>
}
