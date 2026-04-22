package com.capstone.storyvenue.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueAppTheme
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookChapter(
    val id: String,
    val sourceQuestionNo: Int?,
    val title: String,
    val preview: String,
    val createdAt: String,
    val isLatest: Boolean,
    val historyIndex: Int? = null,
)

private data class BookQuestionGroup(
    val key: String,
    val sourceQuestionNo: Int?,
    val activeChapter: BookChapter,
)

private fun buildQuestionLabel(sourceQuestionNo: Int?): String =
    sourceQuestionNo?.let { "이야기 $it" } ?: "자유 이야기"

private fun buildGroupKey(sourceQuestionNo: Int?): String =
    sourceQuestionNo?.let { "q-$it" } ?: "free-story"

private fun formatDraftTimestamp(isoString: String): String {
    return try {
        val zoned = try {
            ZonedDateTime.parse(isoString)
        } catch (_: Exception) {
            Instant.parse(isoString).atZone(ZoneId.systemDefault())
        }
        zoned.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    } catch (_: Exception) {
        timeAgo(isoString)
    }
}

private fun buildQuestionGroups(drafts: List<ChapterDraftData>): List<BookQuestionGroup> {
    return drafts
        .groupBy { it.sourceQuestionNo }
        .toList()
        .sortedWith(
            compareBy<Pair<Int?, List<ChapterDraftData>>>(
                { it.first == null },
                { it.first ?: Int.MAX_VALUE },
            )
        )
        .mapNotNull { (sourceQuestionNo, groupedDrafts) ->
            val versions = groupedDrafts
                .sortedByDescending { it.createdAt }
                .mapIndexed { index, draft ->
                    BookChapter(
                        id = draft.id,
                        sourceQuestionNo = draft.sourceQuestionNo,
                        title = draft.title.ifBlank { "제목 없는 이야기" },
                        preview = draft.preview,
                        createdAt = draft.createdAt,
                        isLatest = index == 0,
                        historyIndex = if (index == 0) null else index,
                    )
                }
            if (versions.isEmpty()) {
                null
            } else {
                BookQuestionGroup(
                    key = buildGroupKey(sourceQuestionNo),
                    sourceQuestionNo = sourceQuestionNo,
                    activeChapter = versions.first(),
                )
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPreviewScreen(
    sessionId: String,
    bookTitle: String = "나의 이야기",
    onBack: () -> Unit = {},
    onAddChapter: () -> Unit = {},
    onAutobiographyCreated: (String) -> Unit = {},
    onPostToFeed: () -> Unit = {},
    onChapterClick: (BookChapter) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var questionGroups by remember { mutableStateOf<List<BookQuestionGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }
    val showPreviousDraftsByGroup = remember { mutableStateMapOf<String, Boolean>() }
    val previousDraftsByGroup = remember { mutableStateMapOf<String, List<BookChapter>>() }
    val loadingPreviousDraftsByGroup = remember { mutableStateMapOf<String, Boolean>() }
    var isPublishing by remember { mutableStateOf(false) }
    var deletingChapterId by remember { mutableStateOf<String?>(null) }
    var postingChapterId by remember { mutableStateOf<String?>(null) }
    val activeChapters = questionGroups
        .map { it.activeChapter }
        .sortedBy { it.sourceQuestionNo ?: Int.MAX_VALUE }
    val storyNumbers = activeChapters.mapNotNull { it.sourceQuestionNo }.toSet()
    val missingStoryNumbers = (1..10).filterNot { it in storyNumbers }
    val canCreateAutobiography = questionGroups.isNotEmpty() && missingStoryNumbers.isEmpty()

    suspend fun loadChapters() {
        if (token.isBlank()) {
            errorMsg = "로그인이 필요합니다."
            isLoading = false
            return
        }
        if (sessionId.isBlank()) {
            errorMsg = "문답 정보를 찾을 수 없습니다."
            isLoading = false
            return
        }

        val result = withContext(Dispatchers.IO) {
            ApiService.listChapters(
                token = token,
                sessionId = sessionId,
                latestOnly = true,
            )
        }
        if (result.isSuccess) {
            val groups = buildQuestionGroups(result.getOrNull().orEmpty())
            questionGroups = groups
            if (groups.none { it.key == expandedGroupKey }) {
                expandedGroupKey = null
            }
            val validKeys = groups.map { it.key }.toSet()
            previousDraftsByGroup.keys.toList().forEach { key ->
                if (key !in validKeys) {
                    previousDraftsByGroup.remove(key)
                    loadingPreviousDraftsByGroup.remove(key)
                    showPreviousDraftsByGroup.remove(key)
                }
            }
            errorMsg = null
        } else {
            errorMsg = result.exceptionOrNull()?.message ?: "불러오기에 실패했습니다"
        }
        isLoading = false
    }

    LaunchedEffect(sessionId) { loadChapters() }

    fun loadPreviousDrafts(group: BookQuestionGroup) {
        val questionNo = group.sourceQuestionNo ?: return
        if (loadingPreviousDraftsByGroup[group.key] == true) return
        loadingPreviousDraftsByGroup[group.key] = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.listChapters(
                    token = token,
                    sessionId = sessionId,
                    questionNo = questionNo,
                    latestOnly = false,
                )
            }
            loadingPreviousDraftsByGroup[group.key] = false
            if (result.isSuccess) {
                val previous = result.getOrNull().orEmpty()
                    .sortedByDescending { it.createdAt }
                    .drop(1)
                    .mapIndexed { index, draft ->
                        BookChapter(
                            id = draft.id,
                            sourceQuestionNo = draft.sourceQuestionNo,
                            title = draft.title.ifBlank { "제목 없는 이야기" },
                            preview = draft.preview,
                            createdAt = draft.createdAt,
                            isLatest = false,
                            historyIndex = index + 1,
                        )
                    }
                previousDraftsByGroup[group.key] = previous
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "이전 초안을 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun createAutobiography() {
        if (isPublishing) return
        if (!canCreateAutobiography) {
            val missingText = if (missingStoryNumbers.isEmpty()) {
                "이야기 10편이 모두 있어야 자서전을 만들 수 있어요."
            } else {
                "이야기 ${missingStoryNumbers.joinToString(", ")}이 더 필요해요."
            }
            Toast.makeText(context, missingText, Toast.LENGTH_SHORT).show()
            return
        }

        isPublishing = true
        scope.launch {
            val ids = activeChapters.map { it.id }

            val createResult = withContext(Dispatchers.IO) {
                ApiService.createAutobiography(
                    token = token,
                    sessionId = sessionId,
                    chapterIds = ids,
                    title = bookTitle,
                )
            }
            if (createResult.isFailure) {
                isPublishing = false
                Toast.makeText(
                    context,
                    createResult.exceptionOrNull()?.message ?: "자서전 생성 실패",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            isPublishing = false
            val created = createResult.getOrNull()!!
            Toast.makeText(context, "자서전을 만들었어요.", Toast.LENGTH_SHORT).show()
            onAutobiographyCreated(created.bookId)
        }
    }

    fun deleteChapter(chapter: BookChapter) {
        if (deletingChapterId != null) return
        deletingChapterId = chapter.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.deleteChapter(token, chapter.id)
            }
            deletingChapterId = null
            if (result.isSuccess) {
                expandedGroupKey = null
                previousDraftsByGroup.clear()
                loadingPreviousDraftsByGroup.clear()
                showPreviousDraftsByGroup.clear()
                isLoading = true
                errorMsg = null
                loadChapters()
                Toast.makeText(
                    context,
                    "${buildQuestionLabel(chapter.sourceQuestionNo)}의 초안을 삭제했어요.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "삭제에 실패했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun postChapter(chapter: BookChapter) {
        if (postingChapterId != null || deletingChapterId != null || isPublishing) return
        postingChapterId = chapter.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.createChapterFeedPost(
                    token = token,
                    chapterId = chapter.id,
                )
            }
            postingChapterId = null
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    "${buildQuestionLabel(chapter.sourceQuestionNo)} 초안을 게시했어요.",
                    Toast.LENGTH_SHORT
                ).show()
                onPostToFeed()
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "초안 게시에 실패했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bookTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StoryVenueColors.OnSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = StoryVenueColors.OnSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StoryVenueColors.Background),
            )
        },
        containerColor = StoryVenueColors.Background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CenteredLoading()
                    errorMsg != null -> CenteredError(
                        message = errorMsg!!,
                        onRetry = {
                            isLoading = true
                            errorMsg = null
                            scope.launch { loadChapters() }
                        },
                    )
                    questionGroups.isEmpty() -> CenteredMessage("아직 생성된 이야기가 없습니다.\n‘이야기 더 만들기’로 시작해 보세요.")
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        items(questionGroups, key = { it.key }) { group ->
                            QuestionGroupCard(
                                group = group,
                                isExpanded = expandedGroupKey == group.key,
                                showPreviousDrafts = showPreviousDraftsByGroup[group.key] == true,
                                deletingChapterId = deletingChapterId,
                                postingChapterId = postingChapterId,
                                onToggle = {
                                    expandedGroupKey = if (expandedGroupKey == group.key) null else group.key
                                },
                                onOpenActiveClick = {
                                    Log.d("BookPreview", "대표 초안 이동: ${group.activeChapter.title}")
                                    onChapterClick(group.activeChapter)
                                },
                                onDeleteChapter = { chapter ->
                                    Log.d("BookPreview", "초안 삭제: ${chapter.title} (${chapter.id})")
                                    deleteChapter(chapter)
                                },
                                onPostChapter = { chapter ->
                                    Log.d("BookPreview", "초안 게시: ${chapter.title} (${chapter.id})")
                                    postChapter(chapter)
                                },
                                previousChapters = previousDraftsByGroup[group.key].orEmpty(),
                                isLoadingPreviousDrafts = loadingPreviousDraftsByGroup[group.key] == true,
                                onTogglePreviousDrafts = {
                                    val current = showPreviousDraftsByGroup[group.key] == true
                                    if (!current && previousDraftsByGroup[group.key] == null) {
                                        loadPreviousDrafts(group)
                                    }
                                    showPreviousDraftsByGroup[group.key] = !current
                                },
                                onOpenPreviousClick = { chapter ->
                                    Log.d("BookPreview", "이전 초안 이동: ${chapter.title}")
                                    onChapterClick(chapter)
                                },
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (questionGroups.isNotEmpty()) {
                    val statusMessage = if (canCreateAutobiography) {
                        "이야기 10편이 모두 모였어요. 이제 자서전을 만들 수 있어요."
                    } else {
                        "현재 ${10 - missingStoryNumbers.size}/10편이 모였어요. 이야기 ${missingStoryNumbers.joinToString(", ")}이 더 필요해요."
                    }
                    Text(
                        text = statusMessage,
                        color = if (canCreateAutobiography) StoryVenueColors.Primary else StoryVenueColors.SubText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }

                OutlinedButton(
                    onClick = {
                        Log.d("BookPreview", "이야기 더 만들기 클릭")
                        onAddChapter()
                    },
                    enabled = !isPublishing && deletingChapterId == null,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StoryVenueColors.Surface,
                        contentColor = StoryVenueColors.OnSurface,
                    ),
                    border = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "이야기 더 만들기",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = {
                        Log.d("BookPreview", "자서전 만들기 클릭")
                        createAutobiography()
                    },
                    enabled = !isPublishing && !isLoading && canCreateAutobiography && deletingChapterId == null && postingChapterId == null,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoryVenueColors.Primary,
                        contentColor = Color.White,
                        disabledContainerColor = StoryVenueColors.Divider,
                        disabledContentColor = StoryVenueColors.SubText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (isPublishing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "자서전 작성 중...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Text(
                            text = "자서전 만들기",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionGroupCard(
    group: BookQuestionGroup,
    isExpanded: Boolean,
    showPreviousDrafts: Boolean,
    deletingChapterId: String?,
    postingChapterId: String?,
    previousChapters: List<BookChapter>,
    isLoadingPreviousDrafts: Boolean,
    onToggle: () -> Unit,
    onOpenActiveClick: () -> Unit,
    onDeleteChapter: (BookChapter) -> Unit,
    onPostChapter: (BookChapter) -> Unit,
    onTogglePreviousDrafts: () -> Unit,
    onOpenPreviousClick: (BookChapter) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${buildQuestionLabel(group.sourceQuestionNo)} · 최신 초안",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StoryVenueColors.SubText,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = group.activeChapter.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.Primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatDraftTimestamp(group.activeChapter.createdAt),
                        fontSize = 13.sp,
                        color = StoryVenueColors.SubText,
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenActiveClick,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "초안 보기",
                        tint = StoryVenueColors.SubText,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = { onDeleteChapter(group.activeChapter) },
                    enabled = deletingChapterId != group.activeChapter.id,
                    modifier = Modifier.size(24.dp),
                ) {
                    if (deletingChapterId == group.activeChapter.id) {
                        CircularProgressIndicator(
                            color = StoryVenueColors.Error,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "대표 초안 삭제",
                            tint = StoryVenueColors.Error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = StoryVenueColors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150)),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = StoryVenueColors.Divider,
                        thickness = 1.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = group.activeChapter.preview,
                        fontSize = 16.sp,
                        color = StoryVenueColors.SubText,
                        lineHeight = 24.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { onPostChapter(group.activeChapter) },
                        enabled = postingChapterId != group.activeChapter.id && deletingChapterId == null,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        if (postingChapterId == group.activeChapter.id) {
                            CircularProgressIndicator(
                                color = StoryVenueColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Text(
                                text = "이 초안 피드에 올리기",
                                color = StoryVenueColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (group.sourceQuestionNo != null) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = onTogglePreviousDrafts,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (showPreviousDrafts) {
                                    "이전 초안 접기"
                                } else {
                                    when {
                                        isLoadingPreviousDrafts -> "이전 초안 불러오는 중..."
                                        previousChapters.isNotEmpty() -> "이전 초안 ${previousChapters.size}개 보기"
                                        else -> "이전 초안 보기"
                                    }
                                },
                                color = StoryVenueColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        AnimatedVisibility(
                            visible = showPreviousDrafts,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(180)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(Modifier.height(4.dp))
                                if (isLoadingPreviousDrafts) {
                                    CircularProgressIndicator(
                                        color = StoryVenueColors.Primary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else if (previousChapters.isEmpty()) {
                                    Text(
                                        text = "이전 초안이 없어요.",
                                        fontSize = 14.sp,
                                        color = StoryVenueColors.SubText,
                                    )
                                } else {
                                    previousChapters.forEach { chapter ->
                                        PreviousDraftRow(
                                            chapter = chapter,
                                            isDeleting = deletingChapterId == chapter.id,
                                            isPosting = postingChapterId == chapter.id,
                                            onOpenClick = { onOpenPreviousClick(chapter) },
                                            onDeleteClick = { onDeleteChapter(chapter) },
                                            onPostClick = { onPostChapter(chapter) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousDraftRow(
    chapter: BookChapter,
    isDeleting: Boolean,
    isPosting: Boolean,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPostClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "이전 초안 ${chapter.historyIndex ?: 1} · ${formatDraftTimestamp(chapter.createdAt)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = StoryVenueColors.SubText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chapter.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StoryVenueColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onOpenClick,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "이전 초안 보기",
                    tint = StoryVenueColors.SubText,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onDeleteClick,
                enabled = !isDeleting && !isPosting,
                modifier = Modifier.size(24.dp),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        color = StoryVenueColors.Error,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "이전 초안 삭제",
                        tint = StoryVenueColors.Error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onPostClick,
            enabled = !isDeleting && !isPosting,
            contentPadding = PaddingValues(0.dp),
        ) {
            if (isPosting) {
                CircularProgressIndicator(
                    color = StoryVenueColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = "이 초안 피드에 올리기",
                    color = StoryVenueColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookPreviewScreenPreview() {
    StoryVenueAppTheme {
        BookPreviewScreen(sessionId = "preview-session")
    }
}
