package com.capstone.storyvenue.ui.screens

object ApiService {

    // ── Auth ─────────────────────────────────────────
    fun getKakaoAuthorizeUrl(): Result<KakaoAuthorizeData> =
        AuthApi.getKakaoAuthorizeUrl()

    fun loginWithKakaoCode(code: String, state: String): Result<AuthSessionData> =
        AuthApi.loginWithKakaoCode(code, state)

    fun login(email: String, password: String): Result<Pair<String, String>> =
        AuthApi.login(email, password)

    fun signup(name: String, email: String, password: String): Result<String> =
        AuthApi.signup(name, email, password)

    fun getProfile(token: String): Result<ProfileData> = AuthApi.getProfile(token)

    fun updateProfileName(token: String, name: String): Result<ProfileData> =
        AuthApi.updateProfileName(token, name)

    fun uploadAvatar(
        token: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): Result<ProfileData> = AuthApi.uploadAvatar(token, imageBytes, contentType, fileName)

    fun deleteMe(token: String): Result<String> = AuthApi.deleteMe(token)

    // ── Sessions ─────────────────────────────────────
    fun createSession(token: String, title: String, theme: String): Result<SessionData> =
        SessionApi.createSession(token, title, theme)

    fun getSessions(token: String): Result<List<InterviewSession>> = SessionApi.getSessions(token)

    fun deleteSession(token: String, sessionId: String): Result<Unit> =
        SessionApi.deleteSession(token, sessionId)

    fun attachPhotoToSession(
        token: String,
        sessionId: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): Result<PhotoAttachmentData> =
        SessionApi.attachPhotoToSession(token, sessionId, imageBytes, contentType, fileName)

    fun getSessionDetail(token: String, sessionId: String): Result<SessionDetailData> =
        SessionApi.getSessionDetail(token, sessionId)

    fun goToPreviousQuestion(
        token: String,
        sessionId: String,
    ): Result<InterviewPromptData> = SessionApi.goToPreviousQuestion(token, sessionId)

    fun goToNextQuestion(
        token: String,
        sessionId: String,
    ): Result<InterviewPromptData> = SessionApi.goToNextQuestion(token, sessionId)

    fun fetchImageBytes(url: String): Result<ByteArray> = SessionApi.fetchImageBytes(url)

    fun voiceTurn(
        token: String,
        sessionId: String,
        audioBytes: ByteArray,
        fileName: String = "recording.m4a",
        contentType: String = "audio/m4a",
    ): Result<VoiceTurnData> = VoiceApi.voiceTurn(token, sessionId, audioBytes, fileName, contentType)

    fun voiceTextTurn(
        token: String,
        sessionId: String,
        userText: String,
    ): Result<VoiceTurnData> = VoiceApi.voiceTextTurn(token, sessionId, userText)

    fun getSessionMessages(token: String, sessionId: String): Result<List<SessionMessageData>> =
        VoiceApi.getSessionMessages(token, sessionId)

    fun generateChapter(
        token: String,
        sessionId: String,
        questionNo: Int? = null,
        chapterType: String? = null,
        allowBasic: Boolean = false,
    ): Result<GeneratedChapterData> = ChapterBookApi.generateChapter(
        token = token,
        sessionId = sessionId,
        questionNo = questionNo,
        chapterType = chapterType,
        allowBasic = allowBasic,
    )

    fun getLatestChapter(
        token: String,
        sessionId: String,
        questionNo: Int? = null,
    ): Result<GeneratedChapterData?> = ChapterBookApi.getLatestChapter(
        token = token,
        sessionId = sessionId,
        questionNo = questionNo,
    )

    fun getChapter(
        token: String,
        chapterId: String,
    ): Result<GeneratedChapterData> = ChapterBookApi.getChapter(
        token = token,
        chapterId = chapterId,
    )

    // ── Chapters / Books ─────────────────────────────
    fun listChapters(
        token: String,
        sessionId: String? = null,
        questionNo: Int? = null,
        latestOnly: Boolean = false,
    ): Result<List<ChapterDraftData>> =
        ChapterBookApi.listChapters(token, sessionId, questionNo, latestOnly)

