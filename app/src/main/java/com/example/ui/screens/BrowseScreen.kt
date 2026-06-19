package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.FilesViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: FilesViewModel,
    modifier: Modifier = Modifier
) {
    val currentCategory by viewModel.currentCategory.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val isSafeFolderUnlocked by viewModel.isSafeFolderUnlocked.collectAsState()
    val activeSearchQuery by viewModel.activeSearchQuery.collectAsState()

    // Screen Routing State
    var selectingSafeLockMode by remember { mutableStateOf(false) }

    // Navigation Interceptor for physical back key
    BackHandler(enabled = currentCategory != null || currentFolder != null || isSafeFolderUnlocked || selectingSafeLockMode) {
        when {
            selectingSafeLockMode -> selectingSafeLockMode = false
            isSafeFolderUnlocked -> viewModel.lockSafeFolder()
            currentFolder != null -> {
                val popped = viewModel.navigateUpFolder()
                if (!popped) {
                    viewModel.exploreInternalStorage()
                }
            }
            currentCategory != null -> viewModel.viewCategory(null)
        }
    }

    Scaffold(
        topBar = {
            BrowseTopBar(
                title = when {
                    selectingSafeLockMode -> "Configure Lock"
                    isSafeFolderUnlocked -> "Safe Folder"
                    currentFolder != null -> "Internal Storage"
                    currentCategory != null -> currentCategory?.name?.lowercase()?.replaceFirstChar { it.titlecase() } ?: "Files"
                    else -> "Files"
                },
                showBack = currentCategory != null || currentFolder != null || isSafeFolderUnlocked || selectingSafeLockMode,
                onBack = {
                    when {
                        selectingSafeLockMode -> selectingSafeLockMode = false
                        isSafeFolderUnlocked -> viewModel.lockSafeFolder()
                        currentFolder != null -> viewModel.navigateUpFolder()
                        currentCategory != null -> viewModel.viewCategory(null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                selectingSafeLockMode -> {
                    SafeFolderLockConfig(viewModel = viewModel, onConfigured = { selectingSafeLockMode = false })
                }
                isSafeFolderUnlocked -> {
                    val safeFiles by viewModel.safeFolderFiles.collectAsState()
                    SafeFolderContents(
                        files = safeFiles,
                        onUnlockMore = { viewModel.lockSafeFolder() },
                        onRestore = { viewModel.restoreFileFromSafe(it) },
                        onDelete = { viewModel.deleteFile(it) }
                    )
                }
                currentFolder != null || currentFolder == "" -> {
                    // Internal storage folder explorer
                    val contents by viewModel.folderContents.collectAsState()
                    FolderBrowser(
                        currentPath = currentFolder,
                        contents = contents,
                        onFolderClick = { viewModel.navigateIntoFolder(it) },
                        onFileAction = { action, file ->
                            when (action) {
                                FileAction.DELETE -> viewModel.deleteFile(file)
                                FileAction.FAVORITE -> viewModel.toggleFavorite(file)
                                FileAction.SECURE -> viewModel.moveFileToSafe(file)
                                FileAction.RENAME -> { /* Handled separately through dialog */ }
                            }
                        },
                        onRename = { file, name -> viewModel.renameFile(file, name) }
                    )
                }
                currentCategory != null -> {
                    val catFiles by viewModel.categoryFiles.collectAsState()
                    CategoryFilesViewer(
                        category = currentCategory!!,
                        files = catFiles,
                        onAction = { action, file ->
                            when (action) {
                                FileAction.DELETE -> viewModel.deleteFile(file)
                                FileAction.FAVORITE -> viewModel.toggleFavorite(file)
                                FileAction.SECURE -> viewModel.moveFileToSafe(file)
                                FileAction.RENAME -> { /* Handled below */ }
                            }
                        },
                        onRename = { file, name -> viewModel.renameFile(file, name) }
                    )
                }
                else -> {
                    // Render Main Browse Index Dashboard
                    BrowseDashboardView(
                        viewModel = viewModel,
                        onSafeFolderClick = {
                            viewModel.checkSafeFolderHasKey()
                            val exists = viewModel.hasSafeFolderPasscode.value
                            if (exists) {
                                // Open passcode confirmation sheet
                                isLockVerificationOpen = true
                            } else {
                                selectingSafeLockMode = true
                            }
                        }
                    )
                }
            }

            // Lock verification Modal Dialog
            if (isLockVerificationOpen) {
                SafeFolderUnlockDialog(
                    onVerify = { pin ->
                        val success = viewModel.unlockSafeFolder(pin)
                        if (success) {
                            isLockVerificationOpen = false
                        }
                        success
                    },
                    onDismiss = { isLockVerificationOpen = false }
                )
            }
        }
    }
}

private var isLockVerificationOpen by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    if (showBack) {
        TopAppBar(
            title = {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("app_bar_back_button")) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF2A2B2D) else Color(0xFFEEF2F6))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu icon",
                    tint = if (isDark) Color(0xFF9E9E9E) else Color(0xFF475569)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Search your files",
                    color = if (isDark) Color(0xFF9E9E9E) else Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseDashboardView(
    viewModel: FilesViewModel,
    onSafeFolderClick: () -> Unit
) {
    val favorites by viewModel.favoriteFiles.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Storage Bar Mini
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectTab(0) } // Jump to storage analyzer tab
                    .testTag("dashboard_storage_bar"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = GoogleBlue
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage used: ${(storageInfo.usedPercentage * 100).toInt()}%",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${storageInfo.freeSpaceString} free • Tap to review",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Go check analysis")
                }
            }
        }

        // 2. Categories Group
        item {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            // Category Buttons Matrix Grid
            val cats = listOf(
                Pair(FileType.DOWNLOAD, Icons.Default.FileDownload),
                Pair(FileType.IMAGE, Icons.Default.Image),
                Pair(FileType.VIDEO, Icons.Default.PlayCircle),
                Pair(FileType.AUDIO, Icons.Default.Audiotrack),
                Pair(FileType.DOCUMENT, Icons.Default.Description),
                Pair(FileType.APP, Icons.Default.Android)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cats.size) { idx ->
                    val (type, icon) = cats[idx]
                    CategoryGridItem(
                        title = type.name.lowercase().replaceFirstChar { it.titlecase() },
                        icon = icon,
                        onClick = { viewModel.viewCategory(type) },
                        testTagStr = "category_${type.name.lowercase()}"
                    )
                }
            }
        }

        // 3. Collections Group (Favorites, Lock folder)
        item {
            Text(
                text = "Collections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        // Favorites item
        item {
            CollectionListItem(
                title = "Favorites",
                subText = "${favorites.size} items",
                icon = Icons.Default.Star,
                onClick = { viewModel.viewCategory(FileType.OTHER) }, // Leverage standard files filter for quick Favorites representation
                iconColor = GoogleYellow,
                testTagStr = "collection_favorites"
            )
        }

        // Safe Folder Item
        item {
            CollectionListItem(
                title = "Safe folder",
                subText = "Locked with passcode",
                icon = Icons.Default.Lock,
                onClick = onSafeFolderClick,
                iconColor = GoogleBlue,
                testTagStr = "collection_safefolder"
            )
        }

        // 4. Storage devices Root Directories Click
        item {
            Text(
                text = "Storage devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }

        item {
            // Internal storage explorer entrance
            CollectionListItem(
                title = "Internal storage",
                subText = "${storageInfo.freeSpaceString} free",
                icon = Icons.Default.SdStorage,
                onClick = { viewModel.exploreInternalStorage() },
                iconColor = GoogleGreen,
                testTagStr = "storage_internal"
            )
        }
    }
}

@Composable
fun CategoryGridItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTagStr: String
) {
    val isDark = isSystemInDarkTheme()
    val bgC = if (isDark) {
        when (title) {
            "Download" -> Color(0xFF1E3A8A)
            "Image" -> Color(0xFF1E3A8A)
            "Video" -> Color(0xFF7C2D12)
            "Audio" -> Color(0xFF701A75)
            "Document" -> Color(0xFF312E81)
            "App" -> Color(0xFF064E3B)
            else -> Color(0xFF78350F)
        }
    } else {
        when (title) {
            "Download" -> Color(0xFFDBEAFE)
            "Image" -> Color(0xFFDBEAFE)
            "Video" -> Color(0xFFFFEDD5)
            "Audio" -> Color(0xFFFCE7F3)
            "Document" -> Color(0xFFE0E7FF)
            "App" -> Color(0xFFDCFCE7)
            else -> Color(0xFFFEF9C3)
        }
    }

    val iconC = if (isDark) {
        when (title) {
            "Download" -> Color(0xFF93C5FD)
            "Image" -> Color(0xFF93C5FD)
            "Video" -> Color(0xFFFDBA74)
            "Audio" -> Color(0xFFF472B6)
            "Document" -> Color(0xFFA5B4FC)
            "App" -> Color(0xFF6EE7B7)
            else -> Color(0xFFFDE047)
        }
    } else {
        when (title) {
            "Download" -> Color(0xFF1D4ED8)
            "Image" -> Color(0xFF1D4ED8)
            "Video" -> Color(0xFFC2410C)
            "Audio" -> Color(0xFFBE185D)
            "Document" -> Color(0xFF4338CA)
            "App" -> Color(0xFF15803D)
            else -> Color(0xFFB45309)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTagStr),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgC),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconC,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CollectionListItem(
    title: String,
    subText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    iconColor: Color,
    testTagStr: String
) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTagStr),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next")
        }
    }
}

// SAFE FOLDER CONFIG GRAPHICS
@Composable
fun SafeFolderLockConfig(
    viewModel: FilesViewModel,
    onConfigured: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = Enter PIN, 2 = Confirm PIN
    var enteredFirstPin by remember { mutableStateOf("") }
    var helperText by remember { mutableStateOf("Set a 4-digit PIN lock for your private data.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("safefolder_config_view"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(90.dp)
                .background(GoogleBlueLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = "Lock configuring",
                tint = GoogleBlue,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (step == 1) "Choose a PIN passcode" else "Confirm PIN passcode",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = helperText,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // Custom PIN Numerical Keys Entry View
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            for (i in 0 until 4) {
                val entered = pinValue.getOrNull(i) != null
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (entered) GoogleBlue else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (entered) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }

        // Custom Grid pad
        PinPadKeyboard(
            onKeyPress = { k ->
                if (pinValue.length < 4) {
                    pinValue += k
                }
            },
            onDelete = {
                if (pinValue.isNotEmpty()) {
                    pinValue = pinValue.substring(0, pinValue.length - 1)
                }
            },
            onNext = {
                if (pinValue.length == 4) {
                    if (step == 1) {
                        enteredFirstPin = pinValue
                        pinValue = ""
                        step = 2
                        helperText = "Enter PIN again to confirm your secret passcode."
                    } else {
                        if (pinValue == enteredFirstPin) {
                            viewModel.setupSafePasscode(pinValue)
                            onConfigured()
                        } else {
                            // Incorrect confirm matching
                            pinValue = ""
                            helperText = "Incorrect pin matching! Try again."
                        }
                    }
                }
            },
            isActionEnabled = pinValue.length == 4
        )
    }
}

@Composable
fun PinPadKeyboard(
    onKeyPress: (String) -> Unit,
    onDelete: () -> Unit,
    onNext: () -> Unit,
    isActionEnabled: Boolean
) {
    val items = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "Delete", "0", "Next"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0 until 3) {
                    val key = items[row * 3 + col]
                    val isNumber = key != "Delete" && key != "Next"

                    Surface(
                        onClick = {
                            when (key) {
                                "Delete" -> onDelete()
                                "Next" -> { if (isActionEnabled) onNext() }
                                else -> onKeyPress(key)
                            }
                        },
                        enabled = isNumber || (key == "Delete") || (key == "Next" && isActionEnabled),
                        shape = CircleShape,
                        color = if (key == "Next") {
                            if (isActionEnabled) GoogleBlue else MaterialTheme.colorScheme.surfaceVariant
                        } else Color.Transparent,
                        contentColor = if (key == "Next") Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "Delete") {
                                Icon(Icons.Default.Backspace, contentDescription = "Delete key")
                            } else if (key == "Next") {
                                Icon(Icons.Default.Done, contentDescription = "Confirm key")
                            } else {
                                Text(text = key, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// SAFE FOLDER UNLOCK MODAL
@Composable
fun SafeFolderUnlockDialog(
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("safefolder_verification_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Lockscreen")
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(GoogleBlueLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Safe Lock Icon",
                        tint = GoogleBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Unlock Safe Folder", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isError) "Incorrect passcode! Try again." else "Enter your 4-digit PIN security lock.",
                    fontSize = 14.sp,
                    color = if (isError) GoogleRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
                )

                // PIN Entry Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 48.dp)
                ) {
                    for (i in 0 until 4) {
                        val entered = pinText.getOrNull(i) != null
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (entered) GoogleBlue else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (entered) {
                                Box(modifier = Modifier.size(12.dp).background(Color.White, CircleShape))
                            }
                        }
                    }
                }

                PinPadKeyboard(
                    onKeyPress = { k ->
                        if (pinText.length < 4) {
                            pinText += k
                        }
                    },
                    onDelete = {
                        if (pinText.isNotEmpty()) {
                            pinText = pinText.substring(0, pinText.length - 1)
                        }
                    },
                    onNext = {
                        val passed = onVerify(pinText)
                        if (!passed) {
                            pinText = ""
                            isError = true
                        }
                    },
                    isActionEnabled = pinText.length == 4
                )
            }
        }
    }
}

