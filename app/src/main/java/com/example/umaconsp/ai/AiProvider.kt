package com.example.umaconsp.ai

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface AiProvider {
    suspend fun sendImages(images: List<Uri>): Flow<AiEvent>
}