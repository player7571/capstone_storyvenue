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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInterviewScreen(
    initialSessionId: String? = null,
    onBack: () -> Unit = {},
    onGenerateChapter: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""

    val interviewQuestions = listOf(
        "어린 시절 가장 따뜻하게 기억나는 집의 풍경은 어떤 모습이었나요?",
        "가족과 함께했던 순간 중 아직도 자주 떠오르는 장면이 있나요?",
        "학창 시절의 나를 가장 많이 바꾼 사건이나 만남은 무엇이었나요?",
        "청년기에 스스로에게 가장 큰 도전이었던 선택은 무엇이었나요?",
        "일을 하며 가장 큰 보람을 느낀 순간은 언제였나요?",
        "반대로 많이 힘들었던 시기를 어떻게 버텨냈는지 들려주세요.",
        "누군가에게 받은 사랑이나 배려 중 평생 잊지 못할 일은 무엇인가요?",
        "가족에게 꼭 전하고 싶은 삶의 교훈이나 가치관이 있다면 무엇인가요?",
        "지금의 나를 가장 잘 설명해주는 습관 또는 태도는 무엇인가요?",
        "앞으로 가족과 함께 남기고 싶은 추억이나 바람이 있다면 들려주세요.",
    )

    var sessionId by remember { mutableStateOf(initialSessionId?.takeIf { it.isNotBlank() }) }
    var sessionType by remember { mutableStateOf("voice") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var userText by remember { mutableStateOf("") }
    var currentQuestionIndex by remember { mutableStateOf(1) }
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
        if (isPreparingSession || isUploadingAudio || isRecording || isUploadingPhoto) return

        val currentSessionId = sessionId
        if (!currentSessionId.isNullOrBlank()) {
            beginRecordingAfterSession(currentSessionId)
            return
        }

        // 세션이 아직 없으면 음성 세션을 먼저 생성한 뒤 바로 녹음 시작
        scope.launch {
            isPreparingSession = true
            errorMessage = null
            val title = "음성 문답 ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
            val result = withContext(Dispatchers.IO) {
                ApiService.createSession(token = token, title = title, theme = "자서전")
            }
            isPreparingSession = false
            result.onSuccess { created ->
                sessionId = created.id
                sessionType = "voice"
                assistantText = "문답이 시작되었습니다. 편하게 이야기해주세요."
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
                        contentType = "audio/mp4",
                    )
                    runCatching { file.delete() }
                    apiResult
                }
            }

            isUploadingAudio = false
            result.onSuccess { voice ->
                userText = voice.userText
                assistantText = voice.assistantText
                latestAudioUrl = voice.audioUrl
                currentQuestionIndex = (currentQuestionIndex + 1).coerceAtMost(interviewQuestions.size)
            }.onFailure { e ->
                errorMessage = e.message ?: "음성 문답 처리에 실패했습니다."
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
                photoUrl = started.photoUrl.takeIf { it.isNotBlank() }
                currentQuestionIndex = 1
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
            scope.launch {
                val messagesResult = withContext(Dispatchers.IO) {
                    ApiService.getSessionMessages(token = token, sessionId = detail.id)
                }
                messagesResult.onSuccess { messages ->
                    val userTurnCount = messages.count { it.role == "user" }
                    currentQuestionIndex = (userTurnCount + 1).coerceAtMost(interviewQuestions.size)
                    messages.lastOrNull { it.role == "assistant" }?.content?.takeIf { it.isNotBlank() }?.let {
                        assistantText = it
                    } ?: run {
                        assistantText = if (detail.sessionType == "photo")
                            "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                        else
                            "문답을 이어갈 수 있어요. 마이크 버튼을 눌러 녹음을 시작하세요."
                    }
                }.onFailure {
                    assistantText = if (detail.sessionType == "photo")
                        "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                    else
                        "문답을 이어갈 수 있어요. 마이크 버튼을 눌러 녹음을 시작하세요."
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

    val totalQuestions = interviewQuestions.size
    val currentProgress = currentQuestionIndex.coerceIn(1, totalQuestions)
    val currentQuestion = interviewQuestions.getOrElse(currentProgress - 1) {
        "인터뷰를 진행해주세요."
    }
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
    val canAttachPhoto = !sessionStarted && !isPreparingSession && !isUploadingPhoto && !isRecording && !isUploadingAudio

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
                        enabled = !isRecording && !isUploadingAudio && !isUploadingPhoto,
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = StoryVenueColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "Q$currentProgress. $currentQuestion",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        lineHeight = 32.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { currentProgress.toFloat() / totalQuestions },
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
                    text = "${(currentProgress.toFloat() / totalQuestions * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "진행도 :  $currentProgress/$totalQuestions 질문",
                fontSize = 16.sp,
                color = StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(40.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(if (isRecording || isUploadingAudio) scale else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRecording -> StoryVenueColors.Error
                            isPreparingSession || isUploadingAudio || isUploadingPhoto -> StoryVenueColors.Divider
                            else -> StoryVenueColors.Surface
                        }
                    )
                    .clickable(
                        enabled = !isPreparingSession && !isUploadingAudio && !isUploadingPhoto,
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
                    lastRecordedFileName.isNotBlank() -> "최근 녹음 파일: $lastRecordedFileName"
                    !sessionStarted -> "마이크를 누르면 음성 문답이 시작돼요"
                    else -> "마이크 버튼을 눌러 실시간 녹음을 시작하세요"
                },
                fontSize = 18.sp,
                color = if (isRecording || isUploadingAudio || isPreparingSession || isUploadingPhoto)
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
                    text = "AI 인터뷰어: $assistantText",
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
                    if (isRecording || isUploadingAudio || isUploadingPhoto) return@StoryButton
                    val currentSessionId = sessionId
                    if (currentSessionId.isNullOrBlank()) {
                        errorMessage = "세션이 준비되지 않았습니다. 잠시 후 다시 시도해주세요."
                    } else {
                        onGenerateChapter(currentSessionId)
                    }
                },
                isLoading = false,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
