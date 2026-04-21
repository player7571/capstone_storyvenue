package com.capstone.storyvenue.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class VoiceInterviewUiState(
    val sessionId: String? = null,
    val sessionType: String = "voice",
    val photoUrl: String? = null,
    val userText: String = "",
    val interviewPrompt: InterviewPromptData? = null,
    val assistantText: String = "마이크 버튼을 눌러 음성으로 시작하거나, 사진을 첨부해 대화를 시작하세요.",
    val latestAudioUrl: String? = null,
    val lastRecordedFileName: String = "",
    val recordingSeconds: Int = 0,
    val errorMessage: String? = null,
    val isPreparingSession: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isUploadingAudio: Boolean = false,
    val isSubmittingText: Boolean = false,
    val isNavigatingPrevious: Boolean = false,
    val isNavigatingNext: Boolean = false,
    val isRecording: Boolean = false,
    val isPlayingAudio: Boolean = false,
)

class VoiceInterviewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VoiceInterviewUiState())
    val uiState: StateFlow<VoiceInterviewUiState> = _uiState.asStateFlow()

    fun resetForInitialSession(initialSessionId: String?) {
        _uiState.value = VoiceInterviewUiState(
            sessionId = initialSessionId?.takeIf { it.isNotBlank() },
            interviewPrompt = if (initialSessionId.isNullOrBlank()) defaultVoiceInterviewPrompt() else null,
            assistantText = if (initialSessionId.isNullOrBlank()) {
                "마이크 버튼을 눌러 음성으로 시작하거나, 사진을 첨부해 대화를 시작하세요."
            } else {
                "문답을 불러오고 있어요."
            },
            isPreparingSession = !initialSessionId.isNullOrBlank(),
        )
    }

    suspend fun bootstrap(token: String, initialSessionId: String?) {
        if (token.isBlank()) {
            _uiState.update {
                it.copy(
                    isPreparingSession = false,
                    errorMessage = "로그인이 필요합니다.",
                )
            }
            return
        }

        if (initialSessionId.isNullOrBlank()) {
            _uiState.update { it.copy(isPreparingSession = false) }
            return
        }

        _uiState.update { it.copy(isPreparingSession = true) }
        val result = withContext(Dispatchers.IO) {
            ApiService.getSessionDetail(token = token, sessionId = initialSessionId)
        }
        result.onSuccess { detail ->
            _uiState.update {
                it.copy(
                    sessionId = detail.id,
                    sessionType = detail.sessionType,
                    photoUrl = detail.photoUrl,
                    interviewPrompt = if (detail.sessionType == "photo") null else (detail.interviewState ?: defaultVoiceInterviewPrompt()),
                    assistantText = "문답을 불러오는 중...",
                    isPreparingSession = false,
                    errorMessage = null,
                )
            }

            val messagesResult = withContext(Dispatchers.IO) {
                ApiService.getSessionMessages(token = token, sessionId = detail.id)
            }
            messagesResult.onSuccess { messages ->
                val fallbackText = if (detail.sessionType == "photo") {
                    "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                } else if (detail.interviewState?.isInterviewComplete == true) {
                    "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                } else {
                    "질문을 보고 천천히 이야기해주세요."
                }
                _uiState.update { current ->
                    current.copy(
                        assistantText = messages.lastOrNull { msg -> msg.role == "assistant" }?.content?.takeIf { msg -> msg.isNotBlank() }
                            ?: fallbackText,
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        assistantText = if (detail.sessionType == "photo") {
                            "사진 문답을 이어갈 수 있어요. 마이크 버튼을 눌러 답변을 녹음하세요."
                        } else if (detail.interviewState?.isInterviewComplete == true) {
                            "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                        } else {
                            "질문을 보고 천천히 이야기해주세요."
                        },
                    )
                }
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    isPreparingSession = false,
                    assistantText = "문답을 불러오지 못했습니다. 뒤로가기 후 다시 시도해주세요.",
                    errorMessage = e.message ?: "문답을 불러오지 못했습니다.",
                )
            }
        }
    }

    suspend fun ensureVoiceSession(token: String, title: String): Result<String> {
        val currentSessionId = _uiState.value.sessionId
        if (!currentSessionId.isNullOrBlank()) {
            return Result.success(currentSessionId)
        }

        _uiState.update { it.copy(isPreparingSession = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.createSession(token = token, title = title, theme = "자서전")
        }
        _uiState.update { it.copy(isPreparingSession = false) }
        result.onSuccess { created ->
            _uiState.update {
                it.copy(
                    sessionId = created.id,
                    sessionType = "voice",
                    interviewPrompt = created.interviewState ?: defaultVoiceInterviewPrompt(),
                    assistantText = "질문을 보고 천천히 이야기해주세요.",
                    errorMessage = null,
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "문답 시작에 실패했습니다.") }
        }
        return result.map { it.id }
    }

    fun onRecordingStarted(fileName: String) {
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingSeconds = 0,
                lastRecordedFileName = fileName,
                errorMessage = null,
            )
        }
    }

    fun onRecordingStopped() {
        _uiState.update { it.copy(isRecording = false) }
    }

    fun onRecordingTick() {
        _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
    }

    fun setAudioPlaying(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlayingAudio = isPlaying) }
    }

    suspend fun submitVoiceTurn(
        token: String,
        sessionId: String,
        audioBytes: ByteArray,
        fileName: String,
        contentType: String,
    ) {
        _uiState.update { it.copy(isUploadingAudio = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.voiceTurn(
                token = token,
                sessionId = sessionId,
                audioBytes = audioBytes,
                fileName = fileName,
                contentType = contentType,
            )
        }
        _uiState.update { it.copy(isUploadingAudio = false) }
        result.onSuccess { applyVoiceTurnResult(it) }
            .onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "음성 문답 처리에 실패했습니다.") }
            }
    }

    suspend fun submitTextTurn(token: String, sessionId: String, userText: String) {
        _uiState.update { it.copy(isSubmittingText = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.voiceTextTurn(token = token, sessionId = sessionId, userText = userText)
        }
        _uiState.update { it.copy(isSubmittingText = false) }
        result.onSuccess { applyVoiceTurnResult(it) }
            .onFailure { e ->
                _uiState.update { it.copy(errorMessage = e.message ?: "문답 처리에 실패했습니다.") }
            }
    }

    suspend fun goToPreviousQuestion(token: String, sessionId: String) {
        _uiState.update { it.copy(isNavigatingPrevious = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.goToPreviousQuestion(token = token, sessionId = sessionId)
        }
        _uiState.update { it.copy(isNavigatingPrevious = false) }
        result.onSuccess { prompt ->
            _uiState.update {
                it.copy(
                    interviewPrompt = prompt,
                    userText = "",
                    latestAudioUrl = null,
                    assistantText = "이전 질문으로 돌아왔어요. 천천히 다시 이야기해주세요.",
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "이전 질문으로 이동하지 못했습니다.") }
        }
    }

    suspend fun goToNextQuestion(token: String, sessionId: String) {
        _uiState.update { it.copy(isNavigatingNext = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.goToNextQuestion(token = token, sessionId = sessionId)
        }
        _uiState.update { it.copy(isNavigatingNext = false) }
        result.onSuccess { prompt ->
            _uiState.update {
                it.copy(
                    interviewPrompt = prompt,
                    userText = "",
                    latestAudioUrl = null,
                    assistantText = if (prompt.isInterviewComplete) {
                        "질문이 모두 끝났어요. 이제 이야기를 생성해보세요."
                    } else {
                        "다음 질문으로 이동했어요. 천천히 이야기해주세요."
                    },
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "다음 질문으로 이동하지 못했습니다.") }
        }
    }

    suspend fun attachPhoto(
        token: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ) {
        _uiState.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            ApiService.createPhotoSession(
                token = token,
                imageBytes = imageBytes,
                contentType = contentType,
                fileName = fileName,
            )
        }
        _uiState.update { it.copy(isUploadingPhoto = false) }
        result.onSuccess { started ->
            _uiState.update {
                it.copy(
                    sessionId = started.sessionId,
                    sessionType = "photo",
                    interviewPrompt = null,
                    photoUrl = started.photoUrl.takeIf { value -> value.isNotBlank() },
                    assistantText = started.aiMessage.ifBlank {
                        "사진을 분석했어요. 마이크 버튼을 눌러 답변을 녹음해주세요."
                    },
                )
            }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "사진 올리기에 실패했습니다.") }
        }
    }

    fun consumeError(): String? {
        val message = _uiState.value.errorMessage
        if (!message.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = null) }
        }
        return message
    }

    private fun applyVoiceTurnResult(voice: VoiceTurnData) {
        val normalizedUserText = when (voice.reasonCode) {
            "question_echo", "transcript_unclear", "empty_answer" -> ""
            else -> voice.userText
        }
        _uiState.update {
            it.copy(
                userText = normalizedUserText,
                assistantText = voice.assistantText,
                latestAudioUrl = voice.audioUrl,
                interviewPrompt = voice.interviewState ?: it.interviewPrompt,
            )
        }
    }
}
