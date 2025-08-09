package com.imtiaz.ktimazrev

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
// AndroidX and Jetpack Compose imports
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

// Project-specific imports
import com.imtiaz.ktimazrev.R // For all your string resources like `disassembly_view_tab`
import com.imtiaz.ktimazrev.ui.FileOpen // Assumes FileOpen composable is in ui package
import com.imtiaz.ktimazrev.viewmodel.DisassemblyViewModel // Your custom ViewModel
import com.imtiaz.ktimazrev.viewmodel.FileLoaderViewModel // Your custom ViewModel
import com.imtiaz.ktimazrev.ui.theme.MobileARMDisassemblerTheme // The name of your custom theme
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.imtiaz.ktimazrev.ui.FileOpen // Assumes your FileOpen composable is in ui package
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imtiaz.ktimazrev.model.Bookmark
import com.imtiaz.ktimazrev.model.Instruction
import com.imtiaz.ktimazrev.model.Symbol
import com.imtiaz.ktimazrev.ui.*
import com.imtiaz.ktimazrev.ui.theme.MobileARMDisassemblerTheme
import com.imtiaz.ktimazrev.viewmodel.DisassemblyViewModel
import com.imtiaz.ktimazrev.viewmodel.FileLoaderViewModel
import com.imtiaz.ktimazrev.viewmodel.LoadingState
import com.imtiaz.ktimazrev.viewmodel.MainTab
import com.imtiaz.ktimazrev.utils.FilePicker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var filePicker: FilePicker

    // Register for storage permission result
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission denied. Cannot load files.", Toast.LENGTH_LONG).show()
            // Optionally, guide user to settings if permission is crucial
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePicker = FilePicker(this)
        requestStoragePermission()

        setContent {
            MobileARMDisassemblerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen(filePicker)
                }
            }
        }
    }

    private fun requestStoragePermission() {
        // For API 33+, READ_MEDIA_AUDIO/VIDEO/IMAGES replaces READ_EXTERNAL_STORAGE.
        // For older APIs, READ_EXTERNAL_STORAGE is still relevant.
        // For SAF, direct permission might not be strictly needed for picking,
        // but it's good practice for general file access/caching.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // On Android 11 (API 30) and above, Scoped Storage is enforced.
            // Using SAF (Storage Access Framework) via ActivityResultContracts.OpenDocument
            // is the recommended way to access files.
            // We don't need explicit READ_EXTERNAL_STORAGE permission for SAF.
            // However, MANAGE_EXTERNAL_STORAGE might be requested for full file management,
            // but is generally discouraged and requires special approval.
            // For this app, SAF is sufficient.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(filePicker: FilePicker, fileLoaderViewModel: FileLoaderViewModel = viewModel(), disassemblyViewModel: DisassemblyViewModel = viewModel()) {
    val loadingState by fileLoaderViewModel.loadingState.collectAsStateWithLifecycle()
    val elfSectionNames by fileLoaderViewModel.elfSectionNames.collectAsStateWithLifecycle()
    val elfSymbols by fileLoaderViewModel.elfSymbols.collectAsStateWithLifecycle()
    val currentFilePath by fileLoaderViewModel.currentFilePath.collectAsStateWithLifecycle()

    val instructions by disassemblyViewModel.filteredInstructions.collectAsStateWithLifecycle()
    val hexDumpData by disassemblyViewModel.hexDumpData.collectAsStateWithLifecycle()
    val bookmarks by disassemblyViewModel.bookmarks.collectAsStateWithLifecycle()
    val currentTab by disassemblyViewModel.currentTab.collectAsStateWithLifecycle()
    val searchQuery by disassemblyViewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSection by disassemblyViewModel.currentSection.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mobile ARM Disassembler") },
                actions = {
                    IconButton(onClick = {
                        filePicker.pickFile { filePath ->
                            if (filePath != null) {
                                fileLoaderViewModel.loadFile(filePath)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("File selection cancelled or failed.")
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = "Open File")
                    }
                    IconButton(onClick = { /* TODO: Implement Settings */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { /* TODO: Implement Info/About */ }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { disassemblyViewModel.selectTab(tab) },
                        icon = {
                            when (tab) {
                                MainTab.Disassembly -> Icon(Icons.Default.Search, contentDescription = stringResource(R.string.disassembly_view_tab))
                                MainTab.HexView -> Icon(Icons.Default.Search, contentDescription = stringResource(R.string.hex_view_tab))
                                MainTab.Symbols -> Icon(Icons.Default.Search, contentDescription = stringResource(R.string.symbols_tab))
                                MainTab.Bookmarks -> Icon(Icons.Default.Search, contentDescription = stringResource(R.string.bookmarks_tab))
                                MainTab.GraphView -> Icon(Icons.Default.Search, contentDescription = stringResource(R.string.graph_view_tab))
                            }
                        },
                        label = {
                            Text(
                                when (tab) {
                                    MainTab.Disassembly -> stringResource(R.string.disassembly_view_tab)
                                    MainTab.HexView -> stringResource(R.string.hex_view_tab)
                                    MainTab.Symbols -> stringResource(R.string.symbols_tab)
                                    MainTab.Bookmarks -> stringResource(R.string.bookmarks_tab)
                                    MainTab.GraphView -> stringResource(R.string.graph_view_tab)
                                }
                            )
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            when (loadingState) {
                is LoadingState.Idle -> {
                    Text(
                        text = "Tap the folder icon to open an ELF file.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                is LoadingState.Loading -> {
                    val progress = (loadingState as LoadingState.Loading).progress
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(R.string.parsing_progress, progress),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                is LoadingState.Success -> {
                    Column(Modifier.fillMaxSize()) {
                        currentFilePath?.let { path ->
                            Text(
                                text = "Loaded: ${path.substringAfterLast('/')}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Section selection dropdown
                        if (elfSectionNames.isNotEmpty()) {
                            SectionSelector(
                                sectionNames = elfSectionNames,
                                selectedSection = currentSection,
                                onSectionSelected = { section ->
                                    // You'll need to know the base address and thumb mode for each section.
                                    // For simplicity, we'll assume 0 and ARM mode for now.
                                    // A real ELF parser would provide this.
                                    disassemblyViewModel.loadDisassemblyForSection(section, 0L, false)
                                }
                            )
                        }

                        // Search Bar (visible on Disassembly and Symbols tabs)
                        if (currentTab == MainTab.Disassembly || currentTab == MainTab.Symbols) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { disassemblyViewModel.updateSearchQuery(it) }
                            )
                        }

                        // Main Content based on selected tab
                        when (currentTab) {
                            MainTab.Disassembly -> {
                                DisassemblyView(
                                    instructions = instructions,
                                    symbols = elfSymbols, // Pass symbols for resolution/comments
                                    bookmarks = bookmarks,
                                    onAddBookmark = { address, name, comment ->
                                        disassemblyViewModel.addBookmark(address, name, comment)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.bookmark_added))
                                        }
                                    }
                                )
                            }
                            MainTab.HexView -> {
                                HexViewer(
                                    hexData = hexDumpData,
                                    startOffset = currentSection?.let {
                                        elfSectionNames.indexOf(it)
                                            .takeIf { it != -1 }
                                            ?.let { index -> elfSymbols.firstOrNull { sym -> sym.sectionName == it }?.value } // Very rough way to get base addr
                                            ?: 0L
                                    } ?: 0L
                                )
                            }
                            MainTab.Symbols -> {
                                SymbolsView(symbols = elfSymbols)
                            }
                            MainTab.Bookmarks -> {
                                BookmarksView(
                                    bookmarks = bookmarks,
                                    onRemoveBookmark = { disassemblyViewModel.removeBookmark(it) },
                                    onNavigateToAddress = { /* TODO: Implement navigation to address */ }
                                )
                            }
                            MainTab.GraphView -> {
                                GraphCanvas(
                                    instructions = instructions,
                                    symbols = elfSymbols
                                )
                            }
                        }
                    }
                }
                is LoadingState.Error -> {
                    val errorMessage = (loadingState as LoadingState.Error).message
                    Text(
                        text = stringResource(R.string.parsing_failed, errorMessage),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = {
                        // Optionally allow retrying or picking another file
                        filePicker.pickFile { filePath ->
                            if (filePath != null) {
                                fileLoaderViewModel.loadFile(filePath)
                            }
                        }
                    }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)) {
                        Text("Retry / Pick New File")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileARMDisassemblerTheme {
        // Preview the AppScreen in a default state (Idle)
        // Note: FilePicker and ViewModels need proper mocking for full preview functionality
        // This preview only shows the basic layout.
        AppScreen(filePicker = FilePicker(context = LocalContext.current as ComponentActivity))
    }
}