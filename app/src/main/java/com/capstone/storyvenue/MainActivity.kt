package com.capstone.storyvenue

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.capstone.storyvenue.ui.screen.BookPreviewScreen
import com.capstone.storyvenue.ui.screen.ChapterDraftScreen
import com.capstone.storyvenue.ui.theme.Capstone_storyvenue_appTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Capstone_storyvenue_appTheme {
                // ── 미리보기용 화면 전환 (추후 NavHost로 교체) ──────────────
                // 아래 주석을 토글해서 각 화면을 확인하세요.

                BookPreviewScreen(
                    onBack        = { Log.d("Nav", "뒤로가기") },
                    onAddChapter  = { Log.d("Nav", "이야기 더 만들기") },
                    onPostToFeed  = { Log.d("Nav", "피드에 올리기") },
                    onChapterClick = { chapter ->
                        Log.d("Nav", "챕터 이동: ${chapter.title}")
                    }
                )

                // ChapterDraftScreen(
                //     sessionId     = "demo-session",
                //     chapterNumber = 3,
                //     onBack        = { Log.d("Nav", "뒤로가기") },
                //     onAddToBook   = { draft -> Log.d("Nav", "책에 추가: ${draft.title}") }
                // )
            }
        }
    }
}
