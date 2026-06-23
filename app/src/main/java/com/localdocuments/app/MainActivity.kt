package com.localdocuments.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.localdocuments.app.navigation.Viewer
import com.localdocuments.app.ui.pdflist.PdfListScreen
import com.localdocuments.app.ui.pdflist.PdfListViewModel
import com.localdocuments.app.ui.pdfviewer.PdfViewerScreen
import com.localdocuments.app.ui.pdfviewer.PdfViewerViewModel
import com.localdocuments.app.ui.theme.LocalDocumentsTheme

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

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pdfListViewModel.setPermissionGranted(Environment.isExternalStorageManager())
    }

    private val readStorageLauncher = registerForActivityResult(
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

        scanViewModel.onScanResult(
            result.pages?.map { page -> ScannedPage(imageUri = page.imageUri) } ?: emptyList(),
            result.pdf?.let { ScanPdf(uri = it.uri, pageCount = it.pageCount) }
        )
    }

    private fun requestPdfPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                pdfListViewModel.setPermissionGranted(true)
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            }
        } else {
            if (PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PermissionChecker.PERMISSION_GRANTED
            ) {
                pdfListViewModel.setPermissionGranted(true)
            } else {
                readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val granted = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_GRANTED
        }
        pdfListViewModel.setPermissionGranted(granted)
        if (!granted) {
            onRequestPdfPermission()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "pdfs"
    ) {
        composable("pdfs") {
            PdfListScreen(
                viewModel = pdfListViewModel,
                onScan = onScan,
                onOpenPdf = { uri, name ->
                    navController.navigate(
                        Viewer.createRoute(
                            Uri.encode(uri),
                            Uri.encode(name)
                        )
                    )
                }
            )
        }
        composable(
            route = Viewer.route,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uri = Uri.decode(
                backStackEntry.arguments?.getString("uri") ?: return@composable
            )
            val name = Uri.decode(
                backStackEntry.arguments?.getString("name") ?: "PDF"
            )
            val pdfUri = Uri.parse(uri)

            val viewModel: PdfViewerViewModel = viewModel()
            val config = LocalConfiguration.current
            val screenWidthPx = with(LocalDensity.current) {
                config.screenWidthDp.dp.toPx().toInt()
            }

            LaunchedEffect(pdfUri) {
                viewModel.loadPdf(pdfUri, screenWidthPx)
            }

            PdfViewerScreen(
                pdfName = name,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
