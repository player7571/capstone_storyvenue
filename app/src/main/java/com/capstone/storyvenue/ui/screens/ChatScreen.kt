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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
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

data class ChatPartner(
    val userId: String,
    val userName: String,
    val avatarUrl: String? = null,
    val lastMessage: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
)

data class ChatMessage(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val time: String,
)

// ── 채팅 목록 화면 ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onPartnerClick: (ChatPartner) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val token = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
        .getString("access_token", "") ?: ""

    var partners by remember { mutableStateOf<List<ChatPartner>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasNotifBadge by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasChatBadge = partners.any { it.unreadCount > 0 }

    suspend fun loadPartners() {
        withContext(Dispatchers.IO) {
            ApiService.getChatPartners(token).onSuccess { partners = it }
            ApiService.getUnreadCount(token).onSuccess { hasNotifBadge = it > 0 }
        }
    }

    LaunchedEffect(Unit) {
        loadPartners()
        isLoading = false
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "대화",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 20.sp,
                    )
                },
                actions = {
                    BellIconButton(
                        hasBadge = hasNotifBadge,
                        onClick = onNotificationClick,
                        iconSize = 24.dp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StoryVenueColors.Background,
                ),
            )
        },
        bottomBar = {
            StoryBottomNavBar(
                selectedIndex = 2,
                hasChatBadge = hasChatBadge,
                onHomeClick = onHomeClick,
                onFeedClick = onFeedClick,
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
                        loadPartners()
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
                    if (partners.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Filled.Chat,
                                    title = "아직 대화 상대가 없어요",
                                    subtitle = "이야기에서 마음에 드는 사람에게 말을 걸어보세요.",
                                )
                            }
                        }
                    } else {
                        item { Spacer(Modifier.height(8.dp)) }
                        items(partners) { partner ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPartnerClick(partner) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarCircle(
                                name = partner.userName,
                                avatarUrl = partner.avatarUrl,
                                size = 48.dp,
                                fontSize = 20.sp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.userName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StoryVenueColors.OnSurface,
                                    fontFamily = SBAggroFamily,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = partner.lastMessage,
                                    fontSize = 15.sp,
                                    color = StoryVenueColors.SubText,
                                    fontFamily = SBAggroFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = partner.lastMessageTime,
                                fontSize = 14.sp,
                                color = StoryVenueColors.SubText,
                                fontFamily = SBAggroFamily,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                    }
                }
            }
        }
    }
}

// ── 대화방 화면 ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    otherUserId: String = "",
    partnerName: String = "",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val myUserId = prefs.getString("user_id", "") ?: ""
    val scope = rememberCoroutineScope()

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(otherUserId) {
        withContext(Dispatchers.IO) {
            ApiService.getMessages(token, otherUserId).onSuccess { data ->
                messages.clear()
                messages.addAll(data.map { msg ->
                    ChatMessage(
                        id = msg.id,
                        content = msg.content,
                        isMine = msg.senderId == myUserId,
                        time = msg.timeAgo,
                    )
                })
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = partnerName.ifBlank { "대화" },
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
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoryVenueColors.Background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "쪽지를 입력하세요...",
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = StoryVenueColors.Surface,
                        unfocusedContainerColor = StoryVenueColors.Surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val text = inputText
                            inputText = ""
                            scope.launch(Dispatchers.IO) {
                                ApiService.sendMessage(token, otherUserId, text).onSuccess { msg ->
                                    messages.add(
                                        ChatMessage(
                                            id = msg.id,
                                            content = msg.content,
                                            isMine = true,
                                            time = "방금",
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = if (inputText.isNotBlank()) StoryVenueColors.Primary else StoryVenueColors.SubText,
                    )
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = StoryVenueColors.Primary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages.toList()) { message ->
                    ChatBubble(message = message)
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (message.isMine) {
            Text(
                text = message.time,
                fontSize = 13.sp,
                color = StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isMine) 16.dp else 4.dp,
                        bottomEnd = if (message.isMine) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (message.isMine) StoryVenueColors.Primary else StoryVenueColors.Surface
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content,
                fontSize = 16.sp,
                color = if (message.isMine) Color.White else StoryVenueColors.OnSurface,
                fontFamily = SBAggroFamily,
            )
        }
        if (!message.isMine) {
            Text(
                text = message.time,
                fontSize = 13.sp,
                color = StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
