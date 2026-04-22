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
import androidx.compose.ui.text.style.TextAlign
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
    val content: String,
    val createdAt: String,
    val isLatest: Boolean,
    val historyIndex: Int? = null,
)

private data class BookQuestionGroup(
    val key: String,
    val sourceQuestionNo: Int?,
    val activeChapter: BookChapter,
    val previousChapters: List<BookChapter>,
)

private fun buildPreview(content: String): String {
    val trimmed = content.trim().replace(Regex("\\s+"), " ")
    return if (trimmed.length <= 120) trimmed else trimmed.take(120) + " ..."
}

private fun buildQuestionLabel(sourceQuestionNo: Int?): String =
    sourceQuestionNo?.let { "질문 $it" } ?: "자유 이야기"

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
                        preview = buildPreview(draft.content),
                        content = draft.content,
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
                    previousChapters = versions.drop(1),
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
    var isPublishing by remember { mutableStateOf(false) }
    var deletingChapterId by remember { mutableStateOf<String?>(null) }

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

        val result = withContext(Dispatchers.IO) { ApiService.listChapters(token, sessionId) }
        if (result.isSuccess) {
            val groups = buildQuestionGroups(result.getOrNull().orEmpty())
            questionGroups = groups
            if (groups.none { it.key == expandedGroupKey }) {
                expandedGroupKey = null
            }
            errorMsg = null
        } else {
            errorMsg = result.exceptionOrNull()?.message ?: "불러오기에 실패했습니다"
        }
        isLoading = false
    }

    LaunchedEffect(sessionId) { loadChapters() }

    fun publish() {
        if (isPublishing) return
        if (questionGroups.isEmpty()) {
            Toast.makeText(context, "게시할 이야기가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        isPublishing = true
        scope.launch {
            val activeChapters = questionGroups.map { it.activeChapter }
            val ids = activeChapters.map { it.id }

            val compileResult = withContext(Dispatchers.IO) {
                ApiService.compileBook(token = token, chapterIds = ids, title = bookTitle)
            }
            if (compileResult.isFailure) {
                isPublishing = false
                Toast.makeText(
                    context,
                    compileResult.exceptionOrNull()?.message ?: "책 만들기 실패",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val book = compileResult.getOrNull()!!
            val subtitleLine = book.subtitle?.takeIf { it.isNotBlank() }
            val chaptersBlock = activeChapters.joinToString("\n\n") { chapter ->
                "${buildQuestionLabel(chapter.sourceQuestionNo)} : ${chapter.title}\n${chapter.content}"
            }
            val preview = if (subtitleLine != null) {
                "$subtitleLine\n\n$chaptersBlock"
            } else {
                chaptersBlock
            }

            val feedResult = withContext(Dispatchers.IO) {
                ApiService.createFeedPost(
                    token = token,
                    bookId = book.id,
                    title = book.title,
                    preview = preview,
                )
            }
            isPublishing = false
            if (feedResult.isSuccess) {
                Toast.makeText(context, "이야기를 게시했습니다.", Toast.LENGTH_SHORT).show()
                onPostToFeed()
            } else {
                Toast.makeText(
                    context,
                    feedResult.exceptionOrNull()?.message ?: "게시 실패",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                                onTogglePreviousDrafts = {
                                    val current = showPreviousDraftsByGroup[group.key] == true
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
                        Log.d("BookPreview", "이야기에 올리기 클릭")
                        publish()
                    },
                    enabled = !isPublishing && !isLoading && questionGroups.isNotEmpty() && deletingChapterId == null,
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
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Text(
                            text = "이야기에 올리기",
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
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = StoryVenueColors.Primary)
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                color = StoryVenueColors.Error,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) {
                Text("다시 시도", color = StoryVenueColors.Primary)
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = StoryVenueColors.SubText,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun QuestionGroupCard(
    group: BookQuestionGroup,
    isExpanded: Boolean,
    showPreviousDrafts: Boolean,
    deletingChapterId: String?,
    onToggle: () -> Unit,
    onOpenActiveClick: () -> Unit,
    onDeleteChapter: (BookChapter) -> Unit,
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

                    if (group.previousChapters.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = onTogglePreviousDrafts,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (showPreviousDrafts) {
                                    "이전 초안 접기"
                                } else {
                                    "이전 초안 ${group.previousChapters.size}개 보기"
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
                                group.previousChapters.forEach { chapter ->
                                    PreviousDraftRow(
                                        chapter = chapter,
                                        isDeleting = deletingChapterId == chapter.id,
                                        onOpenClick = { onOpenPreviousClick(chapter) },
                                        onDeleteClick = { onDeleteChapter(chapter) },
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

@Composable
private fun PreviousDraftRow(
    chapter: BookChapter,
    isDeleting: Boolean,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
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
            enabled = !isDeleting,
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
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun BookPreviewScreenPreview() {
    StoryVenueAppTheme {
        BookPreviewScreen(sessionId = "preview-session")
    }
}
