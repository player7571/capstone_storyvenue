package com.capstone.storyvenue.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capstone.storyvenue.ui.screens.BookPreviewScreen
import com.capstone.storyvenue.ui.screens.ChapterDraftScreen
import com.capstone.storyvenue.ui.screens.ChatListScreen
import com.capstone.storyvenue.ui.screens.ChatRoomScreen
import com.capstone.storyvenue.ui.screens.FeedDetailScreen
import com.capstone.storyvenue.ui.screens.FeedScreen
import com.capstone.storyvenue.ui.screens.HomeScreen
import com.capstone.storyvenue.ui.screens.LoginScreen
import com.capstone.storyvenue.ui.screens.NotificationScreen
import com.capstone.storyvenue.ui.screens.PostListMode
import com.capstone.storyvenue.ui.screens.PostListScreen
import com.capstone.storyvenue.ui.screens.ProfileScreen
import com.capstone.storyvenue.ui.screens.SignUpScreen
import com.capstone.storyvenue.ui.screens.SplashScreen
import com.capstone.storyvenue.ui.screens.VoiceInterviewScreen

object Routes {
    const val SPLASH          = "splash"
    const val LOGIN           = "login"
    const val SIGNUP          = "signup"
    const val HOME            = "home"
    const val VOICE_INTERVIEW = "voice_interview?sessionId={sessionId}"
    const val CHAPTER_DRAFT   = "chapter_draft/{sessionId}"
    const val BOOK_PREVIEW    = "book_preview"
    const val FEED            = "feed"
    const val FEED_DETAIL     = "feed_detail/{postId}"
    const val CHAT_LIST       = "chat_list"
    const val CHAT_ROOM       = "chat_room/{userId}"
    const val PROFILE         = "profile"
    const val MY_POSTS        = "profile/my_posts"
    const val LIKED_POSTS     = "profile/liked_posts"
    const val NOTIFICATIONS   = "notifications"
    fun feedDetail(postId: String) = "feed_detail/$postId"
    fun chatRoom(userId: String) = "chat_room/$userId"
    fun voiceInterview(sessionId: String? = null) =
        if (sessionId.isNullOrBlank()) "voice_interview" else "voice_interview?sessionId=$sessionId"
    fun chapterDraft(sessionId: String) = "chapter_draft/$sessionId"
}

@Composable
fun StoryVenueNavGraph(
    navController: NavHostController = rememberNavController(),
    hasToken: Boolean = false,
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                hasToken = hasToken,
                onNavigateToHome = { navController.navigate(Routes.FEED) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Routes.FEED) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onNavigateToSignUp = { navController.navigate(Routes.SIGNUP) },
            )
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(
                onSignUpSuccess = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SIGNUP) { inclusive = true } } },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNewInterview = { navController.navigate(Routes.voiceInterview()) },
                onSessionClick = { session -> navController.navigate(Routes.voiceInterview(session.id)) },
                onNotificationClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
                onFeedClick = { navController.navigate(Routes.FEED) },
                onChatClick = { navController.navigate(Routes.CHAT_LIST) },
            )
        }
        composable(
            route = Routes.VOICE_INTERVIEW,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { back ->
            val sessionId = back.arguments?.getString("sessionId")
            VoiceInterviewScreen(
                initialSessionId = sessionId,
                onBack = { navController.popBackStack() },
                onGenerateChapter = { sid -> navController.navigate(Routes.chapterDraft(sid)) },
            )
        }
        composable(
            route = Routes.CHAPTER_DRAFT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { back ->
            val sessionId = back.arguments?.getString("sessionId") ?: ""
            ChapterDraftScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onAddToBook = { navController.navigate(Routes.BOOK_PREVIEW) },
            )
        }
        composable(Routes.BOOK_PREVIEW) {
            BookPreviewScreen(
                onBack = { navController.popBackStack() },
                onAddChapter = { navController.navigate(Routes.voiceInterview()) },
                onPostToFeed = { navController.navigate(Routes.FEED) },
            )
        }
        composable(Routes.FEED) {
            FeedScreen(
                onPostClick = { post -> navController.navigate(Routes.feedDetail(post.id)) },
                onNotificationClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onHomeClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                onChatClick = { navController.navigate(Routes.CHAT_LIST) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.FEED_DETAIL) { back ->
            val postId = back.arguments?.getString("postId") ?: ""
            FeedDetailScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                onChatClick = { authorId -> navController.navigate(Routes.chatRoom(authorId)) },
            )
        }
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onPartnerClick = { partner -> navController.navigate(Routes.chatRoom(partner.userId)) },
                onNotificationClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onHomeClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                onFeedClick = { navController.navigate(Routes.FEED) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.CHAT_ROOM) { back ->
            val userId = back.arguments?.getString("userId") ?: ""
            ChatRoomScreen(
                otherUserId = userId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNotificationClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onHomeClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                onFeedClick = { navController.navigate(Routes.FEED) },
                onChatClick = { navController.navigate(Routes.CHAT_LIST) },
                onMyPosts = { navController.navigate(Routes.MY_POSTS) },
                onLikedPosts = { navController.navigate(Routes.LIKED_POSTS) },
            )
        }
        composable(Routes.MY_POSTS) {
            PostListScreen(
                mode = PostListMode.MY,
                onBack = { navController.popBackStack() },
                onPostClick = { post -> navController.navigate(Routes.feedDetail(post.id)) },
            )
        }
        composable(Routes.LIKED_POSTS) {
            PostListScreen(
                mode = PostListMode.LIKED,
                onBack = { navController.popBackStack() },
                onPostClick = { post -> navController.navigate(Routes.feedDetail(post.id)) },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationScreen(onBack = { navController.popBackStack() })
        }
    }
}
