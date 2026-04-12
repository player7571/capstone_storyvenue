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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors

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
    posts: List<FeedPost> = listOf(
        FeedPost("1", "김철수", "나의 첫 번째 이야기", "어린 시절 골목에서 뛰어놀던 그 시절이 가장 행복했다...", 12, 3, "2시간 전"),
        FeedPost("2", "정순복", "대학 생활 회고록", "캠퍼스에서 보낸 4년은 내 인생에서 가장 빛나는 시간이었다...", 24, 7, "3시간 전"),
        FeedPost("3", "이영희", "젊은 시절의 나", "고등학교 때와는 완전히 달라진 세상이 펼쳐졌다...", 8, 1, "5시간 전"),
        FeedPost("4", "박지민", "첫 직장의 기억", "처음 출근하던 날의 떨림은 아직도 생생하다...", 15, 4, "1일 전"),
    ),
    onPostClick: (FeedPost) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(posts) { post ->
                FeedPostCard(post = post, onClick = { onPostClick(post) })
                Spacer(Modifier.height(12.dp))
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    onClick: () -> Unit = {},
) {
    var likeCount by remember { mutableStateOf(post.likeCount) }
    var liked by remember { mutableStateOf(post.likedByMe) }

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
                        liked = !liked
                        likeCount = if (liked) likeCount + 1 else likeCount - 1
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "좋아요",
                        tint = StoryVenueColors.Error,
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
    post: FeedPost = FeedPost(
        id = "1",
        authorName = "김철수",
        title = "나의 첫 번째 이야기",
        preview = "어린 시절 골목에서 뛰어놀던 그 시절이 가장 행복했다...",
        likeCount = 12,
        commentCount = 3,
        timeAgo = "2시간 전",
    ),
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    var liked by remember { mutableStateOf(post.likedByMe) }
    var likeCount by remember { mutableStateOf(post.likeCount) }
    var commentText by remember { mutableStateOf("") }
    val comments = remember {
        mutableStateListOf(
            Triple("이영희", "정말 감동적인 이야기네요!", "1시간 전"),
            Triple("박지민", "다음 이야기도 기대됩니다", "30분 전"),
        )
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(text = post.authorName, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily, fontSize = 18.sp) },
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
                    placeholder = { Text("메세지 입력", color = Color.White.copy(alpha = 0.7f), fontFamily = SBAggroFamily) },
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
                        comments.add(Triple("나", commentText, "방금"))
                        commentText = ""
                    }
                }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = Color.White)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(text = post.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.Primary, fontFamily = SBAggroFamily)
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = "이야기 1 : 어린 시절\n\n${post.preview}\n\n여름이면 매미 소리가 온 동네를 가득 채웠고, 친구들과 골목을 누비던 기억이 아직도 선명하다. 그 시절의 순수함이 그립다.",
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
                            liked = !liked
                            likeCount = if (liked) likeCount + 1 else likeCount - 1
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "좋아요",
                            tint = StoryVenueColors.Error,
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
            items(comments) { comment ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(StoryVenueColors.Primary),
                    ) {
                        Text(text = comment.first.firstOrNull()?.toString() ?: "?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = SBAggroFamily)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = comment.first, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                            Spacer(Modifier.width(8.dp))
                            Text(text = comment.third, fontSize = 12.sp, color = StoryVenueColors.SubText, fontFamily = SBAggroFamily)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(text = comment.second, fontSize = 14.sp, color = StoryVenueColors.OnSurface, fontFamily = SBAggroFamily)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}