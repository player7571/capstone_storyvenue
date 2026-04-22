package com.capstone.storyvenue.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutobiographyDetailScreen(
    bookId: String,
    onBack: () -> Unit = {},
    onOpenFeedPost: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var book by remember { mutableStateOf<BookDetailData?>(null) }
    var shareStatus by remember { mutableStateOf(BookShareStatusData(shared = false, postId = null)) }
    var isLoading by remember { mutableStateOf(true) }
    var isSharing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    suspend fun loadBook() {
        if (token.isBlank()) {
            errorMsg = "로그인이 필요합니다."
            isLoading = false
            return
        }
        val bookResult = withContext(Dispatchers.IO) { ApiService.getBookDetail(token, bookId) }
        if (bookResult.isFailure) {
            errorMsg = bookResult.exceptionOrNull()?.message ?: "자서전을 불러오지 못했습니다."
            isLoading = false
            return
        }
        val shareResult = withContext(Dispatchers.IO) { ApiService.getBookShareStatus(token, bookId) }
        book = bookResult.getOrNull()
        shareStatus = shareResult.getOrNull() ?: BookShareStatusData(shared = false, postId = null)
        errorMsg = null
        isLoading = false
    }

    LaunchedEffect(bookId) { loadBook() }

    fun shareBook() {
        if (isSharing || shareStatus.shared) return
        isSharing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.shareBookToFeed(token, bookId)
            }
            isSharing = false
            if (result.isSuccess) {
                val shared = result.getOrNull()!!
                shareStatus = BookShareStatusData(shared = true, postId = shared.postId)
                Toast.makeText(context, "자서전을 공유했어요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "자서전 공유 실패",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "자서전",
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
                    isLoading -> DetailCenteredLoading()
                    errorMsg != null -> DetailCenteredError(
                        message = errorMsg!!,
                        onRetry = {
                            isLoading = true
                            errorMsg = null
                            scope.launch { loadBook() }
                        },
                    )
                    book == null -> DetailCenteredMessage("자서전을 찾을 수 없어요.")
                    else -> {
                        val currentBook = book!!
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                        ) {
                            item {
                                Text(
                                    text = currentBook.title,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StoryVenueColors.Primary,
                                )
                                currentBook.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        fontSize = 15.sp,
                                        color = StoryVenueColors.SubText,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            items(currentBook.chapters.sortedBy { it.sourceQuestionNo ?: Int.MAX_VALUE }) { chapter ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            text = chapter.sourceQuestionNo?.let { "이야기 $it" } ?: "자유 이야기",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StoryVenueColors.SubText,
                                        )
                                        Text(
                                            text = chapter.title,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StoryVenueColors.OnSurface,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                                        )
                                        Text(
                                            text = chapter.content,
                                            fontSize = 16.sp,
                                            lineHeight = 26.sp,
                                            color = StoryVenueColors.OnSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isLoading && errorMsg == null && book != null) {
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (shareStatus.shared) {
                            "이미 공유된 자서전입니다. 필요하면 피드에서 확인해보세요."
                        } else {
                            "자서전을 먼저 읽어보고, 마음에 들면 공유 버튼을 눌러 피드에 올려보세요."
                        },
                        color = StoryVenueColors.SubText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )

                    Button(
                        onClick = { shareBook() },
                        enabled = !isSharing && !shareStatus.shared,
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
                        if (isSharing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                text = if (shareStatus.shared) "이미 공유됨" else "공유하기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (shareStatus.shared && !shareStatus.postId.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onOpenFeedPost(shareStatus.postId!!) },
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
                                text = "피드 보러가기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = StoryVenueColors.Primary)
    }
}

@Composable
private fun DetailCenteredError(message: String, onRetry: () -> Unit) {
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
private fun DetailCenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = StoryVenueColors.SubText,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}
