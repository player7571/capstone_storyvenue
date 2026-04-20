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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InterviewSession(
    val id: String,
    val number: Int,
    val date: String,
    val title: String,
    val sessionType: String? = null,
    val status: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewInterview: () -> Unit = {},
    onSessionClick: (InterviewSession) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""

    var userName by remember { mutableStateOf("이름") }
    var sessions by remember { mutableStateOf<List<InterviewSession>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun loadHome() {
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        val profileResult = withContext(Dispatchers.IO) { ApiService.getProfile(token) }
        profileResult.onSuccess { userName = it.name.ifBlank { "이름" } }
            .onFailure { e -> errorMessage = e.message ?: "내정보를 불러오지 못했습니다." }

        val sessionsResult = withContext(Dispatchers.IO) { ApiService.getSessions(token) }
        sessionsResult.onSuccess {
            sessions = it
            errorMessage = null
        }.onFailure { e -> errorMessage = e.message ?: "문답 목록을 불러오지 못했습니다." }
    }

    LaunchedEffect(token) { loadHome() }

    LaunchedEffect(errorMessage) {
        val msg = errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            errorMessage = null
        }
    }
    Scaffold(
        containerColor = StoryVenueColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            StoryBottomNavBar(
                selectedIndex = 1,
                onHomeClick = {},
                onFeedClick = onFeedClick,
                onChatClick = onChatClick,
                onProfileClick = onProfileClick,
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadHome()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "안녕하세요, ${userName}님",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
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
                Spacer(Modifier.height(20.dp))
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable { onNewInterview() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "새 문답 시작",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = SBAggroFamily,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "당신의 이야기를\n들려주세요.",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = SBAggroFamily,
                                lineHeight = 24.sp,
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        ) {
                            Text(text = "🎤", fontSize = 42.sp)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "이전 문답",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.Primary,
                        fontFamily = SBAggroFamily,
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(1f)
                            .background(StoryVenueColors.Primary.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            items(sessions) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSessionClick(session) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "문답 #${session.number}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = StoryVenueColors.OnSurface,
                                fontFamily = SBAggroFamily,
                            )
                            Text(
                                text = "  |  ${session.date}",
                                fontSize = 16.sp,
                                color = StoryVenueColors.SubText,
                                fontFamily = SBAggroFamily,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = session.title,
                            fontSize = 16.sp,
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                        )
                        val typeLabel = when (session.sessionType) {
                            "photo" -> "사진 문답"
                            "voice" -> "음성 문답"
                            else -> null
                        }
                        val statusLabel = when (session.status) {
                            "completed" -> "완료"
                            "in_progress", "ongoing" -> "진행 중"
                            else -> null
                        }
                        if (typeLabel != null || statusLabel != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (typeLabel != null) {
                                    SessionChip(text = typeLabel, color = StoryVenueColors.Primary)
                                }
                                if (statusLabel != null) {
                                    if (typeLabel != null) Spacer(Modifier.width(6.dp))
                                    val statusColor = if (session.status == "completed")
                                        StoryVenueColors.SubText else StoryVenueColors.Accent
                                    SessionChip(text = statusLabel, color = statusColor)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
        }
    }
}

@Composable
private fun SessionChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        fontFamily = SBAggroFamily,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun StoryBottomNavBar(
    selectedIndex: Int = 0,
    onHomeClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val items = listOf("이야기", "글쓰기", "대화", "내정보")
    val icons: List<ImageVector> = listOf(
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.Filled.Edit,
        Icons.AutoMirrored.Filled.Chat,
        Icons.Filled.Person,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StoryVenueColors.Background)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEachIndexed { index, label ->
            val tint = if (index == selectedIndex) StoryVenueColors.Primary else StoryVenueColors.SubText
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        when (index) {
                            0 -> onFeedClick()
                            1 -> onHomeClick()
                            2 -> onChatClick()
                            3 -> onProfileClick()
                        }
                    },
            ) {
                Icon(
                    imageVector = icons[index],
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = tint,
                    fontFamily = SBAggroFamily,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