// SAFE FOLDER CONTENT VIEWER
@Composable
fun SafeFolderContents(
    files: List<FileItem>,
    onUnlockMore: () -> Unit,
    onRestore: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit
) {
    if (files.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("safefolder_empty_state"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(GoogleBlueLight.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LockClock,
                    contentDescription = "Safe Folder Empty",
                    tint = GoogleBlue,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Safe folder is empty", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Move photos, videos, or confidential documents here from category menu cards to secure them.",
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            Button(onClick = onUnlockMore, colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)) {
                Text(text = "Go to Browse dashboard")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Secured storage files",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoogleBlue,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(files) { file ->
                    SafeFileRow(
                        file = file,
                        onRestoreClick = { onRestore(file) },
                        onDeleteClick = { onDelete(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun SafeFileRow(
    file: FileItem,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Prevents direct view without re-unsecuring or can inspect if desired */ }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (bgC, tint, icon) = getFileVisualPalette(file.fileType)
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(bgC, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = file.name, tint = tint)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.sizeString,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Box {
            IconButton(onClick = { expandedMenu = true }) {
                Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "Menu options")
            }

            DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Restore (Move out)") },
                    onClick = {
                        onRestoreClick()
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = "Move out") }
                )
                DropdownMenuItem(
                    text = { Text("Delete permanently", color = GoogleRed) },
                    onClick = {
                        onDeleteClick()
                        expandedMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = GoogleRed) }
                )
            }
        }
    }
}

