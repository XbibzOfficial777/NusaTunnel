package com.nusa.tunnel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nusa.tunnel.ui.theme.GlassGradient
import com.nusa.tunnel.ui.theme.GlassShine
import com.nusa.tunnel.ui.theme.GlassStroke

/**
 * Kartu "liquid glass": permukaan translusen berlapis + garis tepi
 * berkilau + highlight spekular di atas — saudara mobile dari
 * backdrop-blur desktop.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(GlassGradient)
            .border(0.6.dp, GlassStroke, RoundedCornerShape(corner))
    ) {
        // kilau spekular kiri-atas (garis cahaya tipis)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corner))
                .background(
                    Brush.linearGradient(
                        colors = listOf(GlassShine, Color.Transparent, Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(600f, 400f),
                    )
                )
        )
        content()
    }
}
