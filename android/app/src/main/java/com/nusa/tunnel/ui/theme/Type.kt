package com.nusa.tunnel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nusa.tunnel.R

val Montserrat = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

val NusaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, letterSpacing = 0.1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Light,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Light,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Montserrat, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, letterSpacing = 0.4.sp,
    ),
)