// GENERAL UTILITY LIST FILE VIEWER
@Composable
fun CategoryFilesViewer(
    category: FileType,
    files: List<FileItem>,
    onAction: (FileAction, FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit
) {
    var isRenameDialogOpen by remember { mutableStateOf<FileItem?>(null) }

    if (files.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp).testTag("category_empty_state"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(GoogleBlueLight.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Empty list",
                    tint = GoogleBlue,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "No files found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Download files or place documents inside your file sandbox directory.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { file ->
                FileItemRow(
                    file = file,
                    onActionClick = { act ->
                        if (act == FileAction.RENAME) {
                            isRenameDialogOpen = file
                        } else {
                            onAction(act, file)
                        }
                    }
                )
            }
        }
    }

    if (isRenameDialogOpen != null) {
        RenameDialog(
            file = isRenameDialogOpen!!,
            onConfirm = { name ->
                onRename(isRenameDialogOpen!!, name)
                isRenameDialogOpen = null
            },
            onDismiss = { isRenameDialogOpen = null }
        )
    }
}

enum class FileAction {
    DELETE, FAVORITE, SECURE, RENAME
}

@Composable
fun FileItemRow(
    file: FileItem,
    onActionClick: (FileAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Optional detail view */ }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (bg, tint, icon) = getFileVisualPalette(file.fileType)
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(bg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = file.name, tint = tint)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${file.sizeString} • ${formatDate(file.lastModified)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "Actions menu")
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        onActionClick(FileAction.RENAME)
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Rename file") }
                )
                DropdownMenuItem(
                    text = { Text(if (file.isFavorite) "Remove Favorite" else "Add to Favorites") },
                    onClick = {
                        onActionClick(FileAction.FAVORITE)
                        expanded = false
                    },
                    leadingIcon = { Icon(if (file.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder, contentDescription = "Fav file") }
                )
                DropdownMenuItem(
                    text = { Text("Move to Safe folder") },
                    onClick = {
                        onActionClick(FileAction.SECURE)
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Safe Lock") }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = GoogleRed) },
                    onClick = {
                        onActionClick(FileAction.DELETE)
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete file", tint = GoogleRed) }
                )
            }
        }
    }
}

