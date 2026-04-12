package com.capstone.storyvenue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors

data class NotificationItem(
    val id: String,
    val actorName: String,
    val message: String,
    val commentPreview: String? = null,
    val timeAgo: String,
    val isRead: Boolean = false,
    val type: String = "comment",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit = {},
    onNotificationClick: (NotificationItem) -> Unit = {},
    notifications: List<NotificationItem> = listOf(
        NotificationItem(
            id = "1",
            actorName = "이영희",
            message = "회원님의 글에 댓글을 남겼습니다.",
            commentPreview = "\"정말 감동적인 이야기네요!\"",
            timeAgo = "1시간 전",
            isRead = false,
            type = "comment",
        ),
        NotificationItem(
            id = "2",
            actorName = "박영수",
            message = "회원님의 글을 좋아합니다.",
            commentPreview = null,
            timeAgo = "2시간 전",
            isRead = true,
            type = "like",
        ),
        NotificationItem(
            id = "3",
            actorName = "김민준",
            message = "회원님의 글에 댓글을 남겼습니다.",
            commentPreview = "\"다음 이야기도 기대됩니다!\"",
            timeAgo = "3시간 전",
            isRead = true,
            type = "comment",
        ),
    ),
) {
    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "알림",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = StoryVenueColors.OnSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StoryVenueColors.Background,
                ),
            )
        }
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Text(
                    text = "아직 알림이 없습니다",
                    fontSize = 16.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                items(notifications) { notification ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNotificationClick(notification) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!notification.isRead)
                                StoryVenueColors.PrimaryLight.copy(alpha = 0.3f)
                            else
                                StoryVenueColors.Surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 프로필 아바타
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StoryVenueColors.Primary),
                            ) {
                                Text(
                                    text = notification.actorName.firstOrNull()?.toString() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontFamily = SBAggroFamily,
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = notification.actorName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StoryVenueColors.OnSurface,
                                        fontFamily = SBAggroFamily,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = notification.message,
                                    fontSize = 14.sp,
                                    color = StoryVenueColors.OnSurface,
                                    fontFamily = SBAggroFamily,
                                )
                                if (notification.commentPreview != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = notification.commentPreview,
                                        fontSize = 13.sp,
                                        color = StoryVenueColors.SubText,
                                        fontFamily = SBAggroFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "· ${notification.timeAgo}",
                                    fontSize = 12.sp,
                                    color = StoryVenueColors.SubText,
                                    fontFamily = SBAggroFamily,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}