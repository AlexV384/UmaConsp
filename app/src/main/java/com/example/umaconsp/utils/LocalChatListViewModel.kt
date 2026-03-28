package com.example.umaconsp.utils

import androidx.compose.runtime.compositionLocalOf
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel

val LocalDocumentListViewModel =
    compositionLocalOf<DocumentListViewModel> {
        error("No DocumentListViewModel provided")
    }