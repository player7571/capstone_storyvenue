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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

data class NotificationItem(
    val id: String,
    val actorName: String,
    val actorAvatarUrl: String? = null,
    val message: String,
    val commentPreview: String? = null,
    val timeAgo: String,
    val isRead: Boolean = false,
    val type: String = "comment",
    val postId: String? = null,
    val chatPartnerId: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit = {},
    onNotificationClick: (NotificationItem) -> Unit = {},
) {
    val context = LocalContext.current
    val token = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
        .getString("access_token", "") ?: ""
    val scope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadNotifications() {
        withContext(Dispatchers.IO) {
            val normal = ApiService.getNotifications(token).getOrNull().orEmpty()
            val partners = ApiService.getChatPartners(token).getOrNull().orEmpty()
            val chatItems = partners
                .filter { it.unreadCount > 0 }
                .map { p ->
                    NotificationItem(
                        id = "chat:${p.userId}",
                        actorName = p.userName,
                        actorAvatarUrl = p.avatarUrl,
                        message = if (p.unreadCount > 1)
                            "새 메시지 ${p.unreadCount}개"
                        else "새 메시지가 도착했어요",
                        commentPreview = p.lastMessage.ifBlank { null },
                        timeAgo = p.lastMessageTime,
                        isRead = false,
                        type = "chat",
                        chatPartnerId = p.userId,
                    )
                }
            notifications = chatItems + normal
        }
    }

    LaunchedEffect(Unit) {
        loadNotifications()
        isLoading = false
    }

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
                        fontSize = 20.sp,
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
                        loadNotifications()
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
                    if (notifications.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.Filled.NotificationsNone,
                                    title = "아직 새 소식이 없어요",
                                    subtitle = "누군가 당신의 이야기에 반응하면 여기에 표시돼요.",
                                )
                            }
                        }
                    } else {
                        item { Spacer(Modifier.height(8.dp)) }

                        items(notifications) { notification ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 채팅 타입은 서버 알림 테이블에 없으므로 읽음 처리 생략
                                if (!notification.isRead && notification.type != "chat") {
                                    scope.launch(Dispatchers.IO) {
                                        ApiService.markNotificationRead(token, notification.id)
                                    }
                                    notifications = notifications.map {
                                        if (it.id == notification.id) it.copy(isRead = true) else it
                                    }
                                }
                                onNotificationClick(notification)
                            },
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
                            AvatarCircle(
                                name = notification.actorName,
                                avatarUrl = notification.actorAvatarUrl,
                                size = 44.dp,
                                fontSize = 20.sp,
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = notification.actorName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StoryVenueColors.OnSurface,
                                        fontFamily = SBAggroFamily,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = notification.message,
                                    fontSize = 16.sp,
                                    color = StoryVenueColors.OnSurface,
                                    fontFamily = SBAggroFamily,
                                )
                                if (notification.commentPreview != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = notification.commentPreview,
                                        fontSize = 15.sp,
                                        color = StoryVenueColors.SubText,
                                        fontFamily = SBAggroFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "· ${notification.timeAgo}",
                                    fontSize = 14.sp,
                                    color = StoryVenueColors.SubText,
                                    fontFamily = SBAggroFamily,
                                )
                            }

                            IconButton(
                                onClick = {
                                    val removed = notification
                                    notifications = notifications.filterNot { it.id == removed.id }
                                    if (removed.type != "chat") {
                                        scope.launch(Dispatchers.IO) {
                                            ApiService.deleteNotification(token, removed.id)
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "알림 삭제",
                                    tint = StoryVenueColors.SubText,
                                    modifier = Modifier.size(18.dp),
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
    }
}
