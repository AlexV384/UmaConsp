package com.example.umaconsp.ai

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LocalAiProvider : AiProvider {
    override suspend fun sendImages(images: List<Uri>): Flow<AiEvent> = flow {
        // TODO: реализовать вызов Native.conversePub для каждого изображения
        // и эмитить события AiEvent.Token и AiEvent.Complete
        emit(AiEvent.Error(UnsupportedOperationException("LocalAiProvider not implemented yet")))
    }
}