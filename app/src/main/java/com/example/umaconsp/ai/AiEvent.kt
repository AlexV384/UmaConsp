package com.example.umaconsp.ai

sealed class AiEvent {
    data class Token(val text: String) : AiEvent()
    data class Thinking(val text: String) : AiEvent()
    data class Error(val throwable: Throwable) : AiEvent()
    object Complete : AiEvent()
}