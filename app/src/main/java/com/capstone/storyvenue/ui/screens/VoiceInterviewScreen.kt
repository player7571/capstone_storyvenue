package com.capstone.storyvenue.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors
import kotlinx.coroutines.Dispatchers
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
    onGenerateChapter: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("storyvenue", Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", "") ?: ""

    var sessionId by remember { mutableStateOf(initialSessionId?.takeIf { it.isNotBlank() }) }
    var userText by remember { mutableStateOf("") }
    var assistantText by remember { mutableStateOf("인터뷰 세션을 준비하고 있어요.") }
    var latestAudioUrl by remember { mutableStateOf<String?>(null) }
    var lastRecordedFileName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPreparingSession by remember { mutableStateOf(initialSessionId.isNullOrBlank()) }
    var isRecording by remember { mutableStateOf(false) }
    var isUploadingAudio by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFilePath by remember { mutableStateOf<String?>(null) }

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

    fun startRecording() {
        val currentSessionId = sessionId
        if (token.isBlank()) {
            errorMessage = "로그인이 필요합니다."
            return
        }
        if (currentSessionId.isNullOrBlank()) {
            errorMessage = "세션 준비가 완료된 후 다시 시도해주세요."
            return
        }
        if (isPreparingSession || isUploadingAudio || isRecording) return

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
            isRecording = true
        } catch (_: Exception) {
            stopAndReleaseRecorder(deleteTempFile = true)
            errorMessage = "녹음을 시작하지 못했습니다. 마이크 권한 및 기기 상태를 확인해주세요."
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
            }.onFailure { e ->
                errorMessage = e.message ?: "음성 인터뷰 처리에 실패했습니다."
            }
        }
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

        if (!initialSessionId.isNullOrBlank()) {
            sessionId = initialSessionId
            isPreparingSession = false
            assistantText = "인터뷰를 이어갈 수 있어요. 마이크 버튼을 눌러 녹음을 시작하세요."
            return@LaunchedEffect
        }

        isPreparingSession = true
        val title = "음성 인터뷰 ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        val result = withContext(Dispatchers.IO) {
            ApiService.createSession(
                token = token,
                title = title,
                theme = "자서전",
            )
        }

        isPreparingSession = false
        result.onSuccess { created ->
            sessionId = created.id
            assistantText = "세션이 시작되었습니다. 마이크 버튼을 눌러 녹음을 시작하세요."
        }.onFailure { e ->
            errorMessage = e.message ?: "인터뷰 세션 생성에 실패했습니다."
            assistantText = "세션 생성에 실패했습니다. 뒤로가기 후 다시 시도해주세요."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndReleaseRecorder(deleteTempFile = true)
            stopAndReleasePlayer()
        }
    }

    val currentProgress = 3
    val totalQuestions = 10

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

    Scaffold(
        containerColor = StoryVenueColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "인터뷰 중",
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !isRecording && !isUploadingAudio,
                    ) {
                        Text(
                            text = "<",
                            fontSize = 20.sp,
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
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

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
                        text = assistantText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        lineHeight = 30.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "진행도 :  $currentProgress/$totalQuestions 질문",
                fontSize = 14.sp,
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
                            isPreparingSession || isUploadingAudio -> StoryVenueColors.Divider
                            else -> StoryVenueColors.Surface
                        }
                    )
                    .clickable(
                        enabled = !isPreparingSession && !isUploadingAudio,
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
                    fontSize = 48.sp,
                    color = if (isRecording) Color.White else StoryVenueColors.OnSurface,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = when {
                    isPreparingSession -> "세션 생성 중..."
                    isRecording -> "녹음 중... 버튼을 다시 누르면 전송됩니다."
                    isUploadingAudio -> "음성을 분석 중..."
                    lastRecordedFileName.isNotBlank() -> "최근 녹음 파일: $lastRecordedFileName"
                    else -> "마이크 버튼을 눌러 실시간 녹음을 시작하세요"
                },
                fontSize = 16.sp,
                color = if (isRecording || isUploadingAudio || isPreparingSession) StoryVenueColors.Error else StoryVenueColors.SubText,
                fontFamily = SBAggroFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (userText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "내 답변: $userText",
                    fontSize = 14.sp,
                    color = StoryVenueColors.SubText,
                    fontFamily = SBAggroFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorMessage ?: "",
                    fontSize = 14.sp,
                    color = StoryVenueColors.Error,
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

            Spacer(Modifier.weight(1f))

            StoryButton(
                text = "이야기 생성하기",
                onClick = {
                    if (!isRecording && !isUploadingAudio) onGenerateChapter()
                },
                isLoading = false,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
