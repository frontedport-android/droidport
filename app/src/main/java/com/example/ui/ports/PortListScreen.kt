package com.example.ui.ports

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.Port
import com.example.ui.components.GamerButton
import com.example.util.AdminConfig
import com.example.util.AppLauncher
import com.example.util.PortShareHelper
import com.example.util.RomStorageManager
import com.google.firebase.auth.FirebaseUser
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortListScreen(
    viewModel: PortViewModel,
    currentUser: FirebaseUser? = null,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val uiState by viewModel.uiState.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPort by remember { mutableStateOf<Port?>(null) }
    var reviewingPort by remember { mutableStateOf<Port?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All Ports, 1 = Descargados
    var hasCheckedUpdatesOnStart by remember { mutableStateOf(false) }

    val isAdmin = AdminConfig.isAdmin(currentUser?.email)

    var pendingRomPort by remember { mutableStateOf<Port?>(null) }
    var showRomPromptForPort by remember { mutableStateOf<Port?>(null) }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            RomStorageManager.saveRomDirectoryUri(context, uri)
            val p = pendingRomPort
            if (p != null) {
                viewModel.downloadRom(p, uri)
                pendingRomPort = null
            }
        } else {
            Toast.makeText(context, "No se seleccionó ninguna carpeta para las ROMs.", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerRomDownload(port: Port) {
        if (port.romUrl.isBlank()) {
            Toast.makeText(context, "No hay URL de ROM configurada para este juego.", Toast.LENGTH_SHORT).show()
            return
        }
        val savedUri = RomStorageManager.getSavedRomDirectoryUri(context)
        if (savedUri != null) {
            viewModel.downloadRom(port, savedUri)
        } else {
            pendingRomPort = port
            Toast.makeText(context, "Selecciona la carpeta donde guardarás tus ROMs", Toast.LENGTH_LONG).show()
            openDocumentTreeLauncher.launch(null)
        }
    }

    var installedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apps = AppLauncher.getInstalledApps(context)
            installedPackageNames = apps.map { it.packageName.lowercase() }.toSet()
        }
    }

    val downloadState by viewModel.downloadState.collectAsState()

    LaunchedEffect(downloadState) {
        val state = downloadState
        if (state is DownloadState.Completed) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val apps = AppLauncher.getInstalledApps(context)
                installedPackageNames = apps.map { it.packageName.lowercase() }.toSet()
            }
            if (!state.isRom) {
                val ports = (uiState as? PortsUiState.Success)?.ports.orEmpty()
                val targetPort = ports.find { it.id == state.portId } ?: ports.find { it.name == state.portName }
                if (targetPort != null && targetPort.romUrl.isNotBlank()) {
                    showRomPromptForPort = targetPort
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Auto-check for updates 1.5s after app opens so initial launch UI is 100% instant and responsive
    LaunchedEffect(uiState) {
        if (!hasCheckedUpdatesOnStart && uiState is PortsUiState.Success) {
            val ports = (uiState as PortsUiState.Success).ports
            if (ports.isNotEmpty()) {
                hasCheckedUpdatesOnStart = true
                kotlinx.coroutines.delay(1500L)
                viewModel.checkForUpdates(ports)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("DROIDPORT", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (uiState is PortsUiState.Success) {
                                viewModel.checkForUpdates((uiState as PortsUiState.Success).ports)
                            }
                        },
                        enabled = !isCheckingUpdates
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check Updates", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (isAdmin) {
                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFFB300)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                                Text("ADMIN", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    IconButton(
                        onClick = { showSignOutDialog = true }
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Cuenta de usuario: ${currentUser?.email.orEmpty()}",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Port", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = if (isLandscape) 20.dp else 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Adaptive Search and Tab Row: In Landscape they sit side-by-side to save vertical space!
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(context.getString(R.string.search_ports)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(320.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(context.getString(R.string.tab_all_ports), fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(context.getString(R.string.tab_updates), fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            } else {
                // Portrait Search Bar & Tabs stacked vertically
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(context.getString(R.string.search_ports)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(context.getString(R.string.tab_all_ports), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(context.getString(R.string.tab_updates), fontWeight = FontWeight.Bold) }
                    )
                }
            }

            when (val state = uiState) {
                is PortsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is PortsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is PortsUiState.Success -> {
                    val filteredPorts = state.ports.filter { port ->
                        val matchesQuery = port.name.contains(searchQuery, ignoreCase = true) ||
                                port.realGameTitle.contains(searchQuery, ignoreCase = true) ||
                                port.packageName.contains(searchQuery, ignoreCase = true) ||
                                port.repoUrl.contains(searchQuery, ignoreCase = true)

                        val isInstalledOnDevice = port.packageName.isNotBlank() &&
                                (installedPackageNames.contains(port.packageName.lowercase()) || AppLauncher.isAppInstalled(context, port.packageName))

                        val matchesTab = if (selectedTab == 0) {
                            !isInstalledOnDevice
                        } else {
                            isInstalledOnDevice
                        }

                        matchesQuery && matchesTab
                    }

                    if (filteredPorts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (selectedTab == 0) "NO HAY PORTS DISPONIBLES PARA INSTALAR" else "NO TIENES JUEGOS DESCARGADOS TODAVÍA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                GamerButton(
                                    text = context.getString(R.string.add_new_port),
                                    onClick = { showAddDialog = true }
                                )
                            }
                        }
                    } else {
                        // In portrait: 2 games side-by-side. In landscape: 4 games side-by-side.
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (isLandscape) 4 else 2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(filteredPorts, key = { it.id }) { port ->
                                PortCard(
                                    port = port,
                                    viewModel = viewModel,
                                    currentUser = currentUser,
                                    onLaunch = { AppLauncher.launchApp(context, port.packageName) },
                                    onSettings = { AppLauncher.openAppSettings(context, port.packageName) },
                                    onEdit = { editingPort = port },
                                    onDelete = { viewModel.deletePort(port) },
                                    onOpenReviews = { reviewingPort = port },
                                    onDownloadRom = { triggerRomDownload(port) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(context.getString(R.string.sign_out), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.confirm_sign_out))
                    if (!currentUser?.email.isNullOrBlank()) {
                        Text(
                            text = currentUser.email!!,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text(context.getString(R.string.sign_out), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showAddDialog) {
        AddEditPortDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newPort -> viewModel.addPort(newPort) }
        )
    }

    if (editingPort != null) {
        AddEditPortDialog(
            portToEdit = editingPort,
            onDismiss = { editingPort = null },
            onSave = { updated -> viewModel.updatePort(updated) }
        )
    }

    if (reviewingPort != null) {
        PortReviewsDialog(
            port = reviewingPort!!,
            viewModel = viewModel,
            onDismiss = { reviewingPort = null }
        )
    }

    if (showRomPromptForPort != null) {
        val promptPort = showRomPromptForPort!!
        AlertDialog(
            onDismissRequest = { showRomPromptForPort = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¿Descargar ROM / Datos?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Text(
                    text = "El APK de ${promptPort.name} se ha descargado correctamente.\n\n¿Deseas descargar los archivos ROM / datos de (${promptPort.realGameTitle.ifBlank { promptPort.name }}) ahora?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = promptPort
                        showRomPromptForPort = null
                        triggerRomDownload(p)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Sí, descargar ROM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRomPromptForPort = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ahora no")
                }
            }
        )
    }

    if (downloadState !is DownloadState.Idle) {
        AlertDialog(
            onDismissRequest = {
                if (downloadState !is DownloadState.Downloading) {
                    viewModel.dismissDownloadState()
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        when (downloadState) {
                            is DownloadState.Downloading -> context.getString(R.string.downloading_apk)
                            is DownloadState.Installing -> context.getString(R.string.preparing_installer)
                            is DownloadState.Completed -> context.getString(R.string.download_complete)
                            is DownloadState.Error -> "Error de Descarga"
                            else -> "APK Manager"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (val state = downloadState) {
                        is DownloadState.Downloading -> {
                            Text(
                                text = state.portName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val percent = (state.progress * 100).toInt()
                                val downloadedMb = state.downloadedBytes.toFloat() / (1024 * 1024)
                                val totalMb = state.totalBytes.toFloat() / (1024 * 1024)
                                Text(
                                    text = "$percent%",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.totalBytes > 0) {
                                    Text(
                                        text = String.format("%.1f MB / %.1f MB", downloadedMb, totalMb),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = String.format("%.1f MB", downloadedMb),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is DownloadState.Installing -> {
                            Text(
                                text = context.getString(R.string.preparing_installer),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        is DownloadState.Completed -> {
                            Text(
                                text = context.getString(R.string.download_complete),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is DownloadState.Error -> {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (downloadState !is DownloadState.Downloading) {
                    TextButton(onClick = { viewModel.dismissDownloadState() }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            }
        )
    }
}

/**
 * Minimalist, professional game box art card for handhelds and consoles.
 * Features a dominant cover art poster, dual project + real game title display,
 * rating pill, and clean actionable install/launch buttons.
 */
@Composable
fun PortCard(
    port: Port,
    viewModel: PortViewModel,
    currentUser: FirebaseUser? = null,
    onLaunch: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenReviews: () -> Unit,
    onDownloadRom: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isAdmin = AdminConfig.isAdmin(currentUser?.email)
    val isOwner = port.userId.isBlank() || port.userId == currentUser?.uid
    val canEditOrDelete = isAdmin || isOwner

    val isInstalled by androidx.compose.runtime.produceState(initialValue = false, key1 = port.packageName) {
        if (port.packageName.isNotBlank()) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppLauncher.isAppInstalled(context, port.packageName)
            }
        } else {
            value = false
        }
    }

    val isUpdateAvailable = remember(port.hasUpdate, port.latestVersion, port.installedVersion) {
        port.hasUpdate || (
            port.latestVersion.isNotBlank() &&
            port.installedVersion.isNotBlank() &&
            !port.latestVersion.equals(port.installedVersion, ignoreCase = true)
        )
    }

    val reviews by viewModel.observeReviews(port.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val calculatedRating = remember(reviews, port.id) {
        if (reviews.isNotEmpty()) {
            reviews.map { it.rating }.average()
        } else {
            0.0
        }
    }
    val ratingText = if (reviews.isEmpty()) {
        "0"
    } else if (calculatedRating % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", calculatedRating)
    } else {
        String.format(Locale.US, "%.1f", calculatedRating)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(275.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .border(
                width = if (port.hasUpdate) 2.dp else 1.dp,
                color = if (port.hasUpdate) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // DOMINANT COVER ART POSTER (occupies the top 175.dp of the card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (port.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = port.iconUrl,
                        contentDescription = port.realGameTitle.ifBlank { port.name },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Minimalist geometric gamer placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = port.name.take(12),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Sleek vertical dark scrim gradient at the bottom of the cover art
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Top-Left GitHub Button (tapping takes user to GitHub repo)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            val raw = port.repoUrl.trim()
                            if (raw.isNotBlank()) {
                                val fullUrl = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                                    raw
                                } else {
                                    "https://github.com/${raw.removePrefix("/")}"
                                }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open GitHub: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No GitHub repository linked", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "GitHub Repository",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Top-Right Glassmorphic Action Buttons: Info ("i") and 3-Dot Menu
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "i" info icon button to open app details / settings
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.70f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "App Info & Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 3-Dot Menu Button for Edit and Delete
                    Box {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { expandedMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.70f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.share), fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    expandedMenu = false
                                    PortShareHelper.sharePort(context, port)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = "Compartir") },
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                            if (canEditOrDelete) {
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.edit), fontWeight = FontWeight.SemiBold) },
                                    onClick = { expandedMenu = false; onEdit() },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                                    onClick = { expandedMenu = false; onDelete() },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom-Left Rating Pill on Cover Art (displays numeric rating score)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenReviews)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating $ratingText",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = ratingText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Bottom-Right Private Badge (shown if port is private to current user)
                if (!port.isAdminPort && port.userId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Port privado",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = context.getString(R.string.private_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // BOTTOM INFO & ACTION BUTTONS (compact vertical arrangement to eliminate empty gap)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Dual Title: Project Name + Real Game Name in parentheses prominently
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = port.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (port.realGameTitle.isNotBlank() && !port.realGameTitle.equals(port.name, ignoreCase = true)) {
                        Text(
                            text = "(${port.realGameTitle})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUpdateAvailable) {
                        Button(
                            onClick = { viewModel.downloadAndInstallPort(port) },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = context.getString(R.string.update_apk),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isInstalled) {
                            GamerButton(
                                text = context.getString(R.string.btn_launch),
                                onClick = onLaunch,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(38.dp),
                                isPrimary = true
                            )
                        }
                    } else if (!isInstalled) {
                        GamerButton(
                            text = context.getString(R.string.btn_install),
                            onClick = { viewModel.downloadAndInstallPort(port) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            isPrimary = false
                        )
                    } else {
                        GamerButton(
                            text = context.getString(R.string.btn_launch),
                            onClick = onLaunch,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            isPrimary = true
                        )
                    }

                    // Small "ROM" button beside main action button if romUrl exists
                    if (port.romUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = onDownloadRom,
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = "ROM", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("ROM", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
