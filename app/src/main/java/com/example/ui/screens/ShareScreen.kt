package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ShareState
import com.example.ui.theme.*

@Composable
fun ShareScreen(
    shareState: ShareState,
    isSendingRole: Boolean,
    onStartShare: (Boolean) -> Unit, // true = Send, false = Receive
    onCancelShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (shareState) {
            is ShareState.Idle -> {
                ShareIdleView(onStartShare = onStartShare)
            }
            is ShareState.Discovering -> {
                ShareDiscoveringView(
                    isSending = isSendingRole,
                    onCancel = onCancelShare
                )
            }
            is ShareState.Connected -> {
                ShareTransferView(
                    isSending = isSendingRole,
                    connectedDevice = shareState.deviceName,
                    progress = shareState.transferProgress,
                    fileName = shareState.fileName,
                    speed = shareState.speedMbps,
                    onCancel = onCancelShare
                )
            }
            is ShareState.Completed -> {
                ShareCompletedView(
                    isSending = isSendingRole,
                    onFinished = onCancelShare
                )
            }
        }
    }
}

@Composable
fun ShareIdleView(onStartShare: (Boolean) -> Unit) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("share_idle_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Core Visual: Elegant Radar Graphics
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(if (isDark) Color(0xFF1E3A8A) else GoogleBlueLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Transform,
                    contentDescription = "Share visual",
                    tint = if (isDark) Color(0xFF93C5FD) else GoogleBlue,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Share offline",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Send and receive files extremely fast with nearby devices without using mobile data or Wi-Fi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SEND BUTTON
                Button(
                    onClick = { onStartShare(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("send_files_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Send", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // RECEIVE BUTTON
                OutlinedButton(
                    onClick = { onStartShare(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("receive_files_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleBlue)
                ) {
                    Icon(imageVector = Icons.Default.Downloading, contentDescription = "Receive")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Receive", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ShareDiscoveringView(
    isSending: Boolean,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("share_discovering_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSending) "Looking for nearby receivers..." else "Ready to receive...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Keep this app open and nearby other devices",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 48.dp)
        )

        // Radar Ripple Wave Animation Canvas
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "radar_waves")
            
            val wave1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 350f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave1"
            )
            val alpha1 by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha1"
            )

            val wave2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 350f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = 800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave2"
            )
            val alpha2 by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = 800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha2"
            )

            val wave3 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 350f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave3"
            )
            val alpha3 by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha3"
            )

            // Canvas drawing radar ripples
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = GoogleBlue.copy(alpha = alpha1),
                    radius = wave1,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = GoogleBlue.copy(alpha = alpha2),
                    radius = wave2,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = GoogleBlue.copy(alpha = alpha3),
                    radius = wave3,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // Radar Central Core Pulsing Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(GoogleBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSending) Icons.Default.Send else Icons.Default.Downloading,
                    contentDescription = "Pulsing core",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.width(180.dp)
        ) {
            Text(text = "Cancel", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShareTransferView(
    isSending: Boolean,
    connectedDevice: String,
    progress: Float,
    fileName: String,
    speed: Float,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("share_transfer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSending) "Sending files to" else "Receiving from",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = connectedDevice,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Progress Circular Loader with centered percentage
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = GoogleGreen,
                    trackColor = GoogleGreenLight
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoogleGreen
                    )
                    Text(
                        text = String.format("%.1f MB/s", speed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(150.dp)
            ) {
                Text(text = "Disconnect")
            }
        }
    }
}

@Composable
fun ShareCompletedView(
    isSending: Boolean,
    onFinished: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("share_completed_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(GoogleGreenLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = GoogleGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Transfer Completed!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSending) {
                    "Your files were successfully delivered with high speed local replication."
                } else {
                    "Saved file packages have been securely cataloged and integrated into your Downloads cache."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onFinished,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoogleGreen),
                modifier = Modifier.width(180.dp)
            ) {
                Text(text = "Share More", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
