package com.capstone.storyvenue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onMyPosts: () -> Unit = {},
    onLikedPosts: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFeedClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("storyvenue", android.content.Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var isSavingEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) { ApiService.getProfile(token) }
        result.onSuccess {
            userName = it.name
            userEmail = it.email
            editName = it.name
        }.onFailure { e ->
            errorMessage = e.message ?: "프로필을 불러오지 못했습니다."
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "로그아웃",
                    fontFamily = SBAggroFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "정말 로그아웃 하시겠어요?",
                    fontFamily = SBAggroFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    prefs.edit().clear().apply()
                    onLogout()
                }) {
                    Text(
                        text = "로그아웃",
                        color = StoryVenueColors.Error,
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "취소",
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }
            },
            containerColor = StoryVenueColors.Background,
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) showEditDialog = false },
            title = {
                Text(
                    text = "프로필 수정",
                    fontFamily = SBAggroFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("이름", fontFamily = SBAggroFamily) },
                        singleLine = true,
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = StoryVenueColors.Error,
                            fontFamily = SBAggroFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nextName = editName.trim()
                        if (nextName.isBlank()) {
                            errorMessage = "이름을 입력해주세요."
                            return@TextButton
                        }
                        isSavingEdit = true
                        errorMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ApiService.updateProfileName(token, nextName)
                            }
                            isSavingEdit = false
                            result.onSuccess {
                                userName = it.name
                                userEmail = it.email
                                showEditDialog = false
                                infoMessage = "프로필이 수정되었습니다."
                            }.onFailure { e ->
                                errorMessage = e.message ?: "프로필 수정에 실패했습니다."
                            }
                        }
                    },
                    enabled = !isSavingEdit,
                ) {
                    Text(
                        text = if (isSavingEdit) "저장 중..." else "저장",
                        color = StoryVenueColors.Primary,
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        errorMessage = null
                    },
                    enabled = !isSavingEdit,
                ) {
                    Text(
                        text = "취소",
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }
            },
            containerColor = StoryVenueColors.Background,
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text(
                    text = "회원탈퇴",
                    fontFamily = SBAggroFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "계정을 삭제하면 복구할 수 없습니다. 정말 탈퇴하시겠어요?",
                    fontFamily = SBAggroFamily,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        errorMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { ApiService.deleteMe(token) }
                            isDeleting = false
                            result.onSuccess {
                                prefs.edit().clear().apply()
                                showDeleteDialog = false
                                onLogout()
                            }.onFailure { e ->
                                errorMessage = e.message ?: "회원탈퇴 처리에 실패했습니다."
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = !isDeleting,
                ) {
                    Text(
                        text = if (isDeleting) "처리 중..." else "탈퇴하기",
                        color = StoryVenueColors.Error,
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                ) {
                    Text(
                        text = "취소",
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }
            },
            containerColor = StoryVenueColors.Background,
        )
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "내 프로필",
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
                selectedIndex = 3,
                onHomeClick = onHomeClick,
                onFeedClick = onFeedClick,
                onChatClick = onChatClick,
                onProfileClick = {},
            )
        }
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(StoryVenueColors.Primary)
                    .border(3.dp, StoryVenueColors.PrimaryLight, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = StoryVenueColors.OnSurface,
                fontFamily = SBAggroFamily,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = userEmail,
                fontSize = 14.sp,
                color = StoryVenueColors.OnSurface,
                fontFamily = SBAggroFamily,
                fontWeight = FontWeight.Bold,
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    fontSize = 13.sp,
                    color = StoryVenueColors.Error,
                    fontFamily = SBAggroFamily,
                )
            }

            if (!infoMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = infoMessage ?: "",
                    fontSize = 13.sp,
                    color = StoryVenueColors.Primary,
                    fontFamily = SBAggroFamily,
                )
            }

            Spacer(Modifier.height(32.dp))

            ProfileMenuItem(text = "내가 쓴 글", onClick = onMyPosts, textColor = StoryVenueColors.Primary)
            Spacer(Modifier.height(12.dp))
            ProfileMenuItem(text = "좋아요한 글", onClick = onLikedPosts, textColor = StoryVenueColors.Primary)
            Spacer(Modifier.height(12.dp))
            ProfileMenuItem(
                text = "프로필 수정",
                onClick = {
                    onEditProfile()
                    editName = userName
                    infoMessage = null
                    errorMessage = null
                    showEditDialog = true
                },
                textColor = StoryVenueColors.Primary,
            )
            Spacer(Modifier.height(12.dp))
            ProfileMenuItem(text = "로그아웃", onClick = { showLogoutDialog = true }, textColor = StoryVenueColors.Accent)
            Spacer(Modifier.height(12.dp))
            ProfileMenuItem(text = "회원탈퇴", onClick = { showDeleteDialog = true }, textColor = StoryVenueColors.Error)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProfileMenuItem(
    text: String,
    onClick: () -> Unit,
    textColor: Color = StoryVenueColors.Primary,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = SBAggroFamily,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = StoryVenueColors.SubText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
