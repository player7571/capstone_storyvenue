package com.capstone.storyvenue.ui.screens.auth

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
import androidx.compose.material.icons.automirrored.filled.Send
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

data class ChatPartner(
    val userId: String,
    val userName: String,
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
    partners: List<ChatPartner> = listOf(
        ChatPartner("1", "김철수", "안녕하세요! 글 잘 읽었어요", "오후 3:42"),
        ChatPartner("2", "이영희", "감사합니다~", "오전 11:20"),
        ChatPartner("3", "박지민", "혹시 다음 이야기도 올리...", "어제"),
    ),
    onPartnerClick: (ChatPartner) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "채팅",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                actions = {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "알림",
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
            StoryBottomNavBar(
                selectedIndex = 2,
                onHomeClick = onHomeClick,
                onFeedClick = onFeedClick,
                onProfileClick = onProfileClick,
            )
        }
    ) { innerPadding ->
        if (partners.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "피드에서 새로운 사람들과",
                        fontSize = 16.sp,
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                    Text(
                        text = "채팅으로 연결해보세요!",
                        fontSize = 16.sp,
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(StoryVenueColors.Primary),
                            ) {
                                Text(
                                    text = partner.userName.firstOrNull()?.toString() ?: "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = SBAggroFamily,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partner.userName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StoryVenueColors.OnSurface,
                                    fontFamily = SBAggroFamily,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = partner.lastMessage,
                                    fontSize = 13.sp,
                                    color = StoryVenueColors.SubText,
                                    fontFamily = SBAggroFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = partner.lastMessageTime,
                                fontSize = 12.sp,
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

// ── 채팅방 화면 ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    partnerName: String = "김철수",
    onBack: () -> Unit = {},
) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage("1", "안녕하세요! 글 잘 읽었어요", false, "오후 3:42"),
            ChatMessage("2", "감사합니다!", true, "오후 3:42"),
            ChatMessage("3", "혹시 다음 이야기는 언제 올리시나요?", false, "오후 3:45"),
            ChatMessage("4", "이번 주 안에 올릴 예정이에요!", true, "오후 3:46"),
        )
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = partnerName,
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
                            "메시지를 입력하세요...",
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
                            messages.add(
                                ChatMessage(
                                    id = messages.size.toString(),
                                    content = inputText,
                                    isMine = true,
                                    time = "방금",
                                )
                            )
                            inputText = ""
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages) { message ->
                ChatBubble(message = message)
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(8.dp)) }
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
                fontSize = 11.sp,
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
                fontSize = 14.sp,
                color = if (message.isMine) Color.White else StoryVenueColors.OnSurface,
                fontFamily = SBAggroFamily,
            )
        }
        if (!message.isMine) {
            Text(
                text = message.time,
                fontSize = 11.sp,
                color = StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}