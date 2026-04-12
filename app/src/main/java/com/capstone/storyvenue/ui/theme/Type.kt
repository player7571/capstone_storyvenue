package com.capstone.storyvenue.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.capstone.storyvenue.R

val SBAggroFamily = FontFamily(
    Font(R.font.sb_aggro_l, FontWeight.Light),
    Font(R.font.sb_aggro_m, FontWeight.Normal),
    Font(R.font.sb_aggro_b, FontWeight.Bold),
)

val StoryVenueTypography = Typography(
    displayLarge   = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    bodyLarge      = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)