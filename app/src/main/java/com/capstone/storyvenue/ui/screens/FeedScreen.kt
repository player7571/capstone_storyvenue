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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val authorName: String,
    val title: String,
    val preview: String,
    val likeCount: Int,
    val commentCount: Int,
    val timeAgo: String,
    val likedByMe: Boolean = false,
)

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
    val token = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
        .getString("access_token", "") ?: ""

    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ApiService.getFeed(token).onSuccess {
                posts = it
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoryVenueColors.Background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "StoryVenue",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = StoryVenueColors.Primary,
                    fontFamily = SBAggroFamily,
                )
                IconButton(onClick = onNotificationClick) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "알림",
                        tint = StoryVenueColors.OnSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        bottomBar = {
            StoryBottomNavBar(
                selectedIndex = 0,
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
        } else if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("아직 게시물이 없습니다", color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(StoryVenueColors.Primary),
                ) {
                    Text(
                        text = post.authorName.firstOrNull()?.toString() ?: "?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = SBAggroFamily,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(text = post.authorName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                Spacer(Modifier.weight(1f))
                Text(text = post.timeAgo, fontSize = 12.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
            }
            Spacer(Modifier.height(12.dp))
            Text(text = post.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
            Spacer(Modifier.height(6.dp))
            Text(text = post.preview, fontSize = 14.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Text(text = "$likeCount", fontSize = 13.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                Spacer(Modifier.width(16.dp))
                Text(text = "💬", fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text(text = "${post.commentCount}", fontSize = 13.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    postId: String = "",
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val token = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
        .getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<FeedPost?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    val comments = remember { mutableStateListOf<ApiService.CommentData>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(postId) {
        withContext(Dispatchers.IO) {
            ApiService.getFeedDetail(token, postId).onSuccess {
                post = it
                liked = it.likedByMe
                likeCount = it.likeCount
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
                title = { Text(text = post?.authorName ?: "", fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily, fontSize = 18.sp) },
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
                    Text(text = post!!.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.Primary, fontFamily = SBAggroFamily)
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Text(
                            text = post!!.preview,
                            fontSize = 15.sp,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(20.dp),
                        )
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
                        Text(text = "좋아요 $likeCount", fontSize = 15.sp, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(text = "|", fontSize = 15.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "채팅",
                            fontSize = 15.sp,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onChatClick() },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "댓글 ${comments.size}개", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.height(1.dp).weight(1f).background(StoryVenueColors.Divider))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                items(comments.toList()) { comment ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(StoryVenueColors.Primary),
                        ) {
                            Text(text = comment.authorName.firstOrNull()?.toString() ?: "?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = SBAggroFamily)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = comment.authorName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                                Spacer(Modifier.width(8.dp))
                                Text(text = comment.timeAgo, fontSize = 12.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(text = comment.content, fontSize = 14.sp, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
