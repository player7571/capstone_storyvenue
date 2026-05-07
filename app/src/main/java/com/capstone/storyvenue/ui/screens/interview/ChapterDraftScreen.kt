package com.capstone.storyvenue.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

data class ChapterDraft(
    val id: String,
    val chapterNumber: Int,
    val chapterType: String,
    val title: String,
    val content: String,
    val storyQualityAtGeneration: String? = null,
)

private sealed interface ChapterDraftScreenState {
    data object Loading : ChapterDraftScreenState
    data object Empty : ChapterDraftScreenState
    data class Loaded(val draft: ChapterDraft) : ChapterDraftScreenState
    data class Error(val message: String) : ChapterDraftScreenState
}

/**
 * 서버에서 챕터 초안을 가져옵니다.
 * 실제 엔드포인트가 연결되기 전까지는 더미 데이터를 반환합니다.
 *
 * @param sessionId 인터뷰 세션 ID
 * @param chapterType 챕터 타입 (childhood/youth/career/love/reflection)
 */
suspend fun generateChapterDraft(
    sessionId: String,
    questionNo: Int?,
    chapterType: String?,
    allowBasic: Boolean,
    token: String,
): Result<ChapterDraft> = withContext(Dispatchers.IO) {
    if (sessionId.isBlank()) {
        return@withContext Result.failure(Exception("세션 정보가 없습니다. 인터뷰를 다시 시작해주세요."))
    }
    if (token.isBlank()) {
        return@withContext Result.failure(Exception("로그인이 필요합니다."))
    }

    return@withContext try {
        val result = ApiService.generateChapter(
            token = token,
            sessionId = sessionId,
            questionNo = questionNo,
            chapterType = chapterType,
            allowBasic = allowBasic,
        )
        result.map { chapter ->
            val resolvedQuestionNo = chapter.sourceQuestionNo ?: questionNo ?: 1
            ChapterDraft(
                id = chapter.id,
                chapterNumber = resolvedQuestionNo,
                chapterType = chapter.chapterType.ifBlank { chapterType.orEmpty() },
                title = chapter.title.ifBlank { "이야기 $resolvedQuestionNo" },
                content = chapter.content,
                storyQualityAtGeneration = chapter.storyQualityAtGeneration,
            )
        }
    } catch (e: Exception) {
        Log.w("ChapterDraft", "챕터 생성 실패: ${e.message}")
        Result.failure(e)
    }
}

suspend fun fetchLatestChapterDraft(
    sessionId: String,
    questionNo: Int?,
    token: String,
): Result<ChapterDraft?> = withContext(Dispatchers.IO) {
    if (sessionId.isBlank()) {
        return@withContext Result.failure(Exception("세션 정보가 없습니다. 인터뷰를 다시 시작해주세요."))
    }
    if (token.isBlank()) {
        return@withContext Result.failure(Exception("로그인이 필요합니다."))
    }

    return@withContext try {
        val result = ApiService.getLatestChapter(
            token = token,
            sessionId = sessionId,
            questionNo = questionNo,
        )
        result.map { chapter ->
            chapter?.let {
                ChapterDraft(
                    id = it.id,
                    chapterNumber = it.sourceQuestionNo ?: questionNo ?: 1,
                    chapterType = it.chapterType,
                    title = it.title.ifBlank { "이야기 ${it.sourceQuestionNo ?: questionNo ?: 1}" },
                    content = it.content,
                    storyQualityAtGeneration = it.storyQualityAtGeneration,
                )
            }
        }
    } catch (e: Exception) {
        Log.w("ChapterDraft", "최신 초안 조회 실패: ${e.message}")
        Result.failure(e)
    }
}

suspend fun fetchChapterDraft(
    chapterId: String,
    token: String,
): Result<ChapterDraft> = withContext(Dispatchers.IO) {
    if (chapterId.isBlank()) {
        return@withContext Result.failure(Exception("초안 정보가 없습니다."))
    }
    if (token.isBlank()) {
        return@withContext Result.failure(Exception("로그인이 필요합니다."))
    }

    return@withContext try {
        val result = ApiService.getChapter(
            token = token,
            chapterId = chapterId,
        )
        result.map { chapter ->
            ChapterDraft(
                id = chapter.id,
                chapterNumber = chapter.sourceQuestionNo ?: 1,
                chapterType = chapter.chapterType,
                title = chapter.title.ifBlank { "이야기 ${chapter.sourceQuestionNo ?: 1}" },
                content = chapter.content,
                storyQualityAtGeneration = chapter.storyQualityAtGeneration,
            )
        }
    } catch (e: Exception) {
        Log.w("ChapterDraft", "초안 조회 실패: ${e.message}")
        Result.failure(e)
    }
}

