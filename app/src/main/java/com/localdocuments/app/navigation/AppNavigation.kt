package com.localdocuments.app.navigation

object Viewer {
    const val route = "pdfviewer/{uri}/{name}?page={page}"
    fun createRoute(uri: String, name: String, page: Int = 0) =
        "pdfviewer/$uri/$name?page=$page"
}
