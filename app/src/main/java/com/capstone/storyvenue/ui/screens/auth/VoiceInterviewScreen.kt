package com.capstone.storyvenue.ui.screens.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.SBAggroFamily
import com.capstone.storyvenue.ui.theme.StoryVenueColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInterviewScreen(
    onBack: () -> Unit = {},
    onGenerateChapter: () -> Unit = {},
) {
    var isRecording by remember { mutableStateOf(false) }

    val currentQuestion = "Q. 어린 시절,\n가장 기억에 남는\n순간은 무엇인가요?"
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
                    TextButton(onClick = onBack) {
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

            // AI 질문 카드
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
                        text = currentQuestion,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = StoryVenueColors.OnSurface,
                        fontFamily = SBAggroFamily,
                        lineHeight = 34.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 진행도 바
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

            // 마이크 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .scale(if (isRecording) scale else 1f)
                    .clip(CircleShape)
                    .background(StoryVenueColors.Surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isRecording = true
                                tryAwaitRelease()
                                isRecording = false
                            }
                        )
                    },
            ) {
                Text(text = "🎙️", fontSize = 52.sp)
            }

            Spacer(Modifier.height(20.dp))

            // 녹음 상태 텍스트
            Text(
                text = if (isRecording) "녹음 중 . . ." else "",
                fontSize = 16.sp,
                color = StoryVenueColors.Error,
                fontFamily = SBAggroFamily,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.weight(1f))

            // 이야기 생성하기 버튼
            StoryButton(
                text = "이야기 생성하기",
                onClick = onGenerateChapter,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}