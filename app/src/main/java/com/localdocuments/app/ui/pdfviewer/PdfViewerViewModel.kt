package com.localdocuments.app.ui.pdfviewer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localdocuments.app.data.repository.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PdfViewerUiState(
    val pageCount: Int = 0,
    val isLoading: Boolean = true
)

class PdfViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PdfRepository(application.contentResolver)

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    val pageBitmaps: MutableMap<Int, Bitmap> = mutableStateMapOf()

    private var currentUri: Uri? = null
    private var screenWidth = 0

    fun loadPdf(uri: Uri, displayWidth: Int) {
        currentUri = uri
        screenWidth = displayWidth
        pageBitmaps.clear()
        _uiState.value = PdfViewerUiState(isLoading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.getPageCount(uri)
            _uiState.value = PdfViewerUiState(pageCount = count, isLoading = false)
        }
    }

    fun loadPage(pageIndex: Int) {
        if (pageBitmaps.containsKey(pageIndex)) return
        val uri = currentUri ?: return
        val width = if (screenWidth > 0) screenWidth else 1080
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = repository.renderPage(uri, pageIndex, width, Int.MAX_VALUE)
            if (bitmap != null) {
                pageBitmaps[pageIndex] = bitmap
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pageBitmaps.values.forEach { it.recycle() }
        pageBitmaps.clear()
    }
}
