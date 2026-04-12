package com.capstone.storyvenue

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.capstone.storyvenue.ui.screen.ChapterDraftScreen
import com.capstone.storyvenue.ui.theme.Capstone_storyvenue_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Capstone_storyvenue_appTheme {
                // 챕터 초안 화면 — 추후 NavHost 연결 시 여기에 Navigation 추가
                ChapterDraftScreen(
                    sessionId     = "demo-session",
                    chapterNumber = 3,
                    onBack        = { Log.d("Nav", "뒤로가기") },
                    onAddToBook   = { draft ->
                        Log.d("Nav", "책에 추가: ${draft.title}")
                    }
                )
            }
        }
    }
}
