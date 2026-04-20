package com.capstone.storyvenue.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueAppTheme
import com.capstone.storyvenue.ui.theme.StoryVenueColors

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

data class BookChapter(
    val number: Int,
    val title: String,
    val preview: String   // 펼쳤을 때 보여줄 미리보기 텍스트
)

// ──────────────────────────────────────────────────────────────────────────────
// Dummy data
// ──────────────────────────────────────────────────────────────────────────────

private val dummyChapters = listOf(
    BookChapter(
        number  = 1,
        title   = "어린 시절",
        preview = "동네 골목에서 뛰어놀던 그 시절이 가장 행복했다 ..."
    ),
    BookChapter(
        number  = 2,
        title   = "첫 직장",
        preview = "처음 출근하던 날의 긴장감과 설렘은 아직도 생생하다. 낯선 사무실, 낯선 얼굴들 속에서 나만의 자리를 찾아가는 과정이었다 ..."
    ),
    BookChapter(
        number  = 3,
        title   = "대학 시절",
        preview = "대학교에 처음 입학했을 때 가장 먼저 느꼈던 건 자유로움이었다. 고등학교 때와는 완전히 다른 세상이 펼쳐졌다 ..."
    )
)

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 책 미리보기 화면
 *
 * @param bookTitle       책 제목 (상단 표시)
 * @param chapters        챕터 목록 (기본: 더미 데이터)
 * @param onBack          뒤로가기 콜백
 * @param onAddChapter    "이야기 더 만들기" 콜백
 * @param onPostToFeed    "이야기에 올리기" 콜백
 * @param onChapterClick  챕터 카드 클릭 콜백 (→ 챕터 초안 화면 이동용)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPreviewScreen(
    bookTitle: String                  = "나의 이야기",
    chapters: List<BookChapter>        = dummyChapters,
    onBack: () -> Unit                 = {},
    onAddChapter: () -> Unit           = {},
    onPostToFeed: () -> Unit           = {},
    onChapterClick: (BookChapter) -> Unit = {}
) {
    // 펼쳐진 챕터 인덱스 (-1 = 없음, 첫 번째 챕터를 기본 확장)
    var expandedIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = bookTitle,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = StoryVenueColors.OnSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint               = StoryVenueColors.OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StoryVenueColors.Background)
            )
        },
        containerColor = StoryVenueColors.Background
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── 챕터 목록 ────────────────────────────────────────────────────
            LazyColumn(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding      = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    ChapterAccordionCard(
                        chapter    = chapter,
                        isExpanded = expandedIndex == index,
                        onToggle   = {
                            expandedIndex = if (expandedIndex == index) -1 else index
                        },
                        onArrowClick = {
                            Log.d("BookPreview", "챕터 이동: ${chapter.title}")
                            onChapterClick(chapter)
                        }
                    )
                }
            }

            // ── 하단 버튼 ────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 이야기 더 만들기
                OutlinedButton(
                    onClick  = {
                        Log.d("BookPreview", "이야기 더 만들기 클릭")
                        onAddChapter()
                    },
                    shape  = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StoryVenueColors.Surface,
                        contentColor   = StoryVenueColors.OnSurface
                    ),
                    border   = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text       = "이야기 더 만들기",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 이야기에 올리기
                Button(
                    onClick  = {
                        Log.d("BookPreview", "이야기에 올리기 클릭")
                        onPostToFeed()
                    },
                    shape  = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoryVenueColors.Primary,
                        contentColor   = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text       = "이야기에 올리기",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Accordion Card
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChapterAccordionCard(
    chapter: BookChapter,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onArrowClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onToggle
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {

            // ── 헤더 행 ──────────────────────────────────────────────────────
            Row(
                modifier       = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = "이야기 ${chapter.number} : ${chapter.title}",
                    fontSize   = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color      = StoryVenueColors.Primary,
                    modifier   = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                // 펼쳐진 상태: ▼(닫기) / 닫힌 상태: →(상세 이동)
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    },
                    label = "chapter_icon"
                ) { expanded ->
                    if (expanded) {
                        Icon(
                            imageVector        = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "접기",
                            tint               = StoryVenueColors.Primary,
                            modifier           = Modifier.size(24.dp)
                        )
                    } else {
                        IconButton(
                            onClick  = onArrowClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "챕터 보기",
                                tint               = StoryVenueColors.SubText,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── 미리보기 텍스트 (아코디언 확장 시) ───────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter   = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                exit    = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color     = StoryVenueColors.Divider,
                        thickness = 1.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text       = chapter.preview,
                        fontSize   = 16.sp,
                        color      = StoryVenueColors.SubText,
                        lineHeight = 24.sp,
                        maxLines   = 4,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Preview
// ──────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookPreviewScreenPreview() {
    StoryVenueAppTheme {
        BookPreviewScreen()
    }
}
