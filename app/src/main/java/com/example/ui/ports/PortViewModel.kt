package com.example.ui.ports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubApiService
import com.example.data.PortRepository
import com.example.model.Port
import com.example.model.Review
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface PortsUiState {
    data object Loading : PortsUiState
    data class Success(val ports: List<Port>) : PortsUiState
    data class Error(val message: String) : PortsUiState
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val portId: String,
        val portName: String,
        val isRom: Boolean = false,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState
    data class Installing(val portId: String, val portName: String) : DownloadState
    data class Completed(
        val portId: String,
        val portName: String,
        val isRom: Boolean = false,
        val folderPath: String = ""
    ) : DownloadState
    data class Error(val portId: String, val message: String) : DownloadState
}

class PortViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PortRepository(application)

    val uiState: StateFlow<PortsUiState> = repository.observePorts()
        .map<List<Port>, PortsUiState> { ports ->
            PortsUiState.Success(ports)
        }
        .catch { e ->
            emit(PortsUiState.Error(e.localizedMessage ?: "Failed to load ports"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PortsUiState.Loading
        )

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun dismissDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun addPort(port: Port) {
        viewModelScope.launch {
            try {
                repository.addPort(port)
                _toastMessage.emit("Port added successfully")
            } catch (e: Exception) {
                _toastMessage.emit("Error adding port: ${e.localizedMessage}")
            }
        }
    }

    fun updatePort(port: Port) {
        viewModelScope.launch {
            try {
                repository.updatePort(port)
                _toastMessage.emit("Port updated successfully")
            } catch (e: Exception) {
                _toastMessage.emit("Error updating port: ${e.localizedMessage}")
            }
        }
    }

    fun deletePort(portId: String) {
        viewModelScope.launch {
            try {
                repository.deletePort(portId)
                _toastMessage.emit("Port deleted")
            } catch (e: Exception) {
                _toastMessage.emit("Error deleting port: ${e.localizedMessage}")
            }
        }
    }

    fun observeReviews(portId: String): Flow<List<Review>> {
        return repository.observeReviews(portId)
    }

    fun addReview(portId: String, review: Review) {
        viewModelScope.launch {
            try {
                repository.addReview(portId, review)
                _toastMessage.emit("Review added successfully")
            } catch (e: Exception) {
                _toastMessage.emit("Error adding review: ${e.localizedMessage}")
            }
        }
    }

    private fun parseGitHubOwnerRepo(input: String): Pair<String, String>? {
        val clean = input.trim()
        if (clean.isBlank()) return null
        val regex = Regex("""(?:https?://)?(?:www\.)?github\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)""")
        val match = regex.find(clean)
        if (match != null) {
            val owner = match.groupValues[1]
            val repo = match.groupValues[2].removeSuffix(".git")
            return Pair(owner, repo)
        }
        val parts = clean.removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .trim('/')
            .split('/')
        if (parts.size >= 2) {
            val owner = parts[0]
            val repo = parts[1].removeSuffix(".git").substringBefore('?').substringBefore('#')
            if (owner.isNotBlank() && repo.isNotBlank()) {
                return Pair(owner, repo)
            }
        }
        return null
    }

    fun checkForUpdates(ports: List<Port>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isCheckingUpdates.value = true
            var updatedCount = 0
            for (port in ports) {
                val parsed = parseGitHubOwnerRepo(port.repoUrl)
                if (parsed != null) {
                    val (owner, repo) = parsed
                    try {
                        val resolved = com.example.data.GitHubReleaseResolver.resolveLatestApk(owner, repo)
                        val latestTag = resolved.tagName
                        val apkUrl = resolved.apkUrl ?: port.apkDownloadUrl
                        if (latestTag.isNotBlank()) {
                            val hasUpdate = latestTag != port.installedVersion
                            val updatedPort = port.copy(
                                latestVersion = latestTag,
                                hasUpdate = hasUpdate,
                                apkDownloadUrl = apkUrl
                            )
                            repository.updatePort(updatedPort)
                            if (hasUpdate) updatedCount++
                        }
                    } catch (e: Exception) {
                        // Ignore API failures per repo
                    }
                }
            }
            _isCheckingUpdates.value = false
            _toastMessage.emit("Checked updates. Found $updatedCount update(s).")
        }
    }

    fun downloadAndInstallPort(port: Port) {
        viewModelScope.launch {
            var apkUrl = port.apkDownloadUrl
            var latestTag = port.latestVersion

            _downloadState.value = DownloadState.Downloading(
                portId = port.id,
                portName = port.name,
                isRom = false,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = 0
            )

            // Resolve APK URL through GitHubReleaseResolver (REST API, all-releases fallback, web scraping, and URL candidates)
            val parsed = parseGitHubOwnerRepo(port.repoUrl)
            if (parsed != null) {
                val (owner, repo) = parsed
                try {
                    val resolved = com.example.data.GitHubReleaseResolver.resolveLatestApk(owner, repo)
                    if (!resolved.apkUrl.isNullOrBlank()) {
                        apkUrl = resolved.apkUrl
                    }
                    if (!resolved.tagName.isNullOrBlank()) {
                        latestTag = resolved.tagName
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            if (apkUrl.isBlank()) {
                _downloadState.value = DownloadState.Error(
                    port.id,
                    "No se encontró un archivo .APK en los releases de este repositorio. Puedes ingresar la URL directa del APK editando el port."
                )
                return@launch
            }

            com.example.util.ApkDownloader.downloadApk(
                context = getApplication(),
                port = port,
                url = apkUrl,
                onProgress = { progress, downloaded, total ->
                    _downloadState.value = DownloadState.Downloading(
                        portId = port.id,
                        portName = port.name,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                },
                onComplete = { apkFile, detectedPackageName ->
                    _downloadState.value = DownloadState.Installing(port.id, port.name)

                    // Auto-update Firestore with detected package name and version
                    val updatedPort = port.copy(
                        packageName = if (!detectedPackageName.isNullOrBlank()) detectedPackageName else port.packageName,
                        installedVersion = if (latestTag.isNotBlank()) latestTag else port.installedVersion,
                        hasUpdate = false,
                        apkDownloadUrl = apkUrl
                    )
                    updatePort(updatedPort)

                    // Trigger Android system installer
                    com.example.util.ApkDownloader.installApk(getApplication(), apkFile)
                    _downloadState.value = DownloadState.Completed(port.id, port.name)
                },
                onError = { errorMsg ->
                    _downloadState.value = DownloadState.Error(port.id, errorMsg)
                }
            )
        }
    }

    fun downloadRom(port: Port, baseUri: android.net.Uri) {
        if (port.romUrl.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("No hay URL de ROM configurada para este port.")
            }
            return
        }

        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(
                portId = port.id,
                portName = port.realGameTitle.ifBlank { port.name },
                isRom = true,
                progress = 0f,
                downloadedBytes = 0,
                totalBytes = 0
            )

            com.example.util.RomStorageManager.downloadRom(
                context = getApplication(),
                baseUri = baseUri,
                port = port,
                url = port.romUrl,
                onProgress = { progress, downloaded, total ->
                    _downloadState.value = DownloadState.Downloading(
                        portId = port.id,
                        portName = port.realGameTitle.ifBlank { port.name },
                        isRom = true,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                },
                onComplete = { folderName, fileName ->
                    _downloadState.value = DownloadState.Completed(
                        portId = port.id,
                        portName = port.realGameTitle.ifBlank { port.name },
                        isRom = true,
                        folderPath = "$folderName/$fileName"
                    )
                    viewModelScope.launch {
                        _toastMessage.emit("ROM guardada con éxito en: $folderName/$fileName")
                    }
                },
                onError = { errorMsg ->
                    _downloadState.value = DownloadState.Error(port.id, errorMsg)
                }
            )
        }
    }
}
