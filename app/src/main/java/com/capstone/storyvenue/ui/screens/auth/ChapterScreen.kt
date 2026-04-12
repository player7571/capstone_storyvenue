package com.capstone.storyvenue.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors

data class ChapterData(
    val id: String,
    val number: Int,
    val title: String,
    val content: String,
)

// ── 챕터 초안 화면 ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDraftScreen(
    chapter: ChapterData = ChapterData(
        id = "1",
        number = 3,
        title = "대학 시절",
        content = "대학교에 처음 입학했을 때 가장 먼저 느꼈던 건 자유로움이었다. 고등학교 때와는 완전히 다른 세상이 펼쳐졌다. 강의실에서 만난 새로운 친구들, 처음으로 혼자 해결해야 했던 수많은 문제들, 그리고 밤새워 공부하던 도서관의 기억들이 아직도 선명하다.\n\n그 시절의 나는 무엇이든 할 수 있을 것 같았다. 젊음이 주는 에너지와 열정으로 가득 차 있었고, 실패를 두려워하지 않았다. 지금 돌이켜보면 그때의 용기가 지금의 나를 만든 것 같다.",
    ),
    onBack: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onAddToBook: () -> Unit = {},
) {
    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "이야기 초안",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = StoryVenueColors.OnSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StoryVenueColors.Background,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "이야기 ${chapter.number} : ${chapter.title}\n이야기가 정리되었습니다!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = StoryVenueColors.Primary,
                fontFamily = SBAggroFamily,
                lineHeight = 30.sp,
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = chapter.content,
                    fontSize = 15.sp,
                    color = StoryVenueColors.OnSurface,
                    fontFamily = SBAggroFamily,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }

            Spacer(Modifier.height(32.dp))

            StoryButton(
                text = "다시 생성",
                onClick = onRegenerate,
                variant = ButtonVariant.Secondary,
            )

            Spacer(Modifier.height(12.dp))

            StoryButton(
                text = "책에 추가",
                onClick = onAddToBook,
                variant = ButtonVariant.Primary,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 책 미리보기 화면 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPreviewScreen(
    chapters: List<ChapterData> = listOf(
        ChapterData("1", 1, "어린 시절", "동네 골목에서 뛰어놀던 그 시절이 가장 행복했다. 여름이면 매미 소리가 온 동네를 가득 채웠고..."),
        ChapterData("2", 2, "첫 직장", "처음 출근하던 날의 떨림은 아직도 생생하다. 새로운 환경에서 나를 증명해야 했던 그 시간들..."),
        ChapterData("3", 3, "대학 시절", "대학교에 처음 입학했을 때 가장 먼저 느꼈던 건 자유로움이었다..."),
    ),
    onBack: () -> Unit = {},
    onMoreInterview: () -> Unit = {},
    onPostToFeed: () -> Unit = {},
) {
    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "나의 이야기",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = StoryVenueColors.OnSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StoryVenueColors.Background,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            chapters.forEach { chapter ->
                ChapterAccordion(chapter = chapter)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))

            StoryButton(
                text = "이야기 더 만들기",
                onClick = onMoreInterview,
                variant = ButtonVariant.Secondary,
            )

            Spacer(Modifier.height(12.dp))

            StoryButton(
                text = "피드에 올리기",
                onClick = onPostToFeed,
                variant = ButtonVariant.Primary,  // ← Primary(초록색)로
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ChapterAccordion(chapter: ChapterData) {
    var expanded by remember { mutableStateOf(chapter.number == 1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "이야기 ${chapter.number} : ${chapter.title}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = StoryVenueColors.Primary,
                    fontFamily = SBAggroFamily,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = StoryVenueColors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(StoryVenueColors.Divider)
                )
                Text(
                    text = chapter.content,
                    fontSize = 14.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}