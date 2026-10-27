package com.example.ui.ports

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.GitHubApiService
import com.example.data.WebImageSearchService
import com.example.model.Port
import com.example.ui.components.GamerButton
import com.example.util.AppLauncher
import com.example.util.GameTitleDetector
import com.example.util.ImageOptimizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPortDialog(
    portToEdit: Port? = null,
    onDismiss: () -> Unit,
    onSave: (Port) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var name by remember { mutableStateOf(portToEdit?.name ?: "") }
    var realGameTitle by remember { mutableStateOf(portToEdit?.realGameTitle ?: "") }
    var repoUrl by remember { mutableStateOf(portToEdit?.repoUrl ?: "") }
    var packageName by remember { mutableStateOf(portToEdit?.packageName ?: "") }
    var installedVersion by remember { mutableStateOf(portToEdit?.installedVersion ?: "v1.0.0") }
    var iconUrl by remember { mutableStateOf(portToEdit?.iconUrl ?: "") }
    var notes by remember { mutableStateOf(portToEdit?.notes ?: "") }
    var apkDownloadUrl by remember { mutableStateOf(portToEdit?.apkDownloadUrl ?: "") }
    var romUrl by remember { mutableStateOf(portToEdit?.romUrl ?: "") }
    var apkFoundMessage by remember { mutableStateOf<String?>(null) }

    var showError by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var showIconPicker by remember { mutableStateOf(false) }
    var isAutocompleting by remember { mutableStateOf(false) }
    var iconSearchQuery by remember { mutableStateOf("") }

    var isSearchingWebIcons by remember { mutableStateOf(false) }
    var isLoadingMoreWebIcons by remember { mutableStateOf(false) }
    var webIconCurrentPage by remember { mutableIntStateOf(0) }
    var hasMoreWebIcons by remember { mutableStateOf(true) }
    var webIconResults by remember { mutableStateOf<List<String>>(emptyList()) }
    val gridState = rememberLazyGridState()

    var installedApps by remember { mutableStateOf<List<com.example.util.InstalledAppInfo>>(emptyList()) }
    var isLoadingInstalledApps by remember { mutableStateOf(false) }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker && installedApps.isEmpty()) {
            isLoadingInstalledApps = true
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppLauncher.getInstalledApps(context)
            }
            installedApps = apps
            isLoadingInstalledApps = false
        }
    }

    val filteredInstalledApps = remember(installedApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.appName.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
        }
    }

    val activeCoverQuery = remember(iconSearchQuery, realGameTitle, name) {
        when {
            iconSearchQuery.isNotBlank() -> iconSearchQuery.trim()
            realGameTitle.isNotBlank() -> realGameTitle.trim()
            name.isNotBlank() -> name.trim()
            else -> "video game"
        }
    }

    // Trigger initial search when dialog opens
    LaunchedEffect(showIconPicker) {
        if (showIconPicker) {
            val initialQuery = when {
                iconSearchQuery.isNotBlank() -> iconSearchQuery.trim()
                realGameTitle.isNotBlank() -> realGameTitle.trim()
                name.isNotBlank() -> name.trim()
                else -> ""
            }
            if (iconSearchQuery.isBlank()) {
                iconSearchQuery = initialQuery
            }
            isSearchingWebIcons = true
            webIconCurrentPage = 0
            hasMoreWebIcons = true
            val results = WebImageSearchService.searchGameImages(initialQuery.ifBlank { "video game cover" }, page = 0)
            webIconResults = results
            isSearchingWebIcons = false
            hasMoreWebIcons = results.isNotEmpty()
        }
    }

    fun loadMoreImages() {
        if (isLoadingMoreWebIcons || isSearchingWebIcons || !hasMoreWebIcons) return
        val currentQuery = if (iconSearchQuery.isNotBlank()) iconSearchQuery.trim() else activeCoverQuery
        coroutineScope.launch {
            isLoadingMoreWebIcons = true
            val nextPage = webIconCurrentPage + 1
            val newResults = WebImageSearchService.searchGameImages(currentQuery, page = nextPage)
            if (newResults.isNotEmpty()) {
                val existing = webIconResults.toSet()
                val fresh = newResults.filter { it !in existing }
                if (fresh.isNotEmpty()) {
                    webIconResults = webIconResults + fresh
                    webIconCurrentPage = nextPage
                } else {
                    hasMoreWebIcons = false
                }
            } else {
                hasMoreWebIcons = false
            }
            isLoadingMoreWebIcons = false
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMoreWebIcons && !isLoadingMoreWebIcons && !isSearchingWebIcons) {
            loadMoreImages()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val compressedDataUri = ImageOptimizer.compressImageToDataUri(context, uri, maxSize = 400, quality = 85)
            if (compressedDataUri != null) {
                iconUrl = compressedDataUri
                Toast.makeText(context, "Cover image selected and optimized!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun parseGitHubOwnerRepo(input: String): Pair<String, String>? {
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

    fun autocompleteFromGitHub() {
        val rawInput = repoUrl.trim()
        if (rawInput.isBlank()) {
            Toast.makeText(context, "Enter a GitHub repo first (owner/repo)", Toast.LENGTH_SHORT).show()
            return
        }

        val parsed = parseGitHubOwnerRepo(rawInput)
        if (parsed == null) {
            Toast.makeText(context, "Invalid GitHub URL or format. Use owner/repo", Toast.LENGTH_SHORT).show()
            return
        }

        val (owner, repo) = parsed

        isAutocompleting = true
        coroutineScope.launch {
            try {
                val repoInfo = GitHubApiService.api.getRepoInfo(owner, repo)
                val resolvedRelease = try {
                    com.example.data.GitHubReleaseResolver.resolveLatestApk(owner, repo)
                } catch (e: Exception) {
                    null
                }

                // Keep project name
                val repoName = repoInfo.name ?: repo
                if (name.isBlank()) {
                    name = repoName
                }

                // Detect real commercial video game title from repo name, description, and release
                val detectedGame = GameTitleDetector.detectRealTitle(
                    repoOrProjectName = repoName,
                    description = repoInfo.description ?: "",
                    releaseTitle = resolvedRelease?.tagName ?: ""
                )
                realGameTitle = detectedGame

                if (!resolvedRelease?.tagName.isNullOrBlank()) {
                    installedVersion = resolvedRelease!!.tagName
                }

                if (!resolvedRelease?.apkUrl.isNullOrBlank()) {
                    apkDownloadUrl = resolvedRelease!!.apkUrl!!
                    apkFoundMessage = "APK listo para descarga dentro de la app"
                }

                if (notes.isBlank() && !repoInfo.description.isNullOrBlank()) {
                    notes = repoInfo.description!!
                }

                if (packageName.isBlank()) {
                    val cleanOwner = owner.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val cleanRepoName = repo.lowercase().replace(Regex("[^a-z0-9]"), "")
                    packageName = "com.$cleanOwner.$cleanRepoName"
                }

                // Auto-fetch high quality box art using the detected real game title!
                val candidateImages = WebImageSearchService.searchGameImages(detectedGame)
                if (candidateImages.isNotEmpty()) {
                    iconUrl = candidateImages.first()
                } else if (!repoInfo.owner?.avatarUrl.isNullOrBlank()) {
                    iconUrl = repoInfo.owner!!.avatarUrl!!
                }

                repoUrl = "$owner/$repo"
                Toast.makeText(context, "Auto-completed for $detectedGame!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "GitHub fetch error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isAutocompleting = false
            }
        }
    }

    // App Picker Dialog (User Installed Apps Only)
    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text(context.getString(R.string.select_installed_app), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        label = { Text(context.getString(R.string.search_installed_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        if (isLoadingInstalledApps) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (filteredInstalledApps.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No installed user apps match query.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filteredInstalledApps.forEach { app ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                packageName = app.packageName
                                                if (name.isBlank()) name = app.appName
                                                if (realGameTitle.isBlank()) realGameTitle = GameTitleDetector.detectRealTitle(app.appName)
                                                showAppPicker = false
                                            },
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            AsyncImage(
                                                model = app.icon,
                                                contentDescription = app.appName,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(app.appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text(context.getString(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Web Cover Art / Box Art Picker Dialog
    if (showIconPicker) {
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Portadas e Iconos del Juego", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Formato cuadrado oficial (1:1)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 340.dp else 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = iconSearchQuery,
                            onValueChange = { iconSearchQuery = it },
                            label = { Text("Nombre del juego (ej. Zelda, Diablo)...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                coroutineScope.launch {
                                    isSearchingWebIcons = true
                                    webIconCurrentPage = 0
                                    hasMoreWebIcons = true
                                    val q = iconSearchQuery.ifBlank { activeCoverQuery }
                                    webIconResults = WebImageSearchService.searchGameImages(q, page = 0)
                                    isSearchingWebIcons = false
                                    hasMoreWebIcons = webIconResults.isNotEmpty()
                                }
                            }),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isSearchingWebIcons = true
                                webIconCurrentPage = 0
                                hasMoreWebIcons = true
                                val q = iconSearchQuery.ifBlank { activeCoverQuery }
                                webIconResults = WebImageSearchService.searchGameImages(q, page = 0)
                                isSearchingWebIcons = false
                                hasMoreWebIcons = webIconResults.isNotEmpty()
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (isSearchingWebIcons) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text("Buscando portadas e iconos cuadrados...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else if (webIconResults.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No se encontraron portadas. Prueba con otro título.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (isLandscape) 4 else 2),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(webIconResults, key = { it }) { url ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                iconUrl = url
                                                showIconPicker = false
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = url,
                                            contentDescription = "Cover Option",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            loading = {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            error = {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                }
                                            }
                                        )
                                    }
                                }

                                if (isLoadingMoreWebIcons) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                                Text("Cargando más portadas...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                } else if (hasMoreWebIcons && webIconResults.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        OutlinedButton(
                                            onClick = { loadMoreImages() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Cargar más resultados")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconPicker = false }) {
                    Text(context.getString(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // MAIN ADD / EDIT DIALOG (Fully Optimized for Landscape & Portrait)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.92f else 0.95f)
                .fillMaxHeight(if (isLandscape) 0.94f else 0.90f)
                .shadow(20.dp, RoundedCornerShape(24.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (portToEdit == null) context.getString(R.string.add_game_port) else context.getString(R.string.edit_game_port),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Responsive Body: 2 Columns in Landscape, 1 Column in Portrait
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Column: Repository, Project Name, Real Game Name, Package Name
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // GitHub Repo with Magic Auto-Complete Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = repoUrl,
                                    onValueChange = { repoUrl = it },
                                    label = { Text("GitHub Repo (owner/repo) *") },
                                    placeholder = { Text("e.g. Zelda-Recomp/las-recomp") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = ::autocompleteFromGitHub,
                                    enabled = !isAutocompleting,
                                    modifier = Modifier.height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    if (isAutocompleting) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Auto-complete", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(context.getString(R.string.autofill))
                                    }
                                }
                            }

                            if (apkFoundMessage != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Text(text = apkFoundMessage!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Port / Project Name (e.g. las-recomp) *") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Real Game Title (Detected or Custom)
                            OutlinedTextField(
                                value = realGameTitle,
                                onValueChange = { realGameTitle = it },
                                label = { Text(context.getString(R.string.real_game_title)) },
                                placeholder = { Text("e.g. The Legend of Zelda: Link's Awakening") },
                                supportingText = { Text("Will appear in parentheses: e.g. $name ($realGameTitle)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = packageName,
                                onValueChange = { packageName = it },
                                label = { Text("Android Package Name (Optional)") },
                                placeholder = { Text("e.g. com.dev.game") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { showAppPicker = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select installed app", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = installedVersion,
                                onValueChange = { installedVersion = it },
                                label = { Text("Installed Version (Optional)") },
                                placeholder = { Text("e.g. v1.0.0") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right Column: Cover Art, Notes & Action Buttons
                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Cover Preview Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    if (iconUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = iconUrl,
                                            contentDescription = "Cover Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Minimalist Cover Art", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(if (iconUrl.isNotBlank()) "Cover loaded" else "No cover set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Text(context.getString(R.string.gallery), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Button(
                                            onClick = {
                                                iconSearchQuery = realGameTitle.ifBlank { name }
                                                showIconPicker = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Text(context.getString(R.string.search_web), fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = iconUrl,
                                onValueChange = { iconUrl = it },
                                label = { Text("Cover / Icon URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = romUrl,
                                onValueChange = { romUrl = it },
                                label = { Text("URL del Archivo ROM / Datos (Opcional)") },
                                placeholder = { Text("https://ejemplo.com/rom.zip") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes / Instructions") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 3
                            )

                            if (showError) {
                                Text(
                                    text = "Please fill all required fields (*).",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(context.getString(R.string.cancel))
                                }
                                GamerButton(
                                    text = context.getString(R.string.save),
                                    onClick = {
                                        if (name.isBlank() || repoUrl.isBlank()) {
                                            showError = true
                                        } else {
                                            val newPort = (portToEdit ?: Port()).copy(
                                                name = name.trim(),
                                                realGameTitle = realGameTitle.trim(),
                                                repoUrl = repoUrl.trim(),
                                                packageName = packageName.trim(),
                                                installedVersion = installedVersion.trim().ifBlank { "v1.0.0" },
                                                iconUrl = iconUrl.trim(),
                                                notes = notes.trim(),
                                                apkDownloadUrl = apkDownloadUrl.trim(),
                                                romUrl = romUrl.trim()
                                            )
                                            onSave(newPort)
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    // Portrait Mode (Vertical scrolling single column)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // GitHub Repo with Magic Auto-Complete Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it },
                                label = { Text("GitHub Repo (owner/repo) *") },
                                placeholder = { Text("e.g. Zelda-Recomp/las-recomp") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = ::autocompleteFromGitHub,
                                enabled = !isAutocompleting,
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (isAutocompleting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondary)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = "Auto-complete")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.autofill))
                                }
                            }
                        }

                        if (apkFoundMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    Text(text = apkFoundMessage!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Port / Project Name (e.g. las-recomp) *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Real Game Title Field
                        OutlinedTextField(
                            value = realGameTitle,
                            onValueChange = { realGameTitle = it },
                            label = { Text(context.getString(R.string.real_game_title)) },
                            placeholder = { Text("e.g. The Legend of Zelda: Link's Awakening") },
                            supportingText = {
                                Text(
                                    text = "Prominently displayed as: $name ($realGameTitle)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Android Package Name (Optional)") },
                            placeholder = { Text("e.g. com.dev.game") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = {
                                IconButton(onClick = { showAppPicker = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select installed app", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = installedVersion,
                            onValueChange = { installedVersion = it },
                            label = { Text("Installed Version (Optional)") },
                            placeholder = { Text("e.g. v1.0.0") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Cover Art & Preview
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = iconUrl,
                                onValueChange = { iconUrl = it },
                                label = { Text("Cover / Box Art URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (iconUrl.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = iconUrl,
                                            contentDescription = "Cover Art Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Column {
                                        Text("Cover Art Preview", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Professional box art ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.gallery), color = MaterialTheme.colorScheme.onSurface)
                                }
                                Button(
                                    onClick = {
                                        // Auto-populate web search query with the real game title!
                                        iconSearchQuery = realGameTitle.ifBlank { name }
                                        showIconPicker = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(context.getString(R.string.search_web), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = romUrl,
                            onValueChange = { romUrl = it },
                            label = { Text("URL del Archivo ROM / Datos (Opcional)") },
                            placeholder = { Text("https://ejemplo.com/rom.zip") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Instructions") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        if (showError) {
                            Text(
                                text = "Please fill all required fields (*).",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(context.getString(R.string.cancel))
                            }
                            GamerButton(
                                text = context.getString(R.string.save),
                                onClick = {
                                    if (name.isBlank() || repoUrl.isBlank()) {
                                        showError = true
                                    } else {
                                        val newPort = (portToEdit ?: Port()).copy(
                                            name = name.trim(),
                                            realGameTitle = realGameTitle.trim(),
                                            repoUrl = repoUrl.trim(),
                                            packageName = packageName.trim(),
                                            installedVersion = installedVersion.trim().ifBlank { "v1.0.0" },
                                            iconUrl = iconUrl.trim(),
                                            notes = notes.trim(),
                                            apkDownloadUrl = apkDownloadUrl.trim(),
                                            romUrl = romUrl.trim()
                                        )
                                        onSave(newPort)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
