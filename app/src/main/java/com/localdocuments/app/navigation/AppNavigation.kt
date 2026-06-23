package com.localdocuments.app.navigation

object Viewer {
    const val route = "pdfviewer/{uri}/{name}"
    fun createRoute(uri: String, name: String) = "pdfviewer/$uri/$name"
}
