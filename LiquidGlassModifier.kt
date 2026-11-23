package com.yourlauncher.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ColorOS 17 Luminous Touch + HyperOS 4 Soft Light Glass Modifier
fun Modifier.liquidGlass(
    cornerRadius: Dp = 26.dp,
    tintColor: Color = Color.White.copy(alpha = 0.12f),
    luminousGlowColor: Color = Color(0xFF64D2FF).copy(alpha = 0.35f),
    enableElasticTouch: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var touchPoint by remember { mutableStateOf(Offset.Unspecified) }
    var isPressed by remember { mutableStateOf(false) }

    // Elastic compression on touch (ColorOS 17 Living Flow)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enableElasticTouch) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 450f),
        label = "elasticScale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "luminousGlow"
    )

    val shape = RoundedCornerShape(cornerRadius)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            clip = true
            this.shape = shape
        }
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tintColor.copy(alpha = tintColor.alpha * 1.4f),
                    tintColor.copy(alpha = tintColor.alpha * 0.7f)
                )
            ),
            shape = shape
        )
        // Specular "Liquid Glass" rim light (top-left bright, bottom-right soft)
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.28f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            shape = shape
        )
        // Luminous Rendering Engine finger-tracking glow
        .drawWithContent {
            drawContent()
            if (glowAlpha > 0.01f && touchPoint != Offset.Unspecified) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            luminousGlowColor.copy(alpha = luminousGlowColor.alpha * glowAlpha),
                            Color.Transparent
                        ),
                        center = touchPoint,
                        radius = size.maxDimension * 0.85f
                    ),
                    radius = size.maxDimension * 0.85f,
                    center = touchPoint,
                    blendMode = BlendMode.Plus
                )
            }
        }
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = { offset ->
                    touchPoint = offset
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick?.invoke() }
            )
        }
} 
