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
    displayLarge   = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    bodyLarge      = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelSmall     = TextStyle(fontFamily = SBAggroFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
)