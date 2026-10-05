package mx.dev1.naturequest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Slightly larger than the Material defaults: the phone is glanced at, not read.
val AppTypography = Typography().copy(
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)
