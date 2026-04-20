package com.capstone.storyvenue.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

data class BookChapter(
    val id: String,
    val number: Int,
    val title: String,
    val preview: String,
    val content: String,
)

private fun buildPreview(content: String): String {
    val trimmed = content.trim().replace(Regex("\\s+"), " ")
    return if (trimmed.length <= 120) trimmed else trimmed.take(120) + " ..."
}

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPreviewScreen(
    bookTitle: String                  = "나의 이야기",
    onBack: () -> Unit                 = {},
    onAddChapter: () -> Unit           = {},
    onPostToFeed: () -> Unit           = {},
    onChapterClick: (BookChapter) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var chapters by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var expandedIndex by remember { mutableIntStateOf(0) }
    var isPublishing by remember { mutableStateOf(false) }

    suspend fun loadChapters() {
        if (token.isBlank()) {
            errorMsg = "로그인이 필요합니다."
            isLoading = false
            return
        }
        val result = withContext(Dispatchers.IO) { ApiService.listChapters(token) }
        if (result.isSuccess) {
            val drafts = result.getOrNull().orEmpty()
            chapters = drafts
                .sortedBy { it.createdAt }
                .mapIndexed { idx, d ->
                    BookChapter(
                        id = d.id,
                        number = idx + 1,
                        title = d.title.ifBlank { "챕터 ${idx + 1}" },
                        preview = buildPreview(d.content),
                        content = d.content,
                    )
                }
            errorMsg = null
        } else {
            errorMsg = result.exceptionOrNull()?.message ?: "불러오기에 실패했습니다"
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { loadChapters() }

    fun publish() {
        if (isPublishing) return
        if (chapters.isEmpty()) {
            Toast.makeText(context, "게시할 이야기가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        isPublishing = true
        scope.launch {
            val ids = chapters.map { it.id }
            val firstContent = chapters.firstOrNull()?.content.orEmpty()

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
            val chaptersBlock = chapters.joinToString("\n\n") { ch ->
                "이야기 ${ch.number} : ${ch.title}\n${ch.content}"
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CenteredLoading()
                    errorMsg != null -> CenteredError(
                        message = errorMsg!!,
                        onRetry = {
                            isLoading = true
                            errorMsg = null
                            scope.launch { loadChapters() }
                        }
                    )
                    chapters.isEmpty() -> CenteredMessage("아직 생성된 이야기가 없습니다.\n‘이야기 더 만들기’로 시작해 보세요.")
                    else -> LazyColumn(
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
                }
            }

            // ── 하단 버튼 ────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = {
                        Log.d("BookPreview", "이야기 더 만들기 클릭")
                        onAddChapter()
                    },
                    enabled  = !isPublishing,
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

                Button(
                    onClick  = {
                        Log.d("BookPreview", "이야기에 올리기 클릭")
                        publish()
                    },
                    enabled  = !isPublishing && !isLoading && chapters.isNotEmpty(),
                    shape  = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StoryVenueColors.Primary,
                        contentColor   = Color.White,
                        disabledContainerColor = StoryVenueColors.Divider,
                        disabledContentColor   = StoryVenueColors.SubText,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
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
}

// ──────────────────────────────────────────────────────────────────────────────
// States
// ──────────────────────────────────────────────────────────────────────────────

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                color = StoryVenueColors.Error,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
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
