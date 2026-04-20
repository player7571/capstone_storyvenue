package com.capstone.storyvenue.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueAppTheme
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

data class ChapterDraft(
    val chapterNumber: Int,
    val title: String,
    val content: String
)

// ──────────────────────────────────────────────────────────────────────────────
// Network helper (OkHttp)
// ──────────────────────────────────────────────────────────────────────────────

private val okHttpClient = OkHttpClient()

/**
 * 서버에서 챕터 초안을 가져옵니다.
 * 실제 엔드포인트가 연결되기 전까지는 더미 데이터를 반환합니다.
 *
 * @param sessionId 인터뷰 세션 ID
 * @param chapterNumber 챕터 번호 (1-based)
 */
suspend fun fetchChapterDraft(
    sessionId: String,
    chapterNumber: Int
): Result<ChapterDraft> = withContext(Dispatchers.IO) {
    return@withContext try {
        val url = "http://10.0.2.2:8000/chapter/$sessionId/$chapterNumber"
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful) {
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            Result.success(
                ChapterDraft(
                    chapterNumber = json.optInt("chapter_number", chapterNumber),
                    title         = json.optString("title", "챕터 $chapterNumber"),
                    content       = json.optString("content", "")
                )
            )
        } else {
            Result.failure(Exception("서버 오류 ${response.code}"))
        }
    } catch (e: Exception) {
        Log.w("ChapterDraft", "네트워크 실패, 더미 데이터 사용: ${e.message}")
        // 백엔드 미연결 시 더미 데이터
        Result.success(dummyChapter(chapterNumber))
    }
}

private fun dummyChapter(number: Int) = ChapterDraft(
    chapterNumber = number,
    title         = "대학 시절",
    content       = """대학교에 처음 입학했을 때 가장 먼저 느꼈던 건 자유로움이었다. 고등학교 때와는 완전히 다른 세상이 펼쳐졌다.

매일 아침 일찍 일어나 교복을 입던 생활은 사라지고, 내가 원하는 시간에 원하는 수업을 들을 수 있었다. 처음엔 그 자유가 낯설고 두렵기도 했지만, 점차 내 삶을 스스로 설계하는 즐거움을 알게 됐다.

1학년 때 동아리 활동을 통해 새로운 친구들을 만났다. 서로 전공도, 고향도 달랐지만 같은 취미로 모인 우리는 금방 가까워졌다. 그 친구들과 함께한 추억들은 지금도 내 마음속에 선명하게 남아 있다.

도서관에서 밤새 공부하던 날들, 학식을 먹으며 나누던 수다, 학교 앞 작은 카페에서의 오후 — 평범하지만 소중한 순간들이 쌓여 대학 시절을 가득 채웠다."""
)

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 챕터 초안 화면
 *
 * @param sessionId    인터뷰 세션 ID (API 연동용)
 * @param chapterNumber 표시할 챕터 번호 (1-based)
 * @param onBack       뒤로가기 콜백
 * @param onAddToBook  "책에 추가" 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDraftScreen(
    sessionId: String     = "demo-session",
    chapterNumber: Int    = 3,
    onBack: () -> Unit    = {},
    onAddToBook: (ChapterDraft) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // ── 상태 ────────────────────────────────────────────────────────────────
    var draft   by remember { mutableStateOf<ChapterDraft?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    // 최초 로드
    LaunchedEffect(sessionId, chapterNumber) {
        loadDraft(sessionId, chapterNumber) { result, err ->
            draft     = result
            errorMsg  = err
            isLoading = false
        }
    }

    // ── 함수 ────────────────────────────────────────────────────────────────
    fun regenerate() {
        isLoading = true
        errorMsg  = null
        scope.launch {
            loadDraft(sessionId, chapterNumber) { result, err ->
                draft     = result
                errorMsg  = err
                isLoading = false
            }
        }
        Log.d("ChapterDraft", "다시 생성 클릭 — sessionId=$sessionId, chapter=$chapterNumber")
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "이야기 초안",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Medium,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StoryVenueColors.Background
                )
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

            // ── 본문 영역 ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                when {
                    isLoading -> LoadingState()
                    errorMsg  != null -> ErrorState(message = errorMsg!!, onRetry = ::regenerate)
                    draft     != null -> DraftContent(draft = draft!!)
                }
            }

            // ── 하단 버튼 ──────────────────────────────────────────────────
            BottomButtons(
                isLoading    = isLoading,
                onRegenerate = ::regenerate,
                onAddToBook  = {
                    draft?.let { d ->
                        Log.d("ChapterDraft", "책에 추가 클릭 — title=${d.title}")
                        onAddToBook(d)
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun DraftContent(draft: ChapterDraft) {
    Column {
        // 챕터 제목
        Text(
            text       = "이야기 ${draft.chapterNumber} : ${draft.title}\n이야기가 정리되었습니다!",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = StoryVenueColors.Primary,
            lineHeight = 30.sp,
            modifier   = Modifier.padding(bottom = 20.dp)
        )

        // 본문 카드
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = draft.content,
                fontSize   = 17.sp,
                color      = StoryVenueColors.OnSurface,
                lineHeight = 26.sp,
                modifier   = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier       = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = StoryVenueColors.Primary)
            Spacer(Modifier.height(16.dp))
            Text(
                text     = "이야기를 생성하고 있어요...",
                fontSize = 16.sp,
                color    = StoryVenueColors.SubText
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text      = message,
                fontSize  = 16.sp,
                color     = StoryVenueColors.Error,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text("다시 시도", color = StoryVenueColors.Primary)
            }
        }
    }
}

@Composable
private fun BottomButtons(
    isLoading: Boolean,
    onRegenerate: () -> Unit,
    onAddToBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 다시 생성
        OutlinedButton(
            onClick  = onRegenerate,
            enabled  = !isLoading,
            shape    = RoundedCornerShape(50.dp),
            colors   = ButtonDefaults.outlinedButtonColors(
                containerColor         = StoryVenueColors.Surface,
                contentColor           = StoryVenueColors.OnSurface,
                disabledContainerColor = StoryVenueColors.Divider,
                disabledContentColor   = StoryVenueColors.SubText
            ),
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text       = "다시 생성",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 책에 추가
        Button(
            onClick  = onAddToBook,
            enabled  = !isLoading,
            shape    = RoundedCornerShape(50.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = StoryVenueColors.Primary,
                contentColor           = Color.White,
                disabledContainerColor = StoryVenueColors.Divider,
                disabledContentColor   = StoryVenueColors.SubText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text       = "책에 추가",
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Internal helper (suspend → callback bridge)
// ──────────────────────────────────────────────────────────────────────────────

private suspend fun loadDraft(
    sessionId: String,
    chapterNumber: Int,
    onResult: (ChapterDraft?, String?) -> Unit
) {
    val result = fetchChapterDraft(sessionId, chapterNumber)
    withContext(Dispatchers.Main) {
        if (result.isSuccess) {
            onResult(result.getOrNull(), null)
        } else {
            onResult(null, result.exceptionOrNull()?.message ?: "알 수 없는 오류")
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Preview
// ──────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChapterDraftScreenPreview() {
    StoryVenueAppTheme {
        ChapterDraftScreen()
    }
}
