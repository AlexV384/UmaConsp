// src/main/java/com/example/umaconsp/ChatActivity.kt
package com.example.umaconsp.presentation.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.umaconsp.ui.theme.UmaconspTheme   // or your app theme
import com.example.umaconsp.R

class ChatDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UmaconspTheme {
                // Use Compose to inflate the XML layout via AndroidView
                androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                    android.view.LayoutInflater.from(context).inflate(R.layout.layout_chat, android.widget.FrameLayout(context), false)
                })
            }
        }
    }
}
