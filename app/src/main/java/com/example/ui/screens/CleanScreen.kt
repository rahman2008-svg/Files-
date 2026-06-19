package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CleanupCardState
import com.example.data.CleanupType
import com.example.data.StorageSpaceInfo
import com.example.data.formatSize
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(
    storageInfo: StorageSpaceInfo,
    cleanupCards: List<CleanupCardState>,
    isCleaning: Boolean,
    onCleanJunk: (Long) -> Unit,
    onViewCardFiles: (CleanupCardState) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Bar Card
            item {
                StorageStatusCard(storageInfo = storageInfo)
            }

            // Cards Listing
            items(cleanupCards) { card ->
                CleanupCard(
                    card = card,
                    onClean = { onCleanJunk(card.sizeBytes) },
                    onView = { onViewCardFiles(card) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Broom Sweeping Custom Animation overlay
        if (isCleaning) {
            SweepingProgressOverlay()
        }
    }
}

@Composable
fun StorageStatusCard(storageInfo: StorageSpaceInfo) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("storage_status_card"),
        shape = RoundedCornerShape(24.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${storageInfo.usedSpaceString} used of ${storageInfo.totalSpaceString}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(if (isDark) Color(0xFF1E3A8A) else GoogleBlueLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Storage outline",
                        tint = if (isDark) Color(0xFF93C5FD) else GoogleBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Premium Custom Storage Slider Bar
            val animatedProgress by animateFloatAsState(
                targetValue = storageInfo.usedPercentage,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "storage_progress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(GoogleBlue, GoogleBlueDark)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(storageInfo.usedPercentage * 100).toInt()}% Used",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GoogleBlue
                )
                Text(
                    text = "${storageInfo.freeSpaceString} free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoogleGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CleanupCard(
    card: CleanupCardState,
    onClean: () -> Unit,
    onView: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val (iconBg, iconTint, icon) = when (card.type) {
        CleanupType.JUNK -> {
            if (isDark) Triple(Color(0xFF1E3A8A), Color(0xFF93C5FD), Icons.Default.CleaningServices)
            else Triple(GoogleBlueLight, GoogleBlue, Icons.Default.CleaningServices)
        }
        CleanupType.DUPLICATES -> {
            if (isDark) Triple(Color(0xFF064E3B), Color(0xFF6EE7B7), Icons.Default.FileCopy)
            else Triple(GoogleGreenLight, GoogleGreen, Icons.Default.FileCopy)
        }
        CleanupType.LARGE_FILES -> {
            if (isDark) Triple(Color(0xFF78350F), Color(0xFFFDE047), Icons.Outlined.FolderZip)
            else Triple(GoogleYellowLight, GoogleYellow, Icons.Outlined.FolderZip)
        }
        CleanupType.OLD_SCREENSHOTS -> {
            if (isDark) Triple(Color(0xFF7C2D12), Color(0xFFF472B6), Icons.Default.Screenshot)
            else Triple(GoogleRedLight, GoogleRed, Icons.Default.Screenshot)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("cleanup_card_${card.type.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(iconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = card.title,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = card.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Found ${card.fileCount} items • ${card.sizeString}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (card.type != CleanupType.JUNK) {
                    TextButton(
                        onClick = onView,
                        modifier = Modifier.testTag("action_view_${card.type.name.lowercase()}")
                    ) {
                        Text(text = "Select and free up")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onClean,
                    colors = ButtonDefaults.buttonColors(containerColor = iconTint, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("action_clean_${card.type.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clean sweep icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Clean ${card.sizeString}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SweepingProgressOverlay() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Broom Sweeping Infinite Rotation Spec
                val infiniteTransition = rememberInfiniteTransition(label = "broom_rotation")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = -30f,
                    targetValue = 30f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "sweep"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(GoogleBlueLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = "Broom Sweeping",
                        tint = GoogleBlue,
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(rotationAngle)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Cleaning up junk...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Deleting safe caches, temporary system dumps, and redundant logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    color = GoogleBlue,
                    trackColor = GoogleBlueLight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
