package com.example.umaconsp.utils

import androidx.compose.runtime.compositionLocalOf
import com.example.umaconsp.ai.AiProvider
import com.example.umaconsp.ai.ResponseParser
import com.example.umaconsp.presentation.documentlist.DocumentListViewModel

val LocalDocumentListViewModel = compositionLocalOf<DocumentListViewModel> {
    error("No DocumentListViewModel provided")
}

val LocalAiProvider = compositionLocalOf<AiProvider> {
    error("No AiProvider provided")
}

val LocalResponseParser = compositionLocalOf<ResponseParser> {
    error("No ResponseParser provided")
}