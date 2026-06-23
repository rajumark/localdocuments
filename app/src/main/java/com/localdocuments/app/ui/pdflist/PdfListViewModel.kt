package com.localdocuments.app.ui.pdflist

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.localdocuments.app.data.db.AppDatabase
import com.localdocuments.app.data.db.PdfSearchResult
import com.localdocuments.app.data.indexer.PdfIndexer
import com.localdocuments.app.data.model.PdfDocument
import com.localdocuments.app.data.model.PdfGroup
import com.localdocuments.app.data.repository.PdfRepository
import com.localdocuments.app.data.worker.PdfIndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class PdfListUiState(
    val groups: List<PdfGroup> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isGridView: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val searchResults: List<PdfSearchResult> = emptyList(),
    val isSearchingContent: Boolean = false,
    val pendingIndexCount: Int = 0
)

class PdfListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PdfRepository(application.contentResolver)
    private val db = AppDatabase.getInstance(application)
    private val indexer = PdfIndexer(application)

    private val _uiState = MutableStateFlow(PdfListUiState())
    val uiState: StateFlow<PdfListUiState> = _uiState.asStateFlow()

    private var allDocuments: List<PdfDocument> = emptyList()

    val thumbnails: MutableMap<Uri, Bitmap> = mutableStateMapOf()

    val isSelectionMode: Boolean get() = _uiState.value.selectedUris.isNotEmpty()

    init {
        viewModelScope.launch {
            db.searchDao().getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingIndexCount = count) }
            }
        }
    }

    fun loadDocuments() {
        if (!_uiState.value.isPermissionGranted) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            allDocuments = repository.getPdfDocuments()
            syncIndexWithDocuments()
            updateGroups()
            scheduleIndexing()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun syncIndexWithDocuments() {
        val indexedUris = db.searchDao().getAllIndexedUris().toSet()
        val currentUriSet = allDocuments.map { it.uri.toString() }.toSet()

        val removedUris = indexedUris - currentUriSet
        removedUris.forEach { uriStr ->
            db.searchDao().deletePagesForUri(uriStr)
            db.searchDao().deleteStatus(uriStr)
        }

        val statusUris = db.searchDao().getAllIndexedUris().toSet()
        allDocuments.filter { it.uri.toString() !in statusUris }.forEach { doc ->
            db.searchDao().insertOrUpdateStatus(
                com.localdocuments.app.data.db.PdfIndexStatusEntity(
                    uri = doc.uri.toString(),
                    fileName = doc.name,
                    pageCount = 0,
                    isIndexed = false
                )
            )
        }
    }

    private fun scheduleIndexing() {
        val work = OneTimeWorkRequestBuilder<PdfIndexWorker>().build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                "pdf_indexing",
                ExistingWorkPolicy.KEEP,
                work
            )
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isPermissionGranted = granted) }
        if (granted) loadDocuments()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchingContent = query.isNotBlank()) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearchingContent = false) }
            updateGroups()
        } else {
            updateGroups()
            searchContent(query)
        }
    }

    private fun searchContent(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val terms = query.trim().split(Regex("\\s+")).joinToString(" ") { "${it}*" }
            val results = db.searchDao().searchPages(terms)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun loadThumbnail(uri: Uri) {
        if (thumbnails.containsKey(uri)) return
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = repository.renderThumbnail(uri, 240, 320)
            if (bitmap != null) {
                thumbnails[uri] = bitmap
            }
        }
    }

    fun toggleSelection(uri: Uri) {
        _uiState.update {
            val current = it.selectedUris.toMutableSet()
            if (current.contains(uri)) current.remove(uri) else current.add(uri)
            it.copy(selectedUris = current)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedUris = emptySet()) }
    }

    fun selectAll() {
        val allUris = allDocuments.map { it.uri }.toSet()
        _uiState.update { it.copy(selectedUris = allUris) }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteSelected(contentResolver: ContentResolver) {
        val uris = _uiState.value.selectedUris.toList()
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    contentResolver.delete(uri, null, null)
                } catch (_: Exception) { }
                indexer.removeFromIndex(uri)
            }
            allDocuments = allDocuments.filter { it.uri !in uris }
            thumbnails.keys.removeAll(uris)
            _uiState.update {
                it.copy(
                    selectedUris = emptySet(),
                    showDeleteConfirmation = false
                )
            }
            updateGroups()
        }
    }

    fun getSelectedDocuments(): List<PdfDocument> {
        val selected = _uiState.value.selectedUris
        return allDocuments.filter { it.uri in selected }
    }

    private fun updateGroups() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allDocuments
        } else {
            allDocuments.filter { it.name.lowercase().contains(query) }
        }
        _uiState.update { it.copy(groups = groupByDate(filtered)) }
    }

    private fun groupByDate(documents: List<PdfDocument>): List<PdfGroup> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = todayStart - 86400000L
        val weekStart = todayStart - 7 * 86400000L
        val monthStart = todayStart - 30 * 86400000L

        val todayDocs = mutableListOf<PdfDocument>()
        val yesterdayDocs = mutableListOf<PdfDocument>()
        val weekDocs = mutableListOf<PdfDocument>()
        val monthDocs = mutableListOf<PdfDocument>()
        val earlierDocs = mutableListOf<PdfDocument>()

        for (doc in documents) {
            when {
                doc.dateModified >= todayStart -> todayDocs.add(doc)
                doc.dateModified >= yesterdayStart -> yesterdayDocs.add(doc)
                doc.dateModified >= weekStart -> weekDocs.add(doc)
                doc.dateModified >= monthStart -> monthDocs.add(doc)
                else -> earlierDocs.add(doc)
            }
        }

        val groups = mutableListOf<PdfGroup>()
        if (todayDocs.isNotEmpty()) groups.add(PdfGroup("Today", todayDocs))
        if (yesterdayDocs.isNotEmpty()) groups.add(PdfGroup("Yesterday", yesterdayDocs))
        if (weekDocs.isNotEmpty()) groups.add(PdfGroup("This Week", weekDocs))
        if (monthDocs.isNotEmpty()) groups.add(PdfGroup("This Month", monthDocs))
        if (earlierDocs.isNotEmpty()) groups.add(PdfGroup("Earlier", earlierDocs))
        return groups
    }
}
