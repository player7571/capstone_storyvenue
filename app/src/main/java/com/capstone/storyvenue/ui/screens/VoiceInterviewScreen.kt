package com.capstone.storyvenue.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun defaultVoiceInterviewPrompt() = InterviewPromptData(
    currentQuestionNo = 1,
    totalQuestions = 10,
    mainQuestion = "어린 시절은 어떠했나요?",
    questionHint = "집, 가족, 동네 중 떠오르는 것부터 말씀해주세요.",
    followUpCount = 0,
    questionStatus = "main",
    progressPercent = 10,
    isInterviewComplete = false,
)

private fun buildVoiceSessionTitle(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    return "음성 문답 ${formatter.format(Date())}"
}

private fun chapterTypeFromAnsweredCount(answeredCount: Int): String {
    return when {
        answeredCount <= 2 -> "childhood"
        answeredCount <= 4 -> "youth"
        answeredCount <= 6 -> "career"
        answeredCount <= 8 -> "love"
        else -> "reflection"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInterviewScreen(
    initialSessionId: String? = null,
    onBack: () -> Unit = {},
    onGenerateChapter: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""

    var sessionId by remember { mutableStateOf(initialSessionId?.takeIf { it.isNotBlank() }) }
    var sessionType by remember { mutableStateOf("voice") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var userText by remember { mutableStateOf("") }
    var interviewPrompt by remember {
        mutableStateOf(
            if (initialSessionId.isNullOrBlank()) defaultVoiceInterviewPrompt() else null
        )
    }
    var assistantText by remember {
        mutableStateOf(
            if (initialSessionId.isNullOrBlank())
                "마이크 버튼을 눌러 음성으로 시작하거나, 사진을 첨부해 대화를 시작하세요."
            else
                "문답을 불러오고 있어요."
        )
    }
    var latestAudioUrl by remember { mutableStateOf<String?>(null) }
    var lastRecordedFileName by remember { mutableStateOf("") }
    var recordingSeconds by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isPreparingSession by remember { mutableStateOf(!initialSessionId.isNullOrBlank()) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isUploadingAudio by remember { mutableStateOf(false) }
    var isSubmittingText by remember { mutableStateOf(false) }
    var isNavigatingPrevious by remember { mutableStateOf(false) }
    var isNavigatingNext by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFilePath by remember { mutableStateOf<String?>(null) }

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

    fun stopAndReleasePlayer() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingAudio = false
    }

    fun stopAndReleaseRecorder(deleteTempFile: Boolean) {
        mediaRecorder?.runCatching { stop() }
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false

        if (deleteTempFile) {
            recordingFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        recordingFilePath = null
    }

    fun playAssistantAudio(url: String) {
        stopAndReleasePlayer()
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(url)
            player.setOnPreparedListener {
                isPlayingAudio = true
                it.start()
            }
            player.setOnCompletionListener {
                isPlayingAudio = false
                it.release()
                mediaPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                mediaPlayer = null
                isPlayingAudio = false
                errorMessage = "AI 음성 재생 중 오류가 발생했습니다."
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            errorMessage = "AI 음성 재생에 실패했습니다."
        }
    }

    fun applyVoiceTurnResult(voice: VoiceTurnData) {
        userText = voice.userText
        assistantText = voice.assistantText
        latestAudioUrl = voice.audioUrl
        voice.interviewState?.let { interviewPrompt = it }
    }

    fun beginRecordingAfterSession(currentSessionId: String) {
        errorMessage = null
        val outputDir = File(context.cacheDir, "voice-recordings").apply { mkdirs() }
        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.m4a")

        try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingFilePath = outputFile.absolutePath
            lastRecordedFileName = outputFile.name
            recordingSeconds = 0
            isRecording = true
        } catch (_: Exception) {
            stopAndReleaseRecorder(deleteTempFile = true)
            errorMessage = "녹음을 시작하지 못했습니다. 마이크 권한 및 기기 상태를 확인해주세요."
        }
    }

    fun startRecording() {
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
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
            isPreparingSession = true
            errorMessage = null
            val title = buildVoiceSessionTitle()
            val result = withContext(Dispatchers.IO) {
                ApiService.createSession(token = token, title = title, theme = "자서전")
            }
            isPreparingSession = false
            result.onSuccess { created ->
                sessionId = created.id
                sessionType = "voice"
                interviewPrompt = created.interviewState ?: defaultVoiceInterviewPrompt()
                assistantText = "질문을 보고 천천히 이야기해주세요."
                beginRecordingAfterSession(created.id)
            }.onFailure { e ->
                errorMessage = e.message ?: "문답 시작에 실패했습니다."
            }
        }
    }

    fun stopRecordingAndUpload() {
        val currentSessionId = sessionId
        val path = recordingFilePath
        if (currentSessionId.isNullOrBlank() || path.isNullOrBlank()) {
            stopAndReleaseRecorder(deleteTempFile = true)
            errorMessage = "녹음 파일을 찾을 수 없습니다."
            return
        }

        mediaRecorder?.runCatching { stop() }
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        recordingFilePath = null

        scope.launch {
            isUploadingAudio = true
            errorMessage = null

            val result = withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.exists() || file.length() == 0L) {
                    Result.failure(Exception("녹음된 파일이 비어 있습니다. 다시 시도해주세요."))
                } else {
                    val bytes = file.readBytes()
                    val apiResult = ApiService.voiceTurn(
                        token = token,
                        sessionId = currentSessionId,
                        audioBytes = bytes,
                        fileName = file.name,
                        contentType = "audio/m4a",
                    )
                    runCatching { file.delete() }
                    apiResult
                }
            }

            isUploadingAudio = false
            result.onSuccess { voice ->
                applyVoiceTurnResult(voice)
            }.onFailure { e ->
                errorMessage = e.message ?: "음성 문답 처리에 실패했습니다."
            }
        }
    }

    fun sendTextTurn(manualText: String) {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        if (sessionType == "photo") {
            errorMessage = "사진 문답에서는 이 기능을 사용할 수 없습니다."
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            errorMessage = "문답이 아직 시작되지 않았습니다."
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            isSubmittingText = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                ApiService.voiceTextTurn(
                    token = token,
                    sessionId = currentSessionId,
                    userText = manualText,
                )
            }
            isSubmittingText = false
            result.onSuccess { voice ->
                applyVoiceTurnResult(voice)
            }.onFailure { e ->
                errorMessage = e.message ?: "문답 처리에 실패했습니다."
            }
        }
    }

    fun goToPreviousQuestion() {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        if (sessionType == "photo") {
            errorMessage = "사진 문답에서는 이 기능을 사용할 수 없습니다."
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            errorMessage = "문답이 아직 시작되지 않았습니다."
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            isNavigatingPrevious = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                ApiService.goToPreviousQuestion(
                    token = token,
                    sessionId = currentSessionId,
                )
            }
            isNavigatingPrevious = false
            result.onSuccess { prompt ->
                interviewPrompt = prompt
                userText = ""
                latestAudioUrl = null
                assistantText = "이전 질문으로 돌아왔어요. 천천히 다시 이야기해주세요."
            }.onFailure { e ->
                errorMessage = e.message ?: "이전 질문으로 이동하지 못했습니다."
            }
        }
    }

    fun goToNextQuestion() {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        if (sessionType == "photo") {
            errorMessage = "사진 문답에서는 이 기능을 사용할 수 없습니다."
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            errorMessage = "문답이 아직 시작되지 않았습니다."
            return
        }
        if (isPreparingSession || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto || isRecording) {
            return
        }

        scope.launch {
            isNavigatingNext = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                ApiService.goToNextQuestion(
                    token = token,
                    sessionId = currentSessionId,
                )
            }
            isNavigatingNext = false
            result.onSuccess { prompt ->
                interviewPrompt = prompt
                userText = ""
                latestAudioUrl = null
                assistantText = if (prompt.isInterviewComplete) {
                    "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                } else {
                    "다음 질문으로 이동했어요. 천천히 이야기해주세요."
                }
            }.onFailure { e ->
                errorMessage = e.message ?: "다음 질문으로 이동하지 못했습니다."
            }
        }
    }

    fun attachPhoto(uri: Uri) {
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        if (!sessionId.isNullOrBlank()) {
            errorMessage = "이미 문답이 시작되어 사진을 첨부할 수 없습니다."
            return
        }
        if (isPreparingSession || isUploadingPhoto) return

        scope.launch {
            isUploadingPhoto = true
            errorMessage = null

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("이미지를 읽을 수 없습니다.")
                    if (bytes.size > 5 * 1024 * 1024) {
                        throw Exception("이미지 크기는 5MB 이하여야 합니다.")
                    }
                    val ext = when {
                        mime.contains("png") -> "png"
                        else -> "jpg"
                    }
                    ApiService.createPhotoSession(
                        token = token,
                        imageBytes = bytes,
                        contentType = mime,
                        fileName = "photo_${System.currentTimeMillis()}.$ext",
                    )
                }.getOrElse { Result.failure(it) }
            }

            isUploadingPhoto = false
            result.onSuccess { started ->
                sessionId = started.sessionId
                sessionType = "photo"
                interviewPrompt = null
                photoUrl = started.photoUrl.takeIf { it.isNotBlank() }
                assistantText = started.aiMessage.ifBlank {
                    "사진을 분석했어요. 마이크 버튼을 눌러 답변을 녹음해주세요."
                }
                photoUrl?.let { loadPhotoThumbnail(it) }
            }.onFailure { e ->
                errorMessage = e.message ?: "사진 올리기에 실패했습니다."
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
            errorMessage = "실시간 녹음을 위해 마이크 권한이 필요합니다."
        }
    }

    LaunchedEffect(token, initialSessionId) {
        if (token.isBlank()) {
            isPreparingSession = false
            errorMessage = "로그인이 필요합니다."
            return@LaunchedEffect
        }

        if (initialSessionId.isNullOrBlank()) {
            // 신규 진입 — 세션은 사용자가 첫 행동(녹음/사진)을 할 때 생성
            isPreparingSession = false
            return@LaunchedEffect
        }

        isPreparingSession = true
        val result = withContext(Dispatchers.IO) {
            ApiService.getSessionDetail(token = token, sessionId = initialSessionId)
        }
        isPreparingSession = false
        result.onSuccess { detail ->
            sessionId = detail.id
            sessionType = detail.sessionType
            photoUrl = detail.photoUrl
            interviewPrompt = if (detail.sessionType == "photo") {
                null
            } else {
                detail.interviewState ?: defaultVoiceInterviewPrompt()
            }
            scope.launch {
                val messagesResult = withContext(Dispatchers.IO) {
                    ApiService.getSessionMessages(token = token, sessionId = detail.id)
                }
                messagesResult.onSuccess { messages ->
                    messages.lastOrNull { it.role == "assistant" }?.content?.takeIf { it.isNotBlank() }?.let {
                        assistantText = it
                    } ?: run {
                        assistantText = if (detail.sessionType == "photo")
                            "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                        else if (detail.interviewState?.isInterviewComplete == true)
                            "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                        else
                            "질문을 보고 천천히 이야기해주세요."
                    }
                }.onFailure {
                    assistantText = if (detail.sessionType == "photo")
                        "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                    else if (detail.interviewState?.isInterviewComplete == true)
                        "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                    else
                        "질문을 보고 천천히 이야기해주세요."
                }
                detail.photoUrl?.let { loadPhotoThumbnail(it) }
            }
        }.onFailure { e ->
            errorMessage = e.message ?: "문답을 불러오지 못했습니다."
            assistantText = "문답을 불러오지 못했습니다. 뒤로가기 후 다시 시도해주세요."
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            delay(1000)
            recordingSeconds += 1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndReleaseRecorder(deleteTempFile = true)
            stopAndReleasePlayer()
        }
    }

    val currentPrompt = if (sessionType == "photo") null else (interviewPrompt ?: defaultVoiceInterviewPrompt())
    val totalQuestions = currentPrompt?.totalQuestions ?: 10
    val currentProgress = currentPrompt?.currentQuestionNo?.coerceIn(1, totalQuestions) ?: 1
    val currentQuestion = currentPrompt?.mainQuestion ?: "인터뷰를 진행해주세요."
    val currentQuestionHint = currentPrompt?.questionHint
    val progressPercent = currentPrompt?.progressPercent ?: 0
    val isInterviewComplete = currentPrompt?.isInterviewComplete == true
    val assistantLabel = if (currentPrompt?.questionStatus == "follow_up") "AI 보조 질문" else "AI 인터뷰어"
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
    val canAttachPhoto = !sessionStarted && !isPreparingSession && !isUploadingPhoto && !isRecording && !isUploadingAudio && !isSubmittingText
    val canRecordMore = sessionType == "photo" || !isInterviewComplete
    val canGoNext = sessionStarted &&
        sessionType != "photo" &&
        !isInterviewComplete &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording
    val canGoPrevious = sessionStarted &&
        sessionType != "photo" &&
        (currentProgress > 1 || isInterviewComplete) &&
        !isPreparingSession &&
        !isUploadingPhoto &&
        !isUploadingAudio &&
        !isSubmittingText &&
        !isNavigatingPrevious &&
        !isNavigatingNext &&
        !isRecording

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (sessionType == "photo") "사진 문답 중" else "문답 중",
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

            if (photoUrl != null) {
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
            }

            if (currentPrompt != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
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
                        text = "📷  사진으로 시작하기",
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
                Text(
                    text = "내 답변: $userText",
                    fontSize = 16.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (assistantText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$assistantLabel: $assistantText",
                    fontSize = 16.sp,
                    color = StoryVenueColors.Primary,
                    fontFamily = SBAggroFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!latestAudioUrl.isNullOrBlank()) {
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

            StoryButton(
                text = "이야기 생성하기",
                onClick = {
                    if (isRecording || isUploadingAudio || isSubmittingText || isNavigatingPrevious || isNavigatingNext || isUploadingPhoto) return@StoryButton
                    val currentSessionId = sessionId
                    if (currentSessionId.isNullOrBlank()) {
                        errorMessage = "세션이 준비되지 않았습니다. 잠시 후 다시 시도해주세요."
                    } else {
                        val answeredCount = if (isInterviewComplete) {
                            totalQuestions
                        } else {
                            (currentProgress - 1).coerceAtLeast(0)
                        }
                        if (answeredCount == 0) {
                            errorMessage = "최소 1개 이상 답변한 뒤 이야기를 생성할 수 있습니다."
                            return@StoryButton
                        }
                        val chapterType = chapterTypeFromAnsweredCount(answeredCount)
                        onGenerateChapter(currentSessionId, chapterType)
                    }
                },
                isLoading = false,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
