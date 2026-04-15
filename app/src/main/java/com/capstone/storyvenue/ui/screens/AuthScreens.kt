package com.capstone.storyvenue.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ButtonVariant { Primary, Accent, Secondary }

@Composable
fun StoryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    isLoading: Boolean = false,
) {
    val bgColor = when (variant) {
        ButtonVariant.Accent -> StoryVenueColors.Accent
        ButtonVariant.Secondary -> StoryVenueColors.Surface
        else -> StoryVenueColors.Primary
    }
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = bgColor,
        ),
        shape = RoundedCornerShape(50.dp),
        modifier = modifier.fillMaxWidth().height(60.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(
                text = text,
                color = if (variant == ButtonVariant.Secondary) StoryVenueColors.OnSurface else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SBAggroFamily,
            )
        }
    }
}

@Composable
fun StoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    label,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    fontSize = 16.sp,
                )
            },
            singleLine = true,
            enabled = enabled,
            isError = errorMessage != null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = StoryVenueColors.SubText,
                        )
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = StoryVenueColors.Surface,
                unfocusedContainerColor = StoryVenueColors.Surface,
                disabledContainerColor = StoryVenueColors.Divider,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                errorContainerColor = StoryVenueColors.Surface,
            ),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.fillMaxWidth().height(63.dp),
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = StoryVenueColors.Error,
                fontSize = 12.sp,
                fontFamily = SBAggroFamily,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun ProfileAvatar(name: String, sizeDp: Int = 40, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull()?.toString() ?: "?"
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(StoryVenueColors.Surface)
            .border(1.dp, StoryVenueColors.Divider, CircleShape),
    ) {
        Text(
            text = initial,
            color = StoryVenueColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp / 2.5).sp,
            fontFamily = SBAggroFamily,
        )
    }
}

// ── 스플래시 ───────────────────────────────────

@Composable
fun SplashScreen(
    hasToken: Boolean,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(900),
        label = "splash",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(2000L)
        if (hasToken) onNavigateToHome() else onNavigateToLogin()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(StoryVenueColors.Primary),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            Text(
                text = "StoryVenue",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = SBAggroFamily,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "당신의 이야기를\n책으로 만들어요",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                fontFamily = SBAggroFamily,
            )
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// ── 로그인 ─────────────────────────────────────

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(StoryVenueColors.Background).imePadding()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(80.dp))
            Text(
                text = "StoryVenue",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = StoryVenueColors.Primary,
                fontFamily = SBAggroFamily,
            )
            Spacer(Modifier.height(48.dp))
            StoryTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = "이메일",
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(20.dp))
            StoryTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = "비밀번호",
                isPassword = true,
                errorMessage = errorMessage,
            )
            Spacer(Modifier.height(28.dp))
            StoryButton(
                text = "로그인",
                isLoading = isLoading,
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "이메일과 비밀번호를 입력해주세요"
                        return@StoryButton
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch(Dispatchers.IO) {
                        val result = ApiService.login(email, password)
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            result.onSuccess { (token, userId) ->
                                context.getSharedPreferences("storyvenue", Activity.MODE_PRIVATE)
                                    .edit()
                                    .putString("access_token", token)
                                    .putString("user_id", userId)
                                    .apply()
                                onLoginSuccess()
                            }.onFailure { e ->
                                errorMessage = e.message
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text(
                    text = "회원가입으로 이동",
                    color = StoryVenueColors.SubText,
                    fontSize = 14.sp,
                    fontFamily = SBAggroFamily,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── 회원가입 ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val emailError = if (email.isNotBlank() && !email.contains("@")) "올바른 이메일을 입력해주세요!" else null
    val passwordError = if (password.isNotBlank() && password.length < 6) "비밀번호는 6자 이상이어야 합니다" else null
    val passwordConfirmError = if (passwordConfirm.isNotBlank() && password != passwordConfirm) "비밀번호가 일치하지 않습니다!" else null

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "회원가입",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .imePadding(),
        ) {
            Spacer(Modifier.height(20.dp))
            StoryTextField(
                value = name,
                onValueChange = { name = it },
                label = "이름",
            )
            Spacer(Modifier.height(20.dp))
            StoryTextField(
                value = email,
                onValueChange = { email = it },
                label = "이메일",
                keyboardType = KeyboardType.Email,
                errorMessage = emailError,
            )
            Spacer(Modifier.height(20.dp))
            StoryTextField(
                value = password,
                onValueChange = { password = it },
                label = "비밀번호",
                isPassword = true,
                errorMessage = passwordError,
            )
            Spacer(Modifier.height(20.dp))
            StoryTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                label = "비밀번호 확인",
                isPassword = true,
                errorMessage = passwordConfirmError,
            )
            if (serverError != null) {
                Spacer(Modifier.height(8.dp))
                Text(text = serverError!!, color = StoryVenueColors.Error, fontSize = 13.sp, fontFamily = SBAggroFamily)
            }
            Spacer(Modifier.height(32.dp))
            StoryButton(
                text = "회원가입",
                isLoading = isLoading,
                onClick = {
                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                        serverError = "모든 항목을 입력해주세요"
                        return@StoryButton
                    }
                    if (emailError != null || passwordError != null || passwordConfirmError != null) return@StoryButton
                    isLoading = true
                    serverError = null
                    scope.launch(Dispatchers.IO) {
                        val result = ApiService.signup(name, email, password)
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            result.onSuccess { onSignUpSuccess() }
                                .onFailure { e -> serverError = e.message }
                        }
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onNavigateToLogin) {
                    Text(
                        "이미 계정이 있어요.",
                        color = StoryVenueColors.SubText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SBAggroFamily,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}