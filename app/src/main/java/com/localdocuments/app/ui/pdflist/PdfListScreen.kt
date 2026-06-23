package com.localdocuments.app.ui.pdflist

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localdocuments.app.data.db.PdfSearchResult
import com.localdocuments.app.data.model.PdfDocument
import com.localdocuments.app.data.model.PdfGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfListScreen(
    viewModel: PdfListViewModel,
    onOpenPdf: (String, String) -> Unit = { _, _ -> },
    onOpenPdfAtPage: (String, String, Int) -> Unit = { _, _, _ -> },
    onScan: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isSelectionMode = state.selectedUris.isNotEmpty()

    LaunchedEffect(state.isPermissionGranted) {
        if (state.isPermissionGranted) {
            viewModel.loadDocuments()
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Delete PDFs") },
            text = {
                Text("Are you sure you want to delete ${state.selectedUris.size} PDF file(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelected(context.contentResolver) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                SelectionTopAppBar(
                    selectedCount = state.selectedUris.size,
                    onShare = {
                        val docs = viewModel.getSelectedDocuments()
                        val uris = docs.map { it.uri }
                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "application/pdf"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share PDFs"))
                        viewModel.clearSelection()
                    },
                    onDelete = { viewModel.showDeleteConfirmation() },
                    onClearSelection = { viewModel.clearSelection() }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "LocalDocuments",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScan,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                isGridView = state.isGridView,
                onToggleView = viewModel::toggleViewMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.pendingIndexCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.pendingIndexCount} PDF(s) pending text indexing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.searchQuery.isNotBlank() && state.searchResults.isNotEmpty()) {
                SearchResultsContent(
                    nameResults = state.groups,
                    contentResults = state.searchResults,
                    selectedUris = state.selectedUris,
                    thumbnails = viewModel.thumbnails,
                    isSelectionMode = isSelectionMode,
                    onOpenPdf = onOpenPdf,
                    onOpenPdfAtPage = onOpenPdfAtPage,
                    onToggleSelection = viewModel::toggleSelection,
                    onThumbnailRequest = viewModel::loadThumbnail,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.groups.isEmpty()) {
                EmptyPdfList(
                    hasSearch = state.searchQuery.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.isGridView) {
                GridContent(
                    groups = state.groups,
                    selectedUris = state.selectedUris,
                    thumbnails = viewModel.thumbnails,
                    isSelectionMode = isSelectionMode,
                    onOpenPdf = onOpenPdf,
                    onToggleSelection = viewModel::toggleSelection,
                    onThumbnailRequest = viewModel::loadThumbnail
                )
            } else {
                ListContent(
                    groups = state.groups,
                    selectedUris = state.selectedUris,
                    thumbnails = viewModel.thumbnails,
                    isSelectionMode = isSelectionMode,
                    onOpenPdf = onOpenPdf,
                    onToggleSelection = viewModel::toggleSelection,
                    onThumbnailRequest = viewModel::loadThumbnail
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear selection"
                )
            }
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share"
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search PDFs by name...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun ListContent(
    groups: List<PdfGroup>,
    selectedUris: Set<Uri>,
    thumbnails: Map<Uri, Bitmap>,
    isSelectionMode: Boolean,
    onOpenPdf: (String, String) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onThumbnailRequest: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.forEach { group ->
            item(key = group.label) {
                SectionHeader(label = group.label)
            }
            items(
                items = group.documents,
                key = { it.uri.toString() }
            ) { doc ->
                PdfListItem(
                    document = doc,
                    thumbnail = thumbnails[doc.uri],
                    isSelected = selectedUris.contains(doc.uri),
                    isSelectionMode = isSelectionMode,
                    onThumbnailRequest = { onThumbnailRequest(doc.uri) },
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(doc.uri)
                        } else {
                            onOpenPdf(doc.uri.toString(), doc.name)
                        }
                    },
                    onLongClick = {
                        onToggleSelection(doc.uri)
                    }
                )
            }
        }
    }
}

@Composable
fun GridContent(
    groups: List<PdfGroup>,
    selectedUris: Set<Uri>,
    thumbnails: Map<Uri, Bitmap>,
    isSelectionMode: Boolean,
    onOpenPdf: (String, String) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onThumbnailRequest: (Uri) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        groups.forEach { group ->
            item(span = { GridItemSpan(2) }) {
                SectionHeader(label = group.label)
            }
            items(
                items = group.documents,
                key = { it.uri.toString() }
            ) { doc ->
                PdfGridItem(
                    document = doc,
                    thumbnail = thumbnails[doc.uri],
                    isSelected = selectedUris.contains(doc.uri),
                    isSelectionMode = isSelectionMode,
                    onThumbnailRequest = { onThumbnailRequest(doc.uri) },
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelection(doc.uri)
                        } else {
                            onOpenPdf(doc.uri.toString(), doc.name)
                        }
                    },
                    onLongClick = {
                        onToggleSelection(doc.uri)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfListItem(
    document: PdfDocument,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onThumbnailRequest: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "${document.name} preview",
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                LaunchedEffect(Unit) {
                    onThumbnailRequest()
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name.removeSuffix(".pdf"),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = document.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = formatDate(document.dateModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfGridItem(
    document: PdfDocument,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onThumbnailRequest: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            Column {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "${document.name} preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    LaunchedEffect(Unit) {
                        onThumbnailRequest()
                    }
                }

                Text(
                    text = document.name.removeSuffix(".pdf"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsContent(
    nameResults: List<PdfGroup>,
    contentResults: List<PdfSearchResult>,
    selectedUris: Set<Uri>,
    thumbnails: Map<Uri, Bitmap>,
    isSelectionMode: Boolean,
    onOpenPdf: (String, String) -> Unit,
    onOpenPdfAtPage: (String, String, Int) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onThumbnailRequest: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (nameResults.isNotEmpty()) {
            item(key = "name_header") {
                SectionHeader(label = "Matching Files")
            }
            nameResults.forEach { group ->
                items(
                    items = group.documents,
                    key = { "name_${it.uri.toString()}" }
                ) { doc ->
                    PdfListItem(
                        document = doc,
                        thumbnail = thumbnails[doc.uri],
                        isSelected = selectedUris.contains(doc.uri),
                        isSelectionMode = isSelectionMode,
                        onThumbnailRequest = { onThumbnailRequest(doc.uri) },
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(doc.uri)
                            } else {
                                onOpenPdf(doc.uri.toString(), doc.name)
                            }
                        },
                        onLongClick = {
                            onToggleSelection(doc.uri)
                        }
                    )
                }
            }
        }

        if (contentResults.isNotEmpty()) {
            item(key = "content_header") {
                SectionHeader(label = "Found in Content")
            }
            items(
                items = contentResults,
                key = { "${it.uri}_${it.pageNumber}" }
            ) { result ->
                SearchResultItem(
                    result = result,
                    onClick = { onOpenPdfAtPage(result.uri, result.fileName, result.pageNumber - 1) }
                )
            }
        }

        if (nameResults.isEmpty() && contentResults.isEmpty()) {
            item(key = "empty_search") {
                EmptyPdfList(hasSearch = true, modifier = Modifier.fillParentMaxSize())
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: PdfSearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${result.fileName.removeSuffix(".pdf")} - Page ${result.pageNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.pageText.take(200).replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EmptyPdfList(
    hasSearch: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasSearch) "No PDFs match your search" else "No PDFs found on device",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasSearch) "Try a different search term" else "PDF files on your device will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