private fun dummyChapter(number: Int) = ChapterDraft(
    id = "preview-$number",
    chapterNumber = number,
    chapterType = "youth",
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
 * @param chapterType  생성할 챕터 타입 (childhood/youth/career/love/reflection)
 * @param onBack       뒤로가기 콜백
 * @param onAddToBook  "책에 추가" 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDraftScreen(
    sessionId: String     = "",
    chapterId: String?    = null,
    questionNo: Int?      = null,
    chapterType: String?  = null,
    allowBasic: Boolean   = false,
    autoGenerate: Boolean = false,
    onBack: () -> Unit    = {},
    onAddToBook: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    // ── 상태 ────────────────────────────────────────────────────────────────
    var screenState by remember { mutableStateOf<ChapterDraftScreenState>(ChapterDraftScreenState.Loading) }
    var pendingAutoGenerate by rememberSaveable(sessionId, questionNo, chapterType, allowBasic) {
        mutableStateOf(autoGenerate)
    }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var editingTitle by rememberSaveable { mutableStateOf("") }
    var editingContent by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val loadedDraft = (screenState as? ChapterDraftScreenState.Loaded)?.draft
    LaunchedEffect(loadedDraft?.id, loadedDraft?.title, loadedDraft?.content, isEditMode) {
        if (loadedDraft != null && !isEditMode) {
            editingTitle = loadedDraft.title
            editingContent = loadedDraft.content
        }
    }

    suspend fun loadDraftState() {
        screenState = ChapterDraftScreenState.Loading
        val result: Result<ChapterDraft?> = if (!chapterId.isNullOrBlank()) {
            val chapterResult = fetchChapterDraft(
                chapterId = chapterId,
                token = token,
            )
            if (chapterResult.isSuccess) {
                Result.success(chapterResult.getOrNull())
            } else {
                Result.failure(chapterResult.exceptionOrNull() ?: Exception("초안 조회 실패"))
            }
        } else {
            fetchLatestChapterDraft(
                sessionId = sessionId,
                questionNo = questionNo,
                token = token,
            )
        }
        screenState = if (result.isSuccess) {
            result.getOrNull()?.let { ChapterDraftScreenState.Loaded(it) }
                ?: ChapterDraftScreenState.Empty
        } else {
            ChapterDraftScreenState.Error(
                result.exceptionOrNull()?.message ?: "최신 초안을 불러오지 못했습니다."
            )
        }
    }

    suspend fun generateDraftState() {
        screenState = ChapterDraftScreenState.Loading
        val result = generateChapterDraft(
            sessionId = sessionId,
            questionNo = questionNo,
            chapterType = chapterType,
            allowBasic = allowBasic,
            token = token,
        )
        screenState = if (result.isSuccess) {
            pendingAutoGenerate = false
            isEditMode = false
            ChapterDraftScreenState.Loaded(result.getOrNull()!!)
        } else {
            ChapterDraftScreenState.Error(
                result.exceptionOrNull()?.message ?: "알 수 없는 오류"
            )
        }
    }

    suspend fun saveDraftChanges() {
        val draft = (screenState as? ChapterDraftScreenState.Loaded)?.draft ?: return
        val title = editingTitle.trim()
        val content = editingContent.trim()
        if (title.isBlank() || content.isBlank()) {
            Toast.makeText(context, "제목과 본문을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        val result = withContext(Dispatchers.IO) {
            ApiService.updateChapter(
                token = token,
                chapterId = draft.id,
                title = title,
                content = content,
            )
        }
        isSaving = false

        if (result.isSuccess) {
            val updated = result.getOrNull()!!
            screenState = ChapterDraftScreenState.Loaded(
                ChapterDraft(
                    id = updated.id,
                    chapterNumber = updated.sourceQuestionNo ?: draft.chapterNumber,
                    chapterType = updated.chapterType.ifBlank { draft.chapterType },
                    title = updated.title.ifBlank { title },
                    content = updated.content,
                    storyQualityAtGeneration = updated.storyQualityAtGeneration ?: draft.storyQualityAtGeneration,
                )
            )
            isEditMode = false
            Toast.makeText(context, "초안을 저장했어요.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                result.exceptionOrNull()?.message ?: "초안 저장에 실패했습니다.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 최초 로드
    LaunchedEffect(sessionId, chapterId, questionNo, chapterType, allowBasic) {
        if (pendingAutoGenerate) {
            generateDraftState()
        } else {
            loadDraftState()
        }
    }

    // ── 함수 ────────────────────────────────────────────────────────────────
    fun regenerate() {
        scope.launch {
            generateDraftState()
        }
        Log.d("ChapterDraft", "다시 생성 클릭 — sessionId=$sessionId, questionNo=$questionNo, chapterType=$chapterType")
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
                when (val state = screenState) {
                    ChapterDraftScreenState.Loading -> LoadingState()
                    ChapterDraftScreenState.Empty -> EmptyDraftState()
                    is ChapterDraftScreenState.Error -> ErrorState(message = state.message, onRetry = {
                        scope.launch {
                            if (pendingAutoGenerate) {
                                generateDraftState()
                            } else {
                                loadDraftState()
                            }
                        }
                    })
                    is ChapterDraftScreenState.Loaded -> DraftContent(
                        draft = state.draft,
                        isEditMode = isEditMode,
                        editingTitle = editingTitle,
                        editingContent = editingContent,
                        onTitleChange = { editingTitle = it },
                        onContentChange = { editingContent = it },
                    )
                }
            }

            // ── 하단 버튼 ──────────────────────────────────────────────────
            when (val state = screenState) {
                ChapterDraftScreenState.Loading -> Spacer(Modifier.height(24.dp))
                ChapterDraftScreenState.Empty -> EmptyBottomButtons(
                    isLoading = false,
                    onGenerate = ::regenerate,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                is ChapterDraftScreenState.Error -> Spacer(Modifier.height(24.dp))
                is ChapterDraftScreenState.Loaded -> BottomButtons(
                    isLoading = isSaving,
                    isEditMode = isEditMode,
                    onRegenerate = ::regenerate,
                    onEdit = {
                        editingTitle = state.draft.title
                        editingContent = state.draft.content
                        isEditMode = true
                    },
                    onCancelEdit = {
                        editingTitle = state.draft.title
                        editingContent = state.draft.content
                        isEditMode = false
                    },
                    onSave = {
                        scope.launch { saveDraftChanges() }
                    },
                    onAddToBook = {
                        Log.d("ChapterDraft", "책에 추가 클릭 — title=${state.draft.title}, sessionId=$sessionId, chapterId=${state.draft.id}")
                        onAddToBook(sessionId)
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun DraftContent(
    draft: ChapterDraft,
    isEditMode: Boolean,
    editingTitle: String,
    editingContent: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
) {
    Column {
        // 챕터 제목
        Text(
            text       = if (isEditMode) {
                "이야기 ${draft.chapterNumber} : 초안을 수정하고 있어요"
            } else {
                "이야기 ${draft.chapterNumber} : ${draft.title}\n이야기가 정리되었습니다!"
            },
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = StoryVenueColors.Primary,
            lineHeight = 30.sp,
            modifier   = Modifier.padding(bottom = 20.dp)
        )

        if (isEditMode) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = onTitleChange,
                    label = { Text("제목") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = editingContent,
                    onValueChange = onContentChange,
                    label = { Text("본문") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        } else {
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
private fun EmptyDraftState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "아직 생성된 이야기가 없어요.\n아래 버튼을 눌러 이야기를 만들어보세요.",
            fontSize = 16.sp,
            color = StoryVenueColors.SubText,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
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
private fun EmptyBottomButtons(
    isLoading: Boolean,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onGenerate,
            enabled = !isLoading,
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StoryVenueColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = StoryVenueColors.Divider,
                disabledContentColor = StoryVenueColors.SubText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "이야기 생성하기",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BottomButtons(
    isLoading: Boolean,
    isEditMode: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onAddToBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isEditMode) {
            OutlinedButton(
                onClick  = onCancelEdit,
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
                    text       = "취소",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick  = onSave,
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
                    text       = if (isLoading) "저장 중..." else "저장하기",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            OutlinedButton(
                onClick  = onEdit,
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
                    text       = "초안 수정",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

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