    fun deleteChapter(token: String, chapterId: String): Result<Unit> =
        ChapterBookApi.deleteChapter(token, chapterId)

    fun updateChapter(
        token: String,
        chapterId: String,
        title: String,
        content: String,
    ): Result<GeneratedChapterData> =
        ChapterBookApi.updateChapter(token, chapterId, title, content)

    fun compileBook(
        token: String,
        chapterIds: List<String>,
        title: String,
    ): Result<BookDetailData> = ChapterBookApi.compileBook(token, chapterIds, title)

    fun createAutobiography(
        token: String,
        sessionId: String,
        chapterIds: List<String>,
        title: String,
    ): Result<AutobiographyCreateData> = ChapterBookApi.createAutobiography(token, sessionId, chapterIds, title)

    fun getBookDetail(token: String, bookId: String): Result<BookDetailData> =
        ChapterBookApi.getBookDetail(token, bookId)

    fun getSharedBookDetail(token: String, bookId: String): Result<BookDetailData> =
        ChapterBookApi.getSharedBookDetail(token, bookId)

    fun updateBook(
        token: String,
        bookId: String,
        title: String,
        subtitle: String?,
        body: String,
    ): Result<BookDetailData> =
        ChapterBookApi.updateBook(token, bookId, title, subtitle, body)

    fun shareBookToFeed(token: String, bookId: String): Result<BookShareResultData> =
        ChapterBookApi.shareBookToFeed(token, bookId)

    fun exportBookPdf(
        token: String,
        bookId: String,
        includeCover: Boolean,
        coverImageBytes: ByteArray? = null,
        coverImageContentType: String? = null,
        coverImageFileName: String? = null,
    ): Result<ByteArray> = ChapterBookApi.exportBookPdf(
        token = token,
        bookId = bookId,
        includeCover = includeCover,
        coverImageBytes = coverImageBytes,
        coverImageContentType = coverImageContentType,
        coverImageFileName = coverImageFileName,
    )

    fun createFeedPost(
        token: String,
        bookId: String,
        title: String,
        preview: String,
    ): Result<FeedPost> = FeedApi.createFeedPost(token, bookId, title, preview)

    fun createChapterFeedPost(
        token: String,
        chapterId: String,
    ): Result<FeedPost> = FeedApi.createChapterFeedPost(token, chapterId)

    // ── Feed ─────────────────────────────────────────
    fun getFeed(token: String, limit: Int = 20, offset: Int = 0, query: String = ""): Result<List<FeedPost>> =
        FeedApi.getFeed(token, limit, offset, query)

    fun getMyFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> =
        FeedApi.getMyFeed(token, limit, offset)

    fun getLikedFeed(token: String, limit: Int = 20, offset: Int = 0): Result<List<FeedPost>> =
        FeedApi.getLikedFeed(token, limit, offset)

    fun getFeedDetail(token: String, postId: String): Result<FeedPost> =
        FeedApi.getFeedDetail(token, postId)

    fun toggleLike(token: String, postId: String): Result<Pair<Boolean, Int>> =
        FeedApi.toggleLike(token, postId)

    // ── Comments ─────────────────────────────────────
    data class CommentData(
        val id: String,
        val authorName: String,
        val authorAvatarUrl: String? = null,
        val content: String,
        val timeAgo: String,
    )

    fun getComments(token: String, postId: String): Result<List<CommentData>> =
        FeedApi.getComments(token, postId)

    fun createComment(token: String, postId: String, content: String): Result<CommentData> =
        FeedApi.createComment(token, postId, content)

    fun deleteComment(token: String, commentId: String): Result<Unit> =
        FeedApi.deleteComment(token, commentId)

    // ── Notifications ────────────────────────────────
    fun getNotifications(token: String, limit: Int = 30, offset: Int = 0): Result<List<NotificationItem>> =
        NotificationApi.getNotifications(token, limit, offset)

