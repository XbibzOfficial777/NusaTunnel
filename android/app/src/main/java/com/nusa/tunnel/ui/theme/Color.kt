package com.nusa.tunnel.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Palet "Midnight Aurora" (satu keluarga dengan desktop NusaTunnel) ----
// Basis gelap laut malam + aurora spruce/mint (aksen aksi ala DESIGN.md)

val Aurora0 = Color(0xFF070D1A)   // laut paling dalam (background)
val Aurora1 = Color(0xFF0A1120)
val Aurora2 = Color(0xFF0E1A32)   // permukaan panel

val Spruce = Color(0xFF00C27A)    // aksi utama (spruce terang utk dark bg)
val SpruceDeep = Color(0xFF008254)
val Mint = Color(0xFF7CE8B4)
val MintDim = Color(0xFF9ADFC0)
val Lime = Color(0xFFB4E34A)      // aksen sekunder

val Frost = Color(0xFFE9F2FF)     // teks utama
val FrostDim = Color(0xFF9FB0CE)  // teks sekunder
val FrostFaint = Color(0xFF5F7096)

val Danger = Color(0xFFFF6B7A)
val Warn = Color(0xFFFFC55C)

// Kaca: putih transparan berlapis (liquid glass)
val GlassFill = Color(0x14FFFFFF)
val GlassFillStrong = Color(0x1FFFFFFF)
val GlassStroke = Color(0x2EFFFFFF)
val GlassShine = Color(0x3DFFFFFF)

val AuroraGradient = Brush.linearGradient(
    0f to Color(0xFF071021),
    0.45f to Color(0xFF0A1428),
    0.75f to Color(0xFF0B1E33),
    1f to Color(0xFF07261F),
)

val AuroraGlow = Brush.radialGradient(
    colors = listOf(Color(0x6600C27A), Color(0x00000000)),
)

val AuroraMintGlow = Brush.radialGradient(
    colors = listOf(Color(0x447CE8B4), Color(0x00000000)),
)

val GlassGradient = Brush.linearGradient(
    0f to Color(0x1FFFFFFF),
    0.5f to Color(0x0FFFFFFF),
    1f to Color(0x1AFFFFFF),
)

val ButtonGradient = Brush.linearGradient(
    0f to Spruce,
    1f to SpruceDeep,
)
