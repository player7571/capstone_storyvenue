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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InterviewSession(
    val id: String,
    val number: Int,
    val date: String,
    val title: String,
)

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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ApiService.getProfile(token).onSuccess { userName = it.name.ifBlank { "이름" } }
            ApiService.getSessions(token).onSuccess { sessions = it }
        }
    }
    Scaffold(
        containerColor = StoryVenueColors.Background,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "안녕하세요, ${userName}님",
                        fontSize = 20.sp,
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
                                text = "새 인터뷰 시작",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = SBAggroFamily,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "당신의 이야기를\n들려주세요.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = SBAggroFamily,
                                lineHeight = 22.sp,
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        ) {
                            Text(text = "🎤", fontSize = 40.sp)
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
                        text = "이전 인터뷰",
                        fontSize = 16.sp,
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
                                text = "인터뷰 #${session.number}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = StoryVenueColors.OnSurface,
                                fontFamily = SBAggroFamily,
                            )
                            Text(
                                text = "  |  ${session.date}",
                                fontSize = 14.sp,
                                color = StoryVenueColors.SubText,
                                fontFamily = SBAggroFamily,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = session.title,
                            fontSize = 14.sp,
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun StoryBottomNavBar(
    selectedIndex: Int = 0,
    onHomeClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val items = listOf("피드", "홈", "채팅", "프로필")
    val icons = listOf("📖", "🏠", "💬", "👤")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StoryVenueColors.Background)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEachIndexed { index, label ->
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
                Text(text = icons[index], fontSize = 22.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (index == selectedIndex) StoryVenueColors.Primary else StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}