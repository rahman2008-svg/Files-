package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface ShareState {
    object Idle : ShareState
    object Discovering : ShareState
    data class Connected(val deviceName: String, val transferProgress: Float, val fileName: String, val speedMbps: Float) : ShareState
    object Completed : ShareState
}

class FilesViewModel(private val repository: FileManagerRepository) : ViewModel() {

    // Current Tab
    private val _currentTab = MutableStateFlow(0) // 0 = Clean, 1 = Browse, 2 = Share
    val currentTab = _currentTab.asStateFlow()

    // Storage Info State
    private val _storageInfo = MutableStateFlow(repository.getStorageInfo())
    val storageInfo = _storageInfo.asStateFlow()

    // Clean Tab State
    private val _cleanupCards = MutableStateFlow<List<CleanupCardState>>(emptyList())
    val cleanupCards = _cleanupCards.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning = _isCleaning.asStateFlow()

    private val _activeSearchQuery = MutableStateFlow("")
    val activeSearchQuery = _activeSearchQuery.asStateFlow()

    // Category Screen State
    private val _currentCategory = MutableStateFlow<FileType?>(null)
    val currentCategory = _currentCategory.asStateFlow()

    private val _categoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val categoryFiles = _categoryFiles.asStateFlow()

    // Folder Navigator State
    private val _currentFolder = MutableStateFlow<String?>(null) // null = Root (DemoFiles index)
    val currentFolder = _currentFolder.asStateFlow()

    private val _folderContents = MutableStateFlow<List<FileItem>>(emptyList())
    val folderContents = _folderContents.asStateFlow()

    private val _folderNavigationStack = MutableStateFlow<List<String>>(emptyList())
    val folderNavigationStack = _folderNavigationStack.asStateFlow()

    // Safe Folder States
    private val _hasSafeFolderPasscode = MutableStateFlow(false)
    val hasSafeFolderPasscode = _hasSafeFolderPasscode.asStateFlow()

    private val _isSafeFolderUnlocked = MutableStateFlow(false)
    val isSafeFolderUnlocked = _isSafeFolderUnlocked.asStateFlow()

    private val _safeFolderFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val safeFolderFiles = _safeFolderFiles.asStateFlow()

    // Offline Share States
    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState = _shareState.asStateFlow()

    private val _isSendingRole = MutableStateFlow(true) // true = Sender, false = Receiver
    val isSendingRole = _isSendingRole.asStateFlow()

    // Favorites stream directly from DB
    val favoriteFiles: StateFlow<List<FileItem>> = repository.getFavoritesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStorageAndCleanups()
        checkSafeFolderHasKey()
        observeSafeFilesFlow()
    }

    fun selectTab(tabIndex: Int) {
        _currentTab.value = tabIndex
        if (tabIndex == 1) {
            // Empty view resets
            _currentCategory.value = null
            _currentFolder.value = null
            _folderNavigationStack.value = emptyList()
        }
    }

    fun refreshStorageAndCleanups() {
        viewModelScope.launch {
            _storageInfo.value = repository.getStorageInfo()
            _cleanupCards.value = repository.getCleanupRecommendations()
        }
    }

    // CLEAN Actions
    fun cleanJunkFiles(sizeToFree: Long) {
        viewModelScope.launch {
            _isCleaning.value = true
            // Play sweeping visual loading delay
            delay(2200)
            _isCleaning.value = false
            
            // Re-read storage
            refreshStorageAndCleanups()
        }
    }

    // NAVIGATION inside Category view
    fun viewCategory(category: FileType?) {
        _currentCategory.value = category
        if (category != null) {
            viewModelScope.launch {
                val files = repository.getFilesByCategory(category)
                _categoryFiles.value = files
            }
        }
    }

    // INTERNAL STORAGE EXPLORER
    fun exploreInternalStorage() {
        _currentFolder.value = null // Begins at Root sandbox
        _folderNavigationStack.value = emptyList()
        loadFolderContents(null)
    }

    fun navigateIntoFolder(folderPath: String) {
        val currentStack = _folderNavigationStack.value.toMutableList()
        _currentFolder.value?.let { currentStack.add(it) }
        _folderNavigationStack.value = currentStack

        _currentFolder.value = folderPath
        loadFolderContents(folderPath)
    }

    fun navigateUpFolder(): Boolean {
        val stack = _folderNavigationStack.value.toMutableList()
        if (stack.isNotEmpty()) {
            val parentPath = stack.removeAt(stack.size - 1)
            _folderNavigationStack.value = stack
            _currentFolder.value = parentPath
            loadFolderContents(parentPath)
            return true
        } else if (_currentFolder.value != null) {
            _currentFolder.value = null
            _folderNavigationStack.value = emptyList()
            loadFolderContents(null)
            return true
        }
        return false
    }

    private fun loadFolderContents(path: String?) {
        viewModelScope.launch {
            val list = repository.getDirectoryContents(path)
            _folderContents.value = list
        }
    }

    // FILE MUTATIONS (Rename, Delete, Favorite)
    fun toggleFavorite(file: FileItem) {
        viewModelScope.launch {
            repository.toggleFavorite(file)
            // Refresh lists
            refreshActiveScreenData()
        }
    }

    fun deleteFile(file: FileItem) {
        viewModelScope.launch {
            repository.deleteFile(file)
            refreshActiveScreenData()
            refreshStorageAndCleanups()
        }
    }

    fun renameFile(file: FileItem, newName: String) {
        viewModelScope.launch {
            repository.renameFile(file, newName)
            refreshActiveScreenData()
        }
    }

    private fun refreshActiveScreenData() {
        viewModelScope.launch {
            _currentCategory.value?.let { viewCategory(it) }
            loadFolderContents(_currentFolder.value)
        }
    }

    // SAFE FOLDER SECURITY
    fun checkSafeFolderHasKey() {
        viewModelScope.launch {
            _hasSafeFolderPasscode.value = repository.hasPasscode()
        }
    }

    fun setupSafePasscode(pin: String) {
        viewModelScope.launch {
            val success = repository.setPasscode(pin)
            if (success) {
                _hasSafeFolderPasscode.value = true
                _isSafeFolderUnlocked.value = true
            }
        }
    }

    fun unlockSafeFolder(pin: String): Boolean {
        var valid = false
        viewModelScope.launch {
            valid = repository.verifyPasscode(pin)
            if (valid) {
                _isSafeFolderUnlocked.value = true
            }
        }
        return valid
    }

    fun lockSafeFolder() {
        _isSafeFolderUnlocked.value = false
    }

    private fun observeSafeFilesFlow() {
        viewModelScope.launch {
            repository.getSafeFilesFlow().collect { files ->
                _safeFolderFiles.value = files
            }
        }
    }

    fun moveFileToSafe(file: FileItem) {
        viewModelScope.launch {
            val success = repository.moveToSafeFolder(file)
            if (success) {
                refreshActiveScreenData()
                refreshStorageAndCleanups()
            }
        }
    }

    fun restoreFileFromSafe(file: FileItem) {
        viewModelScope.launch {
            val success = repository.restoreFileFromSafeFolder(file.path)
            if (success) {
                refreshActiveScreenData()
                refreshStorageAndCleanups()
            }
        }
    }

    // SHARE OFF-LINE LOGIC SIMULATION
    fun initDiscovering(asSender: Boolean) {
        _isSendingRole.value = asSender
        _shareState.value = ShareState.Discovering
        
        viewModelScope.launch {
            delay(3000) // 3 seconds scan pulse animation visual
            
            // Discover nearby peer
            val peerName = if (asSender) "Pixel 8 Pro" else "Galaxy S24 Ultra"
            val mockFileName = if (asSender) "weekly_vivid_photo.jpg (4.2 MB)" else "received_work_notes.pdf (1.6 MB)"
            
            _shareState.value = ShareState.Connected(
                deviceName = peerName,
                transferProgress = 0f,
                fileName = mockFileName,
                speedMbps = 24.5f
            )

            // High speed progress fill simulation
            var pct = 0f
            while (pct < 100f) {
                delay(300)
                pct += 15f + (Math.random() * 10).toFloat()
                if (pct > 100f) pct = 100f
                _shareState.value = ShareState.Connected(
                    deviceName = peerName,
                    transferProgress = pct / 100f,
                    fileName = mockFileName,
                    speedMbps = 28f + (Math.random() * 10).toFloat()
                )
            }
            
            delay(1000)
            _shareState.value = ShareState.Completed
        }
    }

    fun cancelShareMode() {
        _shareState.value = ShareState.Idle
    }
}

// ViewModel Factory
class FilesViewModelFactory(private val repository: FileManagerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FilesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FilesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
