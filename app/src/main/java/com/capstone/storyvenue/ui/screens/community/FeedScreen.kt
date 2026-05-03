package com.capstone.storyvenue.ui.screens

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
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
    var hasNotifBadge by remember { mutableStateOf(false) }
    var hasChatBadge by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadFeed() {
        withContext(Dispatchers.IO) {
            ApiService.getFeed(token).onSuccess { posts = it }
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
                        loadFeed()
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
                    if (posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    title = "아직 올라온 이야기가 없어요",
                                    subtitle = "첫 이야기를 올려 사람들과 나눠보세요.",
                                )
                            }
                        }
                    } else {
                        item { Spacer(Modifier.height(8.dp)) }
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
    val token = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
        .getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<FeedPost?>(null) }
    var sharedBook by remember { mutableStateOf<BookDetailData?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<ApiService.CommentData>() }
    var isLoading by remember { mutableStateOf(true) }

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
    }
}
