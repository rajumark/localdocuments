package com.localdocuments.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    object Scan : Screen("scan", "Scan", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner)
    object Pdfs : Screen("pdfs", "PDFs", Icons.Filled.Description, Icons.Outlined.Description)
}

val bottomNavItems = listOf(Screen.Scan, Screen.Pdfs)
