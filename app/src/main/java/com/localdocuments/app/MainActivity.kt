package com.localdocuments.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.localdocuments.app.navigation.Screen
import com.localdocuments.app.navigation.bottomNavItems
import com.localdocuments.app.ui.pdflist.PdfListScreen
import com.localdocuments.app.ui.pdflist.PdfListViewModel
import com.localdocuments.app.ui.theme.LocalDocumentsTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            handleScanResult(scanResult)
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            scanViewModel.onScanCancelled()
        } else {
            scanViewModel.onScanError("Scan failed with code: ${result.resultCode}")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pdfListViewModel.setPermissionGranted(granted)
    }

    private lateinit var scanViewModel: DocumentViewModel
    private lateinit var pdfListViewModel: PdfListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanViewModel = DocumentViewModel()
        pdfListViewModel = PdfListViewModel(application)

        enableEdgeToEdge()
        setContent {
            LocalDocumentsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocalDocumentsApp(
                        scanViewModel = scanViewModel,
                        pdfListViewModel = pdfListViewModel,
                        onScan = { startScan() },
                        onRequestPdfPermission = { requestPdfPermission() }
                    )
                }
            }
        }
    }

    private fun startScan() {
        val settings = scanViewModel.uiState.value.settings
        val scannerMode = when (settings.mode) {
            ScannerMode.FULL -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
            ScannerMode.BASE -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
            ScannerMode.BASE_WITH_FILTER -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
        }

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(scannerMode)
            .setGalleryImportAllowed(settings.galleryImportEnabled)
            .setPageLimit(settings.pageLimit)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scanViewModel.onScanStarted()
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                scanViewModel.onScanError(e.message ?: "Failed to start scanner")
            }
    }

    private fun handleScanResult(result: GmsDocumentScanningResult?) {
        if (result == null) {
            scanViewModel.onScanError("Empty scan result")
            return
        }

        val pages = result.pages?.map { page ->
            ScannedPage(imageUri = page.imageUri)
        } ?: emptyList()

        val pdf = result.pdf?.let {
            ScanPdf(uri = it.uri, pageCount = it.pageCount)
        }

        scanViewModel.onScanResult(pages, pdf)
    }

    private fun requestPdfPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (PermissionChecker.checkSelfPermission(this, permission) ==
            PermissionChecker.PERMISSION_GRANTED
        ) {
            pdfListViewModel.setPermissionGranted(true)
        } else {
            permissionLauncher.launch(permission)
        }
    }
}

@Composable
fun LocalDocumentsApp(
    scanViewModel: DocumentViewModel,
    pdfListViewModel: PdfListViewModel,
    onScan: () -> Unit,
    onRequestPdfPermission: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Scan.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Scan.route) {
                ScanScreen(
                    viewModel = scanViewModel,
                    onScan = onScan
                )
            }
            composable(Screen.Pdfs.route) {
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    val permission = if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    val granted = PermissionChecker.checkSelfPermission(context, permission) ==
                            PermissionChecker.PERMISSION_GRANTED
                    pdfListViewModel.setPermissionGranted(granted)
                    if (!granted) {
                        onRequestPdfPermission()
                    }
                }

                PdfListScreen(
                    viewModel = pdfListViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: DocumentViewModel = viewModel(),
    onScan: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScan,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScannerSettingsSection(
                settings = state.settings,
                onModeChange = viewModel::setScannerMode,
                onGalleryImportChange = viewModel::setGalleryImportEnabled,
                onPageLimitChange = viewModel::setPageLimit
            )

            state.error?.let { error ->
                ErrorCard(
                    message = error,
                    onDismiss = viewModel::clearError
                )
            }

            if (state.pages.isEmpty() && state.pdf == null) {
                EmptyState()
            } else {
                ScanResultSection(
                    pages = state.pages,
                    pdf = state.pdf,
                    scanCount = state.scanCount
                )
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
fun ScannerSettingsSection(
    settings: ScannerSettings,
    onModeChange: (ScannerMode) -> Unit,
    onGalleryImportChange: (Boolean) -> Unit,
    onPageLimitChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scanner Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Mode",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScannerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { onModeChange(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    ScannerMode.FULL -> "Full"
                                    ScannerMode.BASE -> "Base"
                                    ScannerMode.BASE_WITH_FILTER -> "Base + Filter"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gallery Import",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = settings.galleryImportEnabled,
                    onCheckedChange = onGalleryImportChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page Limit",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                var pageLimitText by remember(settings.pageLimit) {
                    mutableStateOf(settings.pageLimit.toString())
                }
                OutlinedTextField(
                    value = pageLimitText,
                    onValueChange = { text ->
                        pageLimitText = text
                        text.toIntOrNull()?.let { onPageLimitChange(it) }
                    },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun ScanResultSection(
    pages: List<ScannedPage>,
    pdf: ScanPdf?,
    scanCount: Int
) {
    val context = LocalContext.current

    if (pages.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Scanned Pages (${pages.size})",
                style = MaterialTheme.typography.titleMedium
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pages) { page ->
                    Card(
                        modifier = Modifier.size(160.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(page.imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Scanned page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }

    pdf?.let { pdfResult ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PDF Ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${pdfResult.pageCount} pages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Button(
                        onClick = {
                            val path = pdfResult.uri.path ?: return@Button
                            val file = File(path)
                            val externalUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, externalUri)
                                type = "application/pdf"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share PDF")
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF")
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            icon = {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "On-Device Document Scanner",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scan physical documents using your camera.\nAll processing happens on-device — your privacy is protected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