// DIRECT FOLDER DIRECTORIES BROWSER
@Composable
fun FolderBrowser(
    currentPath: String?,
    contents: List<FileItem>,
    onFolderClick: (String) -> Unit,
    onFileAction: (FileAction, FileItem) -> Unit,
    onRename: (FileItem, String) -> Unit
) {
    var isRenameTarget by remember { mutableStateOf<FileItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().testTag("folder_explorer_view")) {
        // Path indicator breadcrumb
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Home, contentDescription = "Root", tint = GoogleBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentPath.isNullOrEmpty()) "Root /" else "Root/${currentPath.substringAfter("DemoFiles/", "")}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (contents.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Empty Dir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Text(text = "This folder is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(contents) { file ->
                    if (file.isDirectory) {
                        FolderItemRow(
                            folder = file,
                            onClick = { onFolderClick(file.path) }
                        )
                    } else {
                        FileItemRow(
                            file = file,
                            onActionClick = { act ->
                                if (act == FileAction.RENAME) {
                                    isRenameTarget = file
                                } else {
                                    onFileAction(act, file)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (isRenameTarget != null) {
        RenameDialog(
            file = isRenameTarget!!,
            onConfirm = { name ->
                onRename(isRenameTarget!!, name)
                isRenameTarget = null
            },
            onDismiss = { isRenameTarget = null }
        )
    }
}

@Composable
fun FolderItemRow(
    folder: FileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(GoogleBlueLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Folder, contentDescription = folder.name, tint = GoogleBlue)
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            // Show subtitle date
            Text(
                text = formatDate(folder.lastModified),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open folder")
    }
}

// RENAME POPUP DIALOG
@Composable
fun RenameDialog(
    file: FileItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textState by remember { mutableStateOf(file.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(text = "Enter a new label for this item.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (textState.isNotBlank()) onConfirm(textState) },
                colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

// Helper colors and icons definitions
@Composable
fun getFileVisualPalette(type: FileType): Triple<Color, Color, ImageVector> {
    return when (type) {
        FileType.IMAGE -> Triple(GoogleGreenLight, GoogleGreen, Icons.Default.Image)
        FileType.VIDEO -> Triple(GoogleRedLight, GoogleRed, Icons.Default.PlayCircle)
        FileType.AUDIO -> Triple(GoogleYellowLight, GoogleYellow, Icons.Default.Audiotrack)
        FileType.DOCUMENT -> Triple(GoogleBlueLight, GoogleBlue, Icons.Default.Description)
        FileType.APP -> Triple(GoogleGreenLight, GoogleGreen, Icons.Default.Android)
        FileType.FOLDER -> Triple(GoogleBlueLight, GoogleBlue, Icons.Default.Folder)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.FolderZip)
    }
}

fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "Jun 19, 2026"
    }
}
