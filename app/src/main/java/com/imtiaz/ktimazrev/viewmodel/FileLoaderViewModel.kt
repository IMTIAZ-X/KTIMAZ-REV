package com.imtiaz.ktimazrev.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imtiaz.ktimazrev.model.Symbol
import com.imtiaz.ktimazrev.utils.AppThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileLoaderViewModel : ViewModel() {
    // States for UI
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _currentFilePath = MutableStateFlow<String?>(null)
    val currentFilePath: StateFlow<String?> = _currentFilePath.asStateFlow()

    private val _elfSectionNames = MutableStateFlow<List<String>>(emptyList())
    val elfSectionNames: StateFlow<List<String>> = _elfSectionNames.asStateFlow()

    private val _elfSymbols = MutableStateFlow<List<Symbol>>(emptyList())
    val elfSymbols: StateFlow<List<Symbol>> = _elfSymbols.asStateFlow()

    // Native methods (declared in JNI)
    external fun loadFileAndParseNative(filePath: String)

    external fun getElfSectionNamesNative(): Array<String>?

    external fun getElfSymbolsNative(): Array<Symbol>?

    // Initialize native library
    init {
        System.loadLibrary("mobilearmdisassembler")
    }

    fun loadFile(filePath: String) {
        _currentFilePath.value = filePath
        viewModelScope.launch(AppThreadPool.IO) {
            _loadingState.value = LoadingState.Loading(0)
            loadFileAndParseNative(filePath)
        }
    }

    // Callbacks from native C++ code (JNI)
    @Suppress("unused") // Called by native code
    fun onParsingStarted() {
        viewModelScope.launch(AppThreadPool.Main) {
            _loadingState.value = LoadingState.Loading(10)
        }
    }

    @Suppress("unused") // Called by native code
    fun onParsingProgress(progress: Int) {
        viewModelScope.launch(AppThreadPool.Main) {
            _loadingState.value = LoadingState.Loading(progress)
        }
    }

    @Suppress("unused") // Called by native code
    fun onParsingFinished(success: Boolean) {
        viewModelScope.launch(AppThreadPool.Main) {
            if (success) {
                _loadingState.value = LoadingState.Success
                // Fetch section names and symbols after successful parsing
                loadElfMetadata()
            } else {
                _loadingState.value = LoadingState.Error("Parsing failed.")
            }
        }
    }

    @Suppress("unused") // Called by native code
    fun onFileReadError(errorMessage: String) {
        viewModelScope.launch(AppThreadPool.Main) {
            _loadingState.value = LoadingState.Error(errorMessage)
        }
    }

    private fun loadElfMetadata() {
        viewModelScope.launch(AppThreadPool.IO) {
            try {
                val sectionNames = getElfSectionNamesNative()?.toList() ?: emptyList()
                val symbols = getElfSymbolsNative()?.toList() ?: emptyList()

                _elfSectionNames.value = sectionNames
                _elfSymbols.value = symbols
            } catch (e: Exception) {
                e.printStackTrace()
                _loadingState.value = LoadingState.Error("Failed to load ELF metadata: ${e.message}")
            }
        }
    }
}

sealed class LoadingState {

    object Idle : LoadingState()

    data class Loading(val progress: Int) : LoadingState()

    object Success : LoadingState()

    data class Error(val message: String) : LoadingState()
}