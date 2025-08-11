package com.imtiaz.ktimazrev

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imtiaz.ktimazrev.ui.*
import com.imtiaz.ktimazrev.ui.theme.MobileARMDisassemblerTheme
import com.imtiaz.ktimazrev.utils.FilePicker
import com.imtiaz.ktimazrev.viewmodel.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var filePicker: FilePicker

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission denied. Cannot load files.", Toast.LENGTH_LONG).show()
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
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppScreen(filePicker)
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    filePicker: FilePicker,
    fileLoaderViewModel: FileLoaderViewModel = viewModel(),
    disassemblyViewModel: DisassemblyViewModel = viewModel(),
) {
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
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { /* TODO: About */ }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                },
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
                                MainTab.Disassembly -> Icon(Icons.Default.Code, contentDescription = null)
                                MainTab.HexView -> Icon(Icons.Default.ViewModule, contentDescription = null)
                                MainTab.Symbols -> Icon(Icons.Default.List, contentDescription = null)
                                MainTab.Bookmarks -> Icon(Icons.Default.Bookmark, contentDescription = null)
                                MainTab.GraphView -> Icon(Icons.Default.AccountTree, contentDescription = null)
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
                                },
                            )
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (loadingState) {
                is LoadingState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Tap the folder icon to open an ELF file.",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is LoadingState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.parsing_progress, loadingState.progress),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is LoadingState.Success -> {
                    Column(Modifier.fillMaxSize()) {
                        currentFilePath?.let { path ->
                            Text(
                                text = "Loaded: ${path.substringAfterLast('/')}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        if (elfSectionNames.isNotEmpty()) {
                            SectionSelector(
                                sectionNames = elfSectionNames,
                                selectedSection = currentSection,
                                onSectionSelected = { section ->
                                    disassemblyViewModel.loadDisassemblyForSection(section, 0L, false)
                                },
                            )
                        }

                        if (currentTab == MainTab.Disassembly || currentTab == MainTab.Symbols) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { disassemblyViewModel.updateSearchQuery(it) },
                            )
                        }

                        when (currentTab) {
                            MainTab.Disassembly -> {
                                DisassemblyView(
                                    instructions = instructions,
                                    symbols = elfSymbols,
                                    bookmarks = bookmarks,
                                    onAddBookmark = { address, name, comment ->
                                        disassemblyViewModel.addBookmark(address, name, comment)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Bookmark added")
                                        }
                                    },
                                )
                            }
                            MainTab.HexView -> {
                                HexViewer(
                                    hexData = hexDumpData,
                                    startOffset = 0L,
                                )
                            }
                            MainTab.Symbols -> {
                                SymbolsView(symbols = elfSymbols)
                            }
                            MainTab.Bookmarks -> {
                                BookmarksView(
                                    bookmarks = bookmarks,
                                    onRemoveBookmark = { disassemblyViewModel.removeBookmark(it) },
                                    onNavigateToAddress = { /* TODO: Navigate */ },
                                )
                            }
                            MainTab.GraphView -> {
                                GraphCanvas(
                                    instructions = instructions,
                                    symbols = elfSymbols,
                                )
                            }
                        }
                    }
                }
                is LoadingState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Error: ${loadingState.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                        Button(
                            onClick = {
                                filePicker.pickFile { filePath ->
                                    if (filePath != null) {
                                        fileLoaderViewModel.loadFile(filePath)
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("Retry / Pick New File")
                        }
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
        AppScreen(filePicker = FilePicker(LocalContext.current as ComponentActivity))
    }
}