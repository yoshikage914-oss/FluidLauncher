package com.yourlauncher.ui.lockscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourlauncher.ui.theme.liquidGlass

@Composable
fun LiquidLockScreen(
    wallpaperBackground: Painter,
    subjectForegroundMask: Painter, // Your segmented cutout for depth clock
    timeText: String = "23:23",
    dateWeatherText: String = "Sun, Nov 22  •  🌙 28°",
    onUnlock: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Base Wallpaper
        Image(
            painter = wallpaperBackground,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Depth Clock Layer (Behind Subject)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HyperIsland Live Pill
            Box(
                modifier = Modifier
                    .liquidGlass(cornerRadius = 50.dp, tintColor = Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "● Charging 80%  •  4.5G",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = dateWeatherText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Liquid Glass Gradient Clock
            Text(
                text = timeText,
                style = TextStyle(
                    fontSize = 118.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-3).sp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.25f)
                        )
                    )
                )
            )
        }

        // 3. Subject Cutout Layer (Overlaps bottom of the clock)
        Image(
            painter = subjectForegroundMask,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 4. Stacked Liquid Glass Notification Card + Biometric + Shortcuts
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stacked Glass Notification (HyperOS 4 style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        cornerRadius = 24.dp,
                        tintColor = Color(0xFF1C1C1E).copy(alpha = 0.45f)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Snapchat",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Updating messages...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "13m ago",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Luminous Fingerprint Sensor
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .liquidGlass(
                        cornerRadius = 32.dp,
                        luminousGlowColor = Color.Cyan.copy(alpha = 0.6f),
                        onClick = onUnlock
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = "Unlock",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Glass Quick Actions (Flashlight & Camera)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassCircleShortcut(icon = Icons.Rounded.FlashlightOn, onClick = {})
                GlassCircleShortcut(icon = Icons.Rounded.CameraAlt, onClick = {})
            }
        }
    }
}

@Composable
private fun GlassCircleShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
