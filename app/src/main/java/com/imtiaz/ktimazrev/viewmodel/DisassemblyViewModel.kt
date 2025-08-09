package com.imtiaz.ktimazrev.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imtiaz.ktimazrev.model.Bookmark
import com.imtiaz.ktimazrev.model.Instruction
import com.imtiaz.ktimazrev.model.Symbol
import com.imtiaz.ktimazrev.utils.AppThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DisassemblyViewModel : ViewModel() {

    // --- State Management ---
    private val _instructions = MutableStateFlow<List<Instruction>>(emptyList())
    val instructions: StateFlow<List<Instruction>> = _instructions.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _currentSection = MutableStateFlow<String?>(null)
    val currentSection: StateFlow<String?> = _currentSection.asStateFlow()

    private val _hexDumpData = MutableStateFlow<ByteArray>(byteArrayOf())
    val hexDumpData: StateFlow<ByteArray> = _hexDumpData.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.Disassembly)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Combined flow for filtered instructions based on search query
    val filteredInstructions: StateFlow<List<Instruction>> = combine(
        _instructions,
        _searchQuery
    ) { instructions, query ->
        if (query.isBlank()) {
            instructions
        } else {
            instructions.filter {
                it.mnemonic.contains(query, ignoreCase = true) ||
                it.operands.contains(query, ignoreCase = true) ||
                it.comment.contains(query, ignoreCase = true) ||
                it.address.toHexString().contains(query, ignoreCase = true)
            }
        }
    }.asStateFlow()

    // --- Native Methods (declared in JNI) ---
    external fun getDisassembledInstructionsNative(
        sectionName: String,
        baseAddress: Long,
        isThumbMode: Boolean
    ): Array<Instruction>?

    external fun getHexDumpNative(
        sectionName: String,
        offset: Long,
        length: Int
    ): ByteArray?

    // --- Public Functions for UI Interaction ---

    fun loadDisassemblyForSection(sectionName: String, baseAddress: Long, isThumbMode: Boolean = false) {
        _currentSection.value = sectionName
        viewModelScope.launch(AppThreadPool.IO) {
            try {
                // For demonstration, we'll request a fixed amount of data for hex dump too
                // In a real app, hex dump would be loaded incrementally or on demand
                val hexData = getHexDumpNative(sectionName, 0, 4096) ?: byteArrayOf() // Load first 4KB
                _hexDumpData.value = hexData

                val disassembledArray = getDisassembledInstructionsNative(
                    sectionName, baseAddress, isThumbMode
                )
                _instructions.value = disassembledArray?.toList() ?: emptyList()
                println("Disassembled ${instructions.value.size} instructions for section $sectionName")
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error
            }
        }
    }

    fun addBookmark(address: Long, name: String, comment: String) {
        _bookmarks.update { currentBookmarks ->
            (currentBookmarks + Bookmark(address, name, comment)).sortedBy { it.address }
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        _bookmarks.update { currentBookmarks ->
            currentBookmarks.filter { it != bookmark }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }
}

enum class MainTab {
    Disassembly, HexView, Symbols, Bookmarks, GraphView
}