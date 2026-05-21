package com.capstone.storyvenue.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun defaultVoiceInterviewPrompt() = InterviewPromptData(
    currentQuestionNo = 1,
    totalQuestions = 10,
    mainQuestion = "어릴 적 살던 곳과 집안 분위기는 어떠했나요?",
    questionHint = "집, 가족, 동네 모습 중 떠오르는 것부터 말씀해주세요.",
    followUpCount = 0,
    questionStatus = "main",
    progressPercent = 10,
    isInterviewComplete = false,
    currentQuestionHasAnswer = false,
    currentQuestionAnswerCount = 0,
    currentQuestionStoryReady = false,
    currentQuestionStoryQuality = "none",
    storyTargetQuestionNo = null,
    storyTargetHasAnswer = false,
    storyTargetAnswerCount = 0,
    storyTargetStoryReady = false,
    storyTargetStoryQuality = "none",
    storyTargetIsCurrentQuestion = true,
)

private fun buildVoiceSessionTitle(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    return "음성 문답 ${formatter.format(Date())}"
}

private const val MIN_RECORDING_SECONDS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInterviewScreen(
    initialSessionId: String? = null,
    onBack: () -> Unit = {},
    onGenerateChapter: (String, Int?, String?, Boolean) -> Unit = { _, _, _, _ -> },
    onOpenAutobiography: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""
    val viewModel: VoiceInterviewViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    val sessionId = uiState.sessionId
    val activePhotoUrl = uiState.activePhotoUrl
    val activePhotoLinkedQuestionNo = uiState.activePhotoLinkedQuestionNo
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val userText = uiState.userText
    val interviewPrompt = uiState.interviewPrompt
    val assistantText = uiState.assistantText
    val latestAudioUrl = uiState.latestAudioUrl
    val latestAudioStatus = uiState.latestAudioStatus
    val latestAudioId = uiState.latestAudioId
    val lastRecordedFileName = uiState.lastRecordedFileName
    val recordingSeconds = uiState.recordingSeconds
    val snackbarHostState = remember { SnackbarHostState() }
    val isPreparingSession = uiState.isPreparingSession
    val isUploadingPhoto = uiState.isUploadingPhoto
    val isRecording = uiState.isRecording
    val isUploadingAudio = uiState.isUploadingAudio
    val isSubmittingText = uiState.isSubmittingText
    val isNavigatingPrevious = uiState.isNavigatingPrevious
    val isNavigatingNext = uiState.isNavigatingNext
    val isPlayingAudio = uiState.isPlayingAudio
    val recorderController = remember { AudioRecorderController() }
    val playerController = remember { AudioPlayerController() }
    var showBasicStoryDialog by remember { mutableStateOf(false) }
    var autoPlayedAudioUrl by remember { mutableStateOf<String?>(null) }

    fun loadPhotoThumbnail(url: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { ApiService.fetchImageBytes(url) }
            result.onSuccess { bytes ->
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()?.let { photoBitmap = it }
            }
        }
    }

    fun playAssistantAudio(url: String) {
        playerController.play(
            url = url,
            onPrepared = { viewModel.setAudioPlaying(true) },
            onCompleted = { viewModel.setAudioPlaying(false) },
            onError = {
                viewModel.setAudioPlaying(false)
                scope.launch {
                    snackbarHostState.showSnackbar("AI 음성 재생에 실패했습니다.")
                }
            },
        )
    }

    fun beginRecordingAfterSession(currentSessionId: String) {
        recorderController.start(context)
            .onSuccess { file ->
                viewModel.onRecordingStarted(file.name)
            }
            .onFailure {
                recorderController.release(deleteTempFile = true)
                scope.launch {
                    snackbarHostState.showSnackbar("녹음을 시작하지 못했습니다. 마이크 권한 및 기기 상태를 확인해주세요.")
                }
            }
    }

    fun startRecording() {
        if (token.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("로그인이 필요합니다.") }
            return
        }
        if (isPlayingAudio) {
            scope.launch { snackbarHostState.showSnackbar("AI 음성 재생이 끝난 뒤 녹음을 시작해주세요.") }
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isRecording || isUploadingPhoto) return

        val currentSessionId = sessionId
        if (!currentSessionId.isNullOrBlank()) {
            beginRecordingAfterSession(currentSessionId)
            return
        }

        // 세션이 아직 없으면 음성 세션을 먼저 생성한 뒤 바로 녹음 시작
        scope.launch {
            val title = buildVoiceSessionTitle()
            val result = viewModel.ensureVoiceSession(token = token, title = title)
            result.onSuccess { created ->
                beginRecordingAfterSession(created)
            }
        }
    }

    fun stopRecordingAndUpload() {
        val currentSessionId = sessionId
        val file = recorderController.stop(deleteTempFile = false)
        if (currentSessionId.isNullOrBlank() || file == null) {
            recorderController.release(deleteTempFile = true)
            viewModel.onRecordingStopped()
            scope.launch { snackbarHostState.showSnackbar("녹음 파일을 찾을 수 없습니다.") }
            return
        }
        viewModel.onRecordingStopped()

        if (recordingSeconds < MIN_RECORDING_SECONDS) {
            runCatching { file.delete() }
            scope.launch {
                snackbarHostState.showSnackbar("답변이 너무 짧아요. 조금 더 천천히 말씀해주세요.")
            }
            return
        }

        scope.launch {
            if (!file.exists() || file.length() == 0L) {
                runCatching { file.delete() }
                snackbarHostState.showSnackbar("녹음된 파일이 비어 있습니다. 다시 시도해주세요.")
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            viewModel.submitVoiceTurn(
                token = token,
                sessionId = currentSessionId,
                audioBytes = bytes,
                fileName = file.name,
                contentType = "audio/m4a",
            )
            runCatching { file.delete() }
        }
    }

    fun sendTextTurn(manualText: String) {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("로그인이 필요합니다.") }
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("문답이 아직 시작되지 않았습니다.") }
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            viewModel.submitTextTurn(
                token = token,
                sessionId = currentSessionId,
                userText = manualText,
            )
        }
    }

    fun goToPreviousQuestion() {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("로그인이 필요합니다.") }
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("문답이 아직 시작되지 않았습니다.") }
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            viewModel.goToPreviousQuestion(token = token, sessionId = currentSessionId)
        }
    }

    fun goToNextQuestion() {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("로그인이 필요합니다.") }
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("문답이 아직 시작되지 않았습니다.") }
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            viewModel.goToNextQuestion(token = token, sessionId = currentSessionId)
        }
    }

    fun attachPhoto(uri: Uri) {
        if (token.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("로그인이 필요합니다.") }
            return
        }
        if (isPreparingSession || isUploadingPhoto) return

        scope.launch {
            runCatching {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("이미지를 읽을 수 없습니다.")
                if (bytes.size > 5 * 1024 * 1024) {
                    throw Exception("이미지 크기는 5MB 이하여야 합니다.")
                }
                val ext = if (mime.contains("png")) "png" else "jpg"
                viewModel.attachPhoto(
                    token = token,
                    imageBytes = bytes,
                    contentType = mime,
                    fileName = "photo_${System.currentTimeMillis()}.$ext",
                )
            }.onFailure { e ->
                snackbarHostState.showSnackbar(e.message ?: "사진 올리기에 실패했습니다.")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) attachPhoto(uri)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("실시간 녹음을 위해 마이크 권한이 필요합니다.")
            }
        }
    }

    LaunchedEffect(initialSessionId) {
        viewModel.resetForInitialSession(initialSessionId)
    }

    LaunchedEffect(token, initialSessionId) {
        viewModel.bootstrap(token, initialSessionId)
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            delay(1000)
            viewModel.onRecordingTick()
        }
    }

    LaunchedEffect(token, latestAudioId, latestAudioStatus) {
        val audioId = latestAudioId
        if (token.isBlank() || audioId.isNullOrBlank() || latestAudioStatus != "pending") {
            return@LaunchedEffect
        }
        repeat(20) {
            delay(1000)
            val result = withContext(Dispatchers.IO) {
                ApiService.getVoiceAudioStatus(token, audioId)
            }
            val audio = result.getOrNull() ?: return@LaunchedEffect
            viewModel.applyVoiceAudioStatus(audio)
            if (audio.audioStatus != "pending") {
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(latestAudioUrl) {
        val audioUrl = latestAudioUrl
        if (
            !audioUrl.isNullOrBlank() &&
            autoPlayedAudioUrl != audioUrl &&
            !isRecording &&
            !isUploadingAudio &&
            !isSubmittingText &&
            !isPlayingAudio
        ) {
            autoPlayedAudioUrl = audioUrl
            playAssistantAudio(audioUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorderController.release(deleteTempFile = true)
            playerController.stop()
        }
    }

    LaunchedEffect(uiState.activePhotoUrl) {
        photoBitmap = null
        uiState.activePhotoUrl?.let { loadPhotoThumbnail(it) }
    }

    val currentPrompt = interviewPrompt ?: defaultVoiceInterviewPrompt()
    val totalQuestions = currentPrompt?.totalQuestions ?: 10
    val currentProgress = currentPrompt?.currentQuestionNo?.coerceIn(1, totalQuestions) ?: 1
    val currentQuestion = currentPrompt?.mainQuestion ?: "인터뷰를 진행해주세요."
    val currentQuestionHint = currentPrompt?.questionHint
    val progressPercent = currentPrompt?.progressPercent ?: 0
    val isInterviewComplete = currentPrompt?.isInterviewComplete == true
    val currentQuestionNo = currentPrompt?.currentQuestionNo
    val currentQuestionHasAnswer = currentPrompt?.currentQuestionHasAnswer == true
    val currentQuestionAnswerCount = currentPrompt?.currentQuestionAnswerCount ?: 0
    val currentQuestionStoryReady = currentPrompt?.currentQuestionStoryReady == true
    val currentQuestionStoryQuality = currentPrompt?.currentQuestionStoryQuality ?: "none"
    val assistantLabel = "AI 인터뷰어"
    val displayAssistantText = assistantText
    val recordingTimeText = String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    val sessionStarted = !sessionId.isNullOrBlank()
    val canAttachPhoto = !isPreparingSession && !isUploadingPhoto && !isRecording && !isUploadingAudio && !isSubmittingText && !isNavigatingPrevious && !isNavigatingNext && !isInterviewComplete
    val canRecordMore = !isInterviewComplete
    val canGoNext = sessionStarted &&
        !isInterviewComplete &&
        currentProgress < totalQuestions &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording
    val canGoPrevious = sessionStarted &&
        (currentProgress > 1 || isInterviewComplete) &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording
    val canGenerateCurrentStory = sessionStarted &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording &&
        currentQuestionStoryReady &&
        currentQuestionHasAnswer &&
        currentQuestionAnswerCount > 0 &&
        currentQuestionNo != null
    val canOpenAutobiography = sessionStarted &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording &&
        currentQuestionNo == totalQuestions &&
        (currentQuestionHasAnswer || isInterviewComplete)

    LaunchedEffect(uiState.errorMessage) {
        val msg = viewModel.consumeError()
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showBasicStoryDialog && !sessionId.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showBasicStoryDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBasicStoryDialog = false
                        onGenerateChapter(sessionId!!, currentQuestionNo, null, true)
                    },
                ) {
                    Text("지금 만들기", fontFamily = SBAggroFamily, color = StoryVenueColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBasicStoryDialog = false }) {
                    Text("한 번 더 말할래요", fontFamily = SBAggroFamily, color = StoryVenueColors.SubText)
                }
            },
            title = {
                Text("이야기 만들기", fontFamily = SBAggroFamily, color = StoryVenueColors.OnSurface)
            },
            text = {
                Text(
                    "답변이 조금 짧지만 지금 이야기로 만들 수 있어요. 계속할까요?",
                    fontFamily = SBAggroFamily,
                    color = StoryVenueColors.SubText,
                )
            },
        )
    }

    Scaffold(
        containerColor = StoryVenueColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "문답 중",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 20.sp,
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !isRecording && !isUploadingAudio && !isSubmittingText && !isNavigatingPrevious && !isNavigatingNext && !isUploadingPhoto,
                    ) {
                        Text(
                            text = "<",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = StoryVenueColors.Primary,
                            fontFamily = SBAggroFamily,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            if (activePhotoUrl != null) {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StoryVenueColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = photoBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "첨부된 사진",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = "📷",
                            fontSize = 34.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (activePhotoLinkedQuestionNo != null && activePhotoLinkedQuestionNo == currentQuestionNo) {
                        "이 사진을 보며 현재 질문과 연결된 기억을 떠올려보세요."
                    } else {
                        "첨부한 사진은 현재 질문의 기억을 떠올리는 데 도움을 줍니다."
                    },
                    fontSize = 15.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            if (currentPrompt != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    ) {
                        Text(
                            text = if (isInterviewComplete) {
                                currentQuestion
                            } else {
                                "Q$currentProgress. $currentQuestion"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            lineHeight = 32.sp,
                        )
                        if (!currentQuestionHint.isNullOrBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "(${currentQuestionHint})",
                                fontSize = 15.sp,
                                color = StoryVenueColors.SubText,
                                fontFamily = SBAggroFamily,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { progressPercent.toFloat() / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50.dp)),
                        color = StoryVenueColors.Primary,
                        trackColor = StoryVenueColors.Divider,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "${progressPercent.coerceIn(0, 100)}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.SubText,
                        fontFamily = SBAggroFamily,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (isInterviewComplete) {
                        "진행도 :  $totalQuestions/$totalQuestions 질문"
                    } else {
                        "진행도 :  $currentProgress/$totalQuestions 질문"
                    },
                    fontSize = 16.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(40.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(if (isRecording || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext) scale else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecording -> StoryVenueColors.Error
                            !canRecordMore -> StoryVenueColors.Divider
                            isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto -> StoryVenueColors.Divider
                            else -> StoryVenueColors.Surface
                        }
                    )
                    .clickable(
                        enabled = !isPreparingSession && !isUploadingAudio && !isSubmittingText && !isNavigatingPrevious && !isNavigatingNext && !isUploadingPhoto && canRecordMore,
                    ) {
                        if (isRecording) {
                            stopRecordingAndUpload()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                startRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
            ) {
                Text(
                    text = if (isRecording) "■" else "🎙",
                    fontSize = 50.sp,
                    color = if (isRecording) Color.White else StoryVenueColors.OnSurface,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = when {
                    isPreparingSession -> "문답 준비 중..."
                    isUploadingPhoto -> "사진 올리는 중..."
                    isRecording -> "녹음 중 $recordingTimeText · 버튼을 다시 누르면 종료/전송됩니다."
                    isUploadingAudio -> "음성을 분석 중..."
                    isSubmittingText -> "답변을 정리 중..."
                    isNavigatingNext -> "다음 질문으로 이동하는 중..."
                    isNavigatingPrevious -> "이전 질문으로 돌아가는 중..."
                    !canRecordMore -> "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                    lastRecordedFileName.isNotBlank() -> "최근 녹음 파일: $lastRecordedFileName"
                    !sessionStarted -> "마이크를 누르면 음성 문답이 시작돼요"
                    else -> "마이크 버튼을 눌러 실시간 녹음을 시작하세요"
                },
                fontSize = 18.sp,
                color = if (isRecording || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isPreparingSession || isUploadingPhoto)
                    StoryVenueColors.Error else StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (canAttachPhoto) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                ) {
                    Text(
                        text = if (sessionStarted) "📷  사진 추가하기" else "📷  사진으로 기억 돕기",
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.Primary,
                        fontSize = 18.sp,
                    )
                }
            }

            if (canGoPrevious || canGoNext) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (canGoPrevious) {
                        TextButton(
                            onClick = { goToPreviousQuestion() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(StoryVenueColors.Surface),
                        ) {
                            Text(
                                text = "이전 질문으로",
                                fontFamily = SBAggroFamily,
                                fontWeight = FontWeight.Bold,
                                color = StoryVenueColors.Primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    if (canGoNext) {
                        TextButton(
                            onClick = { goToNextQuestion() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(StoryVenueColors.Surface),
                        ) {
                            Text(
                                text = "다음 질문으로",
                                fontFamily = SBAggroFamily,
                                fontWeight = FontWeight.Bold,
                                color = StoryVenueColors.Primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (userText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.White),
                    border = BorderStroke(1.dp, StoryVenueColors.Divider),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "내 답변",
                            fontSize = 13.sp,
                            color = StoryVenueColors.SubText,
                            fontFamily = SBAggroFamily,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = userText,
                            fontSize = 18.sp,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            lineHeight = 28.sp,
                        )
                    }
                }
            }

            if (displayAssistantText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.White),
                    border = BorderStroke(1.5.dp, StoryVenueColors.PrimaryLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(StoryVenueColors.PrimaryDark)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = assistantLabel,
                                fontSize = 13.sp,
                                color = StoryVenueColors.White,
                                fontFamily = SBAggroFamily,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Text(
                            text = displayAssistantText,
                            fontSize = 21.sp,
                            color = StoryVenueColors.OnSurface,
                            fontFamily = SBAggroFamily,
                            lineHeight = 32.sp,
                        )
                    }
                }
            }

            if (latestAudioStatus == "pending") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "AI 음성 준비 중...",
                    fontFamily = SBAggroFamily,
                    fontWeight = FontWeight.Bold,
                    color = StoryVenueColors.Primary,
                )
            } else if (!latestAudioUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        latestAudioUrl?.let { playAssistantAudio(it) }
                    },
                    enabled = !isPlayingAudio,
                ) {
                    Text(
                        text = if (isPlayingAudio) "AI 음성 재생 중..." else "AI 음성 다시 듣기",
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.Primary,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.height(12.dp))

            StoryButton(
                onClick = {
                    if (isRecording || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto) return@StoryButton
                    val currentSessionId = sessionId
                    if (currentSessionId.isNullOrBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("세션이 준비되지 않았습니다. 잠시 후 다시 시도해주세요.")
                        }
                    } else {
                        if (!canGenerateCurrentStory || currentQuestionNo == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("현재 질문에 답변해야 이 질문의 이야기를 만들 수 있어요.")
                            }
                            return@StoryButton
                        }
                        if (currentQuestionStoryQuality == "basic") {
                            showBasicStoryDialog = true
                        } else {
                            onGenerateChapter(currentSessionId, currentQuestionNo, null, false)
                        }
                    }
                },
                isLoading = false,
                enabled = canGenerateCurrentStory,
                text = if (currentQuestionNo != null) {
                    "이야기 ${currentQuestionNo} 생성하기"
                } else {
                    "이야기 생성하기"
                },
            )

            if (currentQuestionStoryQuality == "almost_ready" && !currentQuestionStoryReady) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "조금만 더 들려주시면 초안이 가능해요.",
                    fontFamily = SBAggroFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = StoryVenueColors.Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (canOpenAutobiography) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val currentSessionId = sessionId
                        if (!currentSessionId.isNullOrBlank()) {
                            onOpenAutobiography(currentSessionId)
                        }
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StoryVenueColors.Surface,
                        contentColor = StoryVenueColors.Primary,
                    ),
                    border = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "자서전 만들러 가기",
                        fontFamily = SBAggroFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
