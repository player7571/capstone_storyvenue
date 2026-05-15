package com.capstone.storyvenue.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.screens.common.PdfExport
import com.capstone.storyvenue.ui.screens.common.PdfExportSheetContent
import com.capstone.storyvenue.ui.screens.common.PdfShareMode
import com.capstone.storyvenue.ui.screens.common.shareBookTextToKakaoTalk
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FeedPost(
    val id: String,
    val bookId: String? = null,
    val authorId: String = "",
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val title: String,
    val preview: String,
    val likeCount: Int,
    val commentCount: Int,
    val timeAgo: String,
    val likedByMe: Boolean = false,
)

private fun buildSharedAutobiographyBody(book: BookDetailData): String {
    return book.body.ifBlank {
        book.chapters
            .sortedBy { it.sourceQuestionNo ?: Int.MAX_VALUE }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPostClick: (FeedPost) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val cachedUserName = prefs.getString("user_name", "") ?: ""

    var userName by remember { mutableStateOf(cachedUserName) }
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hasNotifBadge by remember { mutableStateOf(false) }
    var hasChatBadge by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadFeed(query: String = searchQuery) {
        withContext(Dispatchers.IO) {
            ApiService.getFeed(token, query = query.trim()).onSuccess { posts = it }
        }
    }

    suspend fun loadProfile() {
        if (token.isBlank()) return
        val profileResult = withContext(Dispatchers.IO) { ApiService.getProfile(token) }
        profileResult.onSuccess { profile ->
            userName = profile.name
            prefs.edit()
                .putString("user_name", profile.name)
                .putString("user_email", profile.email)
                .apply()
        }
    }

    suspend fun loadBadges() {
        withContext(Dispatchers.IO) {
            ApiService.getUnreadCount(token).onSuccess { hasNotifBadge = it > 0 }
            ApiService.getChatPartners(token).onSuccess { partners ->
                hasChatBadge = partners.any { it.unreadCount > 0 }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
        loadFeed()
        loadBadges()
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (!isLoading) {
            delay(350)
            loadFeed(searchQuery)
        }
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoryVenueColors.Background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "이야기마당",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.Primary,
                        fontFamily = SBAggroFamily,
                    )
                    if (userName.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${userName}님, 오늘의 이야기를 만나보세요",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                        )
                    }
                }
                BellIconButton(
                    hasBadge = hasNotifBadge,
                    onClick = onNotificationClick,
                )
            }
        },
        bottomBar = {
            StoryBottomNavBar(
                selectedIndex = 0,
                hasChatBadge = hasChatBadge,
                onHomeClick = onHomeClick,
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StoryVenueColors.Primary)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        loadProfile()
                        loadFeed(searchQuery)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        FeedSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClear = { searchQuery = "" },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    title = if (searchQuery.isBlank()) "아직 올라온 이야기가 없어요" else "검색 결과가 없어요",
                                    subtitle = if (searchQuery.isBlank()) "첫 이야기를 올려 사람들과 나눠보세요." else "다른 검색어로 다시 찾아보세요.",
                                )
                            }
                        }
                    } else {
                        items(posts) { post ->
                            FeedPostCard(post = post, token = token, onClick = { onPostClick(post) })
                            Spacer(Modifier.height(12.dp))
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}


@Composable
private fun FeedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = "제목, 내용, 작성자 검색",
                color = StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "검색",
                tint = StoryVenueColors.SubText,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "검색어 지우기",
                        tint = StoryVenueColors.SubText,
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = StoryVenueColors.Surface,
            unfocusedContainerColor = StoryVenueColors.Surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = StoryVenueColors.OnSurface,
            unfocusedTextColor = StoryVenueColors.OnSurface,
        ),
    )
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    token: String = "",
    onClick: () -> Unit = {},
) {
    var likeCount by remember { mutableStateOf(post.likeCount) }
    var liked by remember { mutableStateOf(post.likedByMe) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(
                    name = post.authorName,
                    avatarUrl = post.authorAvatarUrl,
                    size = 36.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(text = post.authorName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                Spacer(Modifier.weight(1f))
                Text(text = post.timeAgo, fontSize = 14.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
            }
            Spacer(Modifier.height(12.dp))
            Text(text = post.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
            Spacer(Modifier.height(6.dp))
            Text(text = post.preview, fontSize = 16.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            ApiService.toggleLike(token, post.id).onSuccess { (newLiked, newCount) ->
                                liked = newLiked
                                likeCount = newCount
                            }
                        }
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "좋아요",
                        tint = if (liked) StoryVenueColors.Error else StoryVenueColors.SubText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(text = "$likeCount", fontSize = 15.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                Spacer(Modifier.width(16.dp))
                Text(text = "💬", fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(text = "${post.commentCount}", fontSize = 15.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    postId: String = "",
    onBack: () -> Unit = {},
    onChatClick: (authorId: String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val myUserId = prefs.getString("user_id", "") ?: ""
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<FeedPost?>(null) }
    var sharedBook by remember { mutableStateOf<BookDetailData?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<ApiService.CommentData>() }
    var isLoading by remember { mutableStateOf(true) }

    var isPdfSheetOpen by remember { mutableStateOf(false) }
    var pdfIncludeCover by remember { mutableStateOf(false) }
    var pdfCoverBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pdfCoverMime by remember { mutableStateOf<String?>(null) }
    var pdfCoverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var pdfShareMode by remember { mutableStateOf(PdfShareMode.SAVE) }
    var isExportingPdf by remember { mutableStateOf(false) }
    val pdfSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pdfCoverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("이미지를 읽을 수 없습니다.")
                    if (bytes.size > 10 * 1024 * 1024) {
                        throw Exception("표지 이미지 크기는 10MB 이하여야 해요.")
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        ?: throw Exception("이미지를 표시할 수 없어요.")
                    Triple(bytes, mime, bitmap)
                }
            }
            result.onSuccess { (bytes, mime, bitmap) ->
                pdfCoverBytes = bytes
                pdfCoverMime = mime
                pdfCoverBitmap = bitmap
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "이미지를 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun resetPdfSheetState() {
        pdfIncludeCover = false
        pdfCoverBytes = null
        pdfCoverMime = null
        pdfCoverBitmap = null
        pdfShareMode = PdfShareMode.SAVE
    }

    fun exportPdf() {
        val bookId = post?.bookId
        val target = sharedBook
        if (bookId.isNullOrBlank() || target == null) return
        if (isExportingPdf) return
        if (token.isBlank()) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        isExportingPdf = true
        scope.launch {
            val includeCover = pdfIncludeCover
            val coverBytes = if (includeCover) pdfCoverBytes else null
            val coverMime = if (includeCover) pdfCoverMime else null
            val shareMode = pdfShareMode
            val fileName = PdfExport.safeFileName(target.title)

            val pdfResult = withContext(Dispatchers.IO) {
                ApiService.exportBookPdf(
                    token = token,
                    bookId = bookId,
                    includeCover = includeCover,
                    coverImageBytes = coverBytes,
                    coverImageContentType = coverMime,
                    coverImageFileName = coverBytes?.let {
                        if ((coverMime ?: "").contains("png")) "cover.png" else "cover.jpg"
                    },
                )
            }

            pdfResult.onSuccess { bytes ->
                val handled = withContext(Dispatchers.IO) {
                    runCatching {
                        when (shareMode) {
                            PdfShareMode.SAVE -> {
                                PdfExport.saveToDownloads(context, bytes, fileName)
                                null
                            }
                            PdfShareMode.KAKAO -> PdfExport.cacheForShare(context, bytes, fileName)
                        }
                    }
                }
                handled.onSuccess { shareUri ->
                    when (shareMode) {
                        PdfShareMode.SAVE -> Toast.makeText(
                            context,
                            "다운로드 폴더에 저장했어요.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        PdfShareMode.KAKAO -> if (shareUri != null) {
                            PdfExport.shareToKakao(context, shareUri, target.title)
                        }
                    }
                    isPdfSheetOpen = false
                    resetPdfSheetState()
                }.onFailure { e ->
                    Toast.makeText(
                        context,
                        e.message ?: "PDF 저장에 실패했어요.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    e.message ?: "PDF 내보내기에 실패했어요.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            isExportingPdf = false
        }
    }

    LaunchedEffect(postId) {
        sharedBook = null
        withContext(Dispatchers.IO) {
            ApiService.getFeedDetail(token, postId).onSuccess {
                post = it
                liked = it.likedByMe
                likeCount = it.likeCount
                val currentBookId = it.bookId
                if (!currentBookId.isNullOrBlank()) {
                    ApiService.getSharedBookDetail(token, currentBookId).onSuccess { detail ->
                        sharedBook = detail
                    }
                }
            }
            ApiService.getComments(token, postId).onSuccess {
                comments.clear()
                comments.addAll(it)
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(text = post?.authorName ?: "", fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = StoryVenueColors.OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StoryVenueColors.Background),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoryVenueColors.Primary)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("댓글 입력", color = Color.White.copy(alpha = 0.7f), fontFamily = SBAggroFamily) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = StoryVenueColors.Primary,
                        unfocusedContainerColor = StoryVenueColors.Primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    if (commentText.isNotBlank()) {
                        val text = commentText
                        commentText = ""
                        scope.launch(Dispatchers.IO) {
                            ApiService.createComment(token, postId, text).onSuccess {
                                comments.add(it)
                            }
                        }
                    }
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = Color.White)
                }
            }
        }
    ) { innerPadding ->
        if (isLoading || post == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StoryVenueColors.Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
            ) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(text = post!!.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.Primary, fontFamily = SBAggroFamily)
                    formatBookCreatedAt(sharedBook?.createdAt)?.let { createdLabel ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = createdLabel,
                            fontSize = 13.sp,
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            val currentBook = sharedBook
                        if (currentBook != null) {
                            val autobiographyBody = buildSharedAutobiographyBody(currentBook)
                            Column(modifier = Modifier.padding(20.dp)) {
                                currentBook.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        fontSize = 16.sp,
                                        color = StoryVenueColors.SubText,
                                        fontFamily = SBAggroFamily,
                                        lineHeight = 26.sp,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                                Text(
                                    text = autobiographyBody.ifBlank { post!!.preview },
                                    fontSize = 17.sp,
                                    color = StoryVenueColors.OnSurface,
                                    fontFamily = SBAggroFamily,
                                    lineHeight = 28.sp,
                                )
                            }
                        } else {
                            Text(
                                text = post!!.preview,
                                fontSize = 17.sp,
                                color = StoryVenueColors.OnSurface,
                                fontFamily = SBAggroFamily,
                                lineHeight = 28.sp,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    ApiService.toggleLike(token, postId).onSuccess { (newLiked, newCount) ->
                                        liked = newLiked
                                        likeCount = newCount
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "좋아요",
                                tint = if (liked) StoryVenueColors.Error else StoryVenueColors.SubText,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(text = "좋아요 $likeCount", fontSize = 17.sp, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(text = "|", fontSize = 17.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "대화",
                            fontSize = 17.sp,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                post?.authorId?.takeIf { it.isNotBlank() }?.let { onChatClick(it) }
                            },
                        )
                    }

                    val isMyPost = myUserId.isNotBlank() && post?.authorId == myUserId
                    val currentBook = sharedBook
                    if (isMyPost && currentBook != null && !post?.bookId.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                shareBookTextToKakaoTalk(
                                    context = context,
                                    title = currentBook.title,
                                    subtitle = currentBook.subtitle,
                                    body = buildSharedAutobiographyBody(currentBook),
                                )
                            },
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFE300),
                                contentColor = Color(0xFF3C1E1E),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text(
                                text = "카카오톡으로 공유",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SBAggroFamily,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { isPdfSheetOpen = true },
                            enabled = !isExportingPdf,
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
                                text = "PDF로 내보내기",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SBAggroFamily,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "댓글 ${comments.size}개", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.height(1.dp).weight(1f).background(StoryVenueColors.Divider))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                items(comments.toList()) { comment ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        AvatarCircle(
                            name = comment.authorName,
                            avatarUrl = comment.authorAvatarUrl,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = comment.authorName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                                Spacer(Modifier.width(8.dp))
                                Text(text = comment.timeAgo, fontSize = 14.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(text = comment.content, fontSize = 16.sp, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        if (isPdfSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (!isExportingPdf) {
                        isPdfSheetOpen = false
                    }
                },
                sheetState = pdfSheetState,
                containerColor = StoryVenueColors.Background,
            ) {
                PdfExportSheetContent(
                    includeCover = pdfIncludeCover,
                    onIncludeCoverChange = { enabled ->
                        pdfIncludeCover = enabled
                        if (!enabled) {
                            pdfCoverBytes = null
                            pdfCoverMime = null
                            pdfCoverBitmap = null
                        }
                    },
                    coverBitmap = pdfCoverBitmap,
                    onPickCoverImage = {
                        pdfCoverPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearCoverImage = {
                        pdfCoverBytes = null
                        pdfCoverMime = null
                        pdfCoverBitmap = null
                    },
                    shareMode = pdfShareMode,
                    onShareModeChange = { pdfShareMode = it },
                    isExporting = isExportingPdf,
                    onConfirm = { exportPdf() },
                )
            }
        }
    }
}
