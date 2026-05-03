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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun buildAutobiographyBody(chapters: List<BookChapterPayloadData>): String {
    return chapters
        .sortedBy { it.sourceQuestionNo ?: Int.MAX_VALUE }
        .map { it.content.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n")
}

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
    var isLoading by remember { mutableStateOf(true) }
    var isSharing by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var editingTitle by remember { mutableStateOf("") }
    var editingSubtitle by remember { mutableStateOf("") }
    var editingBody by remember { mutableStateOf("") }

    val currentBook = book
    LaunchedEffect(currentBook?.id, currentBook?.title, currentBook?.subtitle, currentBook?.body, isEditMode) {
        if (currentBook != null && !isEditMode) {
            editingTitle = currentBook.title
            editingSubtitle = currentBook.subtitle.orEmpty()
            editingBody = currentBook.body.ifBlank { buildAutobiographyBody(currentBook.chapters) }
        }
    }

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
        book = bookResult.getOrNull()
        errorMsg = null
        isLoading = false
    }

    LaunchedEffect(bookId) { loadBook() }

    fun shareBook() {
        if (isSharing || book?.shared == true) return
        isSharing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.shareBookToFeed(token, bookId)
            }
            isSharing = false
            if (result.isSuccess) {
                val shared = result.getOrNull()!!
                book = book?.copy(shared = true, sharedPostId = shared.postId)
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

    fun saveBook() {
        val targetBook = book ?: return
        val title = editingTitle.trim()
        val subtitle = editingSubtitle.trim().ifBlank { null }
        val body = editingBody.trim()
        if (title.isBlank() || body.isBlank()) {
            Toast.makeText(context, "제목과 자서전 본문을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiService.updateBook(
                    token = token,
                    bookId = targetBook.id,
                    title = title,
                    subtitle = subtitle,
                    body = body,
                )
            }
            isSaving = false
            if (result.isSuccess) {
                book = result.getOrNull()
                isEditMode = false
                Toast.makeText(context, "자서전을 저장했어요.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    result.exceptionOrNull()?.message ?: "자서전 수정 실패",
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
                    isLoading -> CenteredLoading()
                    errorMsg != null -> CenteredError(
                        message = errorMsg!!,
                        onRetry = {
                            isLoading = true
                            errorMsg = null
                            scope.launch { loadBook() }
                        },
                    )
                    book == null -> CenteredMessage("자서전을 찾을 수 없어요.")
                    else -> {
                        val detailBook = book!!
                        val autobiographyBody = detailBook.body.ifBlank { buildAutobiographyBody(detailBook.chapters) }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                        ) {
                            item {
                                if (isEditMode) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = editingTitle,
                                            onValueChange = { editingTitle = it },
                                            label = { Text("제목") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                        )
                                        OutlinedTextField(
                                            value = editingSubtitle,
                                            onValueChange = { editingSubtitle = it },
                                            label = { Text("부제") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = detailBook.title,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StoryVenueColors.Primary,
                                    )
                                    detailBook.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                        Text(
                                            text = subtitle,
                                            fontSize = 15.sp,
                                            color = StoryVenueColors.SubText,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                    formatBookCreatedAt(detailBook.createdAt)?.let { createdLabel ->
                                        Text(
                                            text = createdLabel,
                                            fontSize = 13.sp,
                                            color = StoryVenueColors.SubText,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }
                            }
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        if (isEditMode) {
                                            OutlinedTextField(
                                                value = editingBody,
                                                onValueChange = { editingBody = it },
                                                label = { Text("자서전 본문") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 360.dp),
                                                shape = RoundedCornerShape(16.dp),
                                            )
                                        } else {
                                            Text(
                                                text = autobiographyBody.ifBlank { "자서전 본문이 아직 비어 있어요." },
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
            }

            if (!isLoading && errorMsg == null && book != null) {
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val detailBook = book!!
                    if (isEditMode) {
                        OutlinedButton(
                            onClick = {
                                editingTitle = detailBook.title
                                editingSubtitle = detailBook.subtitle.orEmpty()
                                editingBody = detailBook.body.ifBlank { buildAutobiographyBody(detailBook.chapters) }
                                isEditMode = false
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = StoryVenueColors.Surface,
                                contentColor = StoryVenueColors.OnSurface,
                                disabledContainerColor = StoryVenueColors.Divider,
                                disabledContentColor = StoryVenueColors.SubText,
                            ),
                            border = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(
                                text = "취소",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Button(
                            onClick = { saveBook() },
                            enabled = !isSaving,
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
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else {
                                Text(
                                    text = "저장하기",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (detailBook.shared) {
                                "이미 공유된 자서전입니다. 피드에서 확인해보세요."
                            } else {
                                "자서전을 먼저 읽어보고, 마음에 들면 공유 버튼을 눌러 피드에 올려보세요."
                            },
                            color = StoryVenueColors.SubText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )

                        if (!detailBook.shared) {
                            OutlinedButton(
                                onClick = { isEditMode = true },
                                enabled = !isSharing,
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
                                    text = "수정하기",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Button(
                            onClick = { shareBook() },
                            enabled = !isSharing && !detailBook.shared,
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
                                    text = if (detailBook.shared) "이미 공유됨" else "공유하기",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        if (detailBook.shared && !detailBook.sharedPostId.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { onOpenFeedPost(detailBook.sharedPostId!!) },
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
}
