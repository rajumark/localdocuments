package com.localdocuments.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ScannerMode(val value: String) {
    FULL("Full"),
    BASE("Base"),
    BASE_WITH_FILTER("Base + Filter")
}

data class ScannedPage(
    val imageUri: Uri
)

data class ScanPdf(
    val uri: Uri,
    val pageCount: Int
)

data class ScannerSettings(
    val mode: ScannerMode = ScannerMode.FULL,
    val galleryImportEnabled: Boolean = true,
    val pageLimit: Int = 5
)

data class DocumentUiState(
    val pages: List<ScannedPage> = emptyList(),
    val pdf: ScanPdf? = null,
    val isScanning: Boolean = false,
    val error: String? = null,
    val scanCount: Int = 0,
    val settings: ScannerSettings = ScannerSettings()
)

class DocumentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    fun setScannerMode(mode: ScannerMode) {
        _uiState.update { it.copy(settings = it.settings.copy(mode = mode)) }
    }

    fun setGalleryImportEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(galleryImportEnabled = enabled)) }
    }

    fun setPageLimit(limit: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(pageLimit = limit.coerceIn(1, 20))) }
    }

    fun onScanStarted() {
        _uiState.update { it.copy(isScanning = true, error = null) }
    }

    fun onScanResult(pages: List<ScannedPage>, pdf: ScanPdf?) {
        _uiState.update {
            it.copy(
                pages = pages,
                pdf = pdf,
                isScanning = false,
                error = null,
                scanCount = it.scanCount + 1
            )
        }
    }

    fun onScanCancelled() {
        _uiState.update { it.copy(isScanning = false, error = null) }
    }

    fun onScanError(message: String) {
        _uiState.update { it.copy(isScanning = false, error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResults() {
        _uiState.update { it.copy(pages = emptyList(), pdf = null, error = null) }
    }
}
