package com.capstone.storyvenue.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    enabled: Boolean = true,
) {
    val bgColor = when (variant) {
        ButtonVariant.Accent -> StoryVenueColors.Accent
        ButtonVariant.Secondary -> StoryVenueColors.Surface
        else -> StoryVenueColors.Primary
    }
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = bgColor.copy(alpha = 0.45f),
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
                fontSize = 18.sp,
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
                    fontSize = 18.sp,
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
                fontSize = 14.sp,
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
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(900),
        label = "splash",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(2000L)
        val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        if (token.isNullOrBlank()) {
            onNavigateToLogin()
            return@LaunchedEffect
        }

        val isValidToken = withContext(Dispatchers.IO) {
            ApiService.getProfile(token).isSuccess
        }

        if (isValidToken) {
            onNavigateToHome()
        } else {
            prefs.edit()
                .remove("access_token")
                .remove("refresh_token")
                .remove("user_id")
                .remove("user_name")
                .remove("user_email")
                .apply()
            onNavigateToLogin()
        }
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
                text = "이야기마당",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = SBAggroFamily,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "당신의 이야기를\n책으로 만들어요",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
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
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showKakaoWebView by remember { mutableStateOf(false) }
    var kakaoAuthorizeUrl by remember { mutableStateOf("") }
    var kakaoRedirectUri by remember { mutableStateOf("") }
    var kakaoState by remember { mutableStateOf("") }
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
                text = "이야기마당",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = StoryVenueColors.Primary,
                fontFamily = SBAggroFamily,
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch(Dispatchers.IO) {
                        val result = ApiService.getKakaoAuthorizeUrl()
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            result.onSuccess { authData ->
                                kakaoAuthorizeUrl = authData.authorizeUrl
                                kakaoRedirectUri = authData.redirectUri
                                kakaoState = authData.state
                                showKakaoWebView = true
                            }.onFailure { e ->
                                errorMessage = e.message
                            }
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEE500),
                    contentColor = Color(0xFF191919),
                    disabledContainerColor = Color(0xFFFEE500).copy(alpha = 0.55f),
                    disabledContentColor = Color(0xFF191919).copy(alpha = 0.7f),
                ),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF191919),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "카카오로 로그인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SBAggroFamily,
                    )
                }
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = errorMessage ?: "",
                    color = StoryVenueColors.Error,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = SBAggroFamily,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showKakaoWebView && kakaoAuthorizeUrl.isNotBlank() && kakaoRedirectUri.isNotBlank()) {
        KakaoAuthWebViewDialog(
            authorizeUrl = kakaoAuthorizeUrl,
            redirectUri = kakaoRedirectUri,
            expectedState = kakaoState,
            onClose = { showKakaoWebView = false },
            onAuthError = { message ->
                showKakaoWebView = false
                errorMessage = message
            },
            onAuthCodeReceived = { code, state ->
                showKakaoWebView = false
                isLoading = true
                errorMessage = null
                scope.launch(Dispatchers.IO) {
                    val result = ApiService.loginWithKakaoCode(code, state)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        result.onSuccess { session ->
                            context.getSharedPreferences("storyvenue", Activity.MODE_PRIVATE)
                                .edit()
                                .putString("access_token", session.accessToken)
                                .putString("refresh_token", session.refreshToken)
                                .putString("user_id", session.userId)
                                .putString("user_name", session.name)
                                .putString("user_email", session.email)
                                .apply()
                            onLoginSuccess()
                        }.onFailure { e ->
                            errorMessage = e.message
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun KakaoAuthWebViewDialog(
    authorizeUrl: String,
    redirectUri: String,
    expectedState: String,
    onClose: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCodeReceived: (String, String) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = StoryVenueColors.Background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            var handled = false

                            fun tryHandleCallback(url: String?): Boolean {
                                val targetUrl = url?.trim().orEmpty()
                                if (handled || targetUrl.isBlank()) return false
                                if (!targetUrl.startsWith(redirectUri)) return false

                                handled = true
                                val (code, state, errorMessage) = parseKakaoCallbackUrl(targetUrl)
                                when {
                                    !errorMessage.isNullOrBlank() ->
                                        onAuthError("카카오 인증에 실패했습니다: $errorMessage")
                                    state.isNullOrBlank() || state != expectedState ->
                                        onAuthError("카카오 인증 state 검증에 실패했습니다.")
                                    !code.isNullOrBlank() -> onAuthCodeReceived(code, state)
                                    else -> onAuthError("카카오 인증 코드를 확인할 수 없습니다.")
                                }
                                return true
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = tryHandleCallback(request?.url?.toString())

                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                    tryHandleCallback(url)

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    if (!tryHandleCallback(url)) {
                                        super.onPageStarted(view, url, favicon)
                                    }
                                }
                            }

                            loadUrl(authorizeUrl)
                        }
                    },
                )

                TextButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp),
                ) {
                    Text(
                        text = "닫기",
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }
            }
        }
    }
}

private fun parseKakaoCallbackUrl(callbackUrl: String): Triple<String?, String?, String?> {
    return try {
        val uri = Uri.parse(callbackUrl)
        val code = uri.getQueryParameter("code")?.trim().orEmpty().ifBlank { null }
        val state = uri.getQueryParameter("state")?.trim().orEmpty().ifBlank { null }
        val error = (
            uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("error")
                ?: uri.getQueryParameter("error_reason")
            ).orEmpty().trim().ifBlank { null }
        Triple(code, state, error)
    } catch (_: Exception) {
        Triple(null, null, "카카오 인증 응답 파싱에 실패했습니다.")
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
                        fontSize = 20.sp,
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
                Text(text = serverError!!, color = StoryVenueColors.Error, fontSize = 15.sp, fontFamily = SBAggroFamily)
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SBAggroFamily,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