    fun getUnreadCount(token: String): Result<Int> = NotificationApi.getUnreadCount(token)

    fun deleteNotification(token: String, notificationId: String): Result<Unit> =
        NotificationApi.deleteNotification(token, notificationId)

    fun markNotificationRead(token: String, notificationId: String): Result<Unit> =
        NotificationApi.markNotificationRead(token, notificationId)

    // ── Chat ─────────────────────────────────────────
    fun getChatPartners(token: String): Result<List<ChatPartner>> = ChatApi.getChatPartners(token)

    fun getMessages(token: String, otherUserId: String, limit: Int = 50): Result<List<ChatMessageData>> =
        ChatApi.getMessages(token, otherUserId, limit)

    fun sendMessage(token: String, otherUserId: String, content: String): Result<ChatMessageData> =
        ChatApi.sendMessage(token, otherUserId, content)
}

// ── 공통 데이터 클래스 ───────────────────────────────
data class ProfileData(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

data class SessionData(
    val id: String,
    val title: String,
    val theme: String,
    val status: String,
    val createdAt: String,
    val interviewState: InterviewPromptData? = null,
)

data class VoiceTurnData(
    val userText: String,
    val assistantText: String,
    val audioUrl: String? = null,
    val decision: String? = null,
    val reasonCode: String? = null,
    val interviewState: InterviewPromptData? = null,
)

data class PhotoAttachmentData(
    val artifactId: String,
    val photoUrl: String,
    val linkedQuestionNo: Int? = null,
    val aiMessage: String? = null,
    val createdAt: String? = null,
)

data class SessionDetailData(
    val id: String,
    val sessionType: String,
    val photoUrl: String?,
    val activePhotoArtifactId: String? = null,
    val activePhotoLinkedQuestionNo: Int? = null,
    val status: String,
    val interviewState: InterviewPromptData? = null,
)

data class InterviewPromptData(
    val currentQuestionNo: Int,
    val totalQuestions: Int,
    val mainQuestion: String,
    val questionHint: String? = null,
    val followUpCount: Int = 0,
    val questionStatus: String = "main",
    val progressPercent: Int = 0,
    val isInterviewComplete: Boolean = false,
    val currentQuestionHasAnswer: Boolean = false,
    val currentQuestionAnswerCount: Int = 0,
    val currentQuestionStoryReady: Boolean = false,
    val currentQuestionStoryQuality: String = "none",
    val currentQuestionCompleted: Boolean = false,
    val currentQuestionCanMoveNext: Boolean = false,
    val storyTargetQuestionNo: Int? = null,
    val storyTargetHasAnswer: Boolean = false,
    val storyTargetAnswerCount: Int = 0,
    val storyTargetStoryReady: Boolean = false,
    val storyTargetStoryQuality: String = "none",
    val storyTargetIsCurrentQuestion: Boolean = true,
)

data class SessionMessageData(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

data class GeneratedChapterData(
    val id: String,
    val sessionId: String,
    val title: String,
    val content: String,
    val chapterType: String,
    val sourceQuestionNo: Int? = null,
    val storyQualityAtGeneration: String? = null,
)

data class ChatMessageData(
    val id: String,
    val senderId: String,
    val content: String,
    val createdAt: String?,
)

data class ChapterDraftData(
    val id: String,
    val title: String,
    val preview: String,
    val chapterType: String,
    val createdAt: String,
    val sourceQuestionNo: Int? = null,
)

data class BookDetailData(
    val id: String,
    val title: String,
    val subtitle: String?,
    val createdAt: String? = null,
    val chapters: List<BookChapterPayloadData> = emptyList(),
    val body: String = "",
    val shared: Boolean = false,
    val sharedPostId: String? = null,
)

data class AutobiographyCreateData(
    val bookId: String,
)

data class BookShareResultData(
    val bookId: String,
    val postId: String,
)

data class BookChapterPayloadData(
    val id: String,
    val title: String,
    val content: String,
    val sourceQuestionNo: Int? = null,
)
