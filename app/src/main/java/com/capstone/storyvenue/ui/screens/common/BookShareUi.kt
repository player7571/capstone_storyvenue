package com.capstone.storyvenue.ui.screens.common

import android.content.Context
import android.content.Intent
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.template.model.Button as KakaoButton
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.ui.theme.StoryVenueColors

enum class PdfShareMode { SAVE, KAKAO }

fun shareBookTextToKakaoTalk(context: Context, bookId: String, title: String, subtitle: String?, body: String) {
    val kakaoLink = Link(androidExecutionParams = mapOf("bookId" to bookId))
    val description = buildString {
        if (!subtitle.isNullOrBlank()) append("$subtitle\n\n")
        append(body.take(150).let { if (body.length > 150) "$it…" else it })
    }
    val feed = FeedTemplate(
        content = Content(
            title = title,
            description = description,
            link = kakaoLink,
        ),
        buttons = listOf(
            KakaoButton(title = "앱에서 보기", link = kakaoLink),
        ),
    )

    if (ShareClient.instance.isKakaoTalkSharingAvailable(context)) {
        ShareClient.instance.shareDefault(context, feed) { result, error ->
            if (result != null) context.startActivity(result.intent)
            else sharePlainText(context, bookId, title, subtitle, body)
        }
    } else {
        sharePlainText(context, bookId, title, subtitle, body)
    }
}

private fun sharePlainText(context: Context, bookId: String, title: String, subtitle: String?, body: String) {
    val subtitleLine = if (!subtitle.isNullOrBlank()) "\n$subtitle" else ""
    val text = "📖 $title$subtitleLine\n\n$body\n\n— StoryVenue에서 작성된 자서전\n앱에서 보기: storyvenue://book/$bookId"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.kakao.talk")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "자서전 공유하기",
            )
        )
    }
}

@Composable
fun PdfExportSheetContent(
    includeCover: Boolean,
    onIncludeCoverChange: (Boolean) -> Unit,
    coverBitmap: ImageBitmap?,
    onPickCoverImage: () -> Unit,
    onClearCoverImage: () -> Unit,
    shareMode: PdfShareMode,
    onShareModeChange: (PdfShareMode) -> Unit,
    isExporting: Boolean,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "PDF로 내보내기",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = StoryVenueColors.OnSurface,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "표지 포함하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StoryVenueColors.OnSurface,
                )
                Text(
                    text = "체크하면 표지 이미지를 고를 수 있어요.",
                    fontSize = 13.sp,
                    color = StoryVenueColors.SubText,
                )
            }
            Switch(
                checked = includeCover,
                onCheckedChange = { onIncludeCoverChange(it) },
                enabled = !isExporting,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StoryVenueColors.Primary,
                ),
            )
        }

        if (includeCover) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val bitmap = coverBitmap
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "표지 이미지",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onPickCoverImage,
                            enabled = !isExporting,
                            shape = RoundedCornerShape(50.dp),
                            border = null,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = StoryVenueColors.Surface,
                                contentColor = StoryVenueColors.OnSurface,
                            ),
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Text("이미지 변경", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        TextButton(
                            onClick = onClearCoverImage,
                            enabled = !isExporting,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text(
                                text = "제거",
                                fontSize = 14.sp,
                                color = StoryVenueColors.SubText,
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onPickCoverImage,
                        enabled = !isExporting,
                        shape = RoundedCornerShape(50.dp),
                        border = null,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = StoryVenueColors.Surface,
                            contentColor = StoryVenueColors.OnSurface,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("표지 이미지 선택", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = "이미지를 고르지 않으면 텍스트로 된 표지로 만들어요.",
                        fontSize = 12.sp,
                        color = StoryVenueColors.SubText,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "어떻게 받을까요?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = StoryVenueColors.OnSurface,
            )
            PdfShareModeRow(
                label = "다운로드 폴더에 저장",
                selected = shareMode == PdfShareMode.SAVE,
                enabled = !isExporting,
                onSelect = { onShareModeChange(PdfShareMode.SAVE) },
            )
            PdfShareModeRow(
                label = "카카오톡으로 공유",
                selected = shareMode == PdfShareMode.KAKAO,
                enabled = !isExporting,
                onSelect = { onShareModeChange(PdfShareMode.KAKAO) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onConfirm,
            enabled = !isExporting,
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StoryVenueColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = StoryVenueColors.Divider,
                disabledContentColor = StoryVenueColors.SubText,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = "만들기",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PdfShareModeRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = StoryVenueColors.Primary,
            ),
        )
        Text(
            text = label,
            fontSize = 16.sp,
            color = StoryVenueColors.OnSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
