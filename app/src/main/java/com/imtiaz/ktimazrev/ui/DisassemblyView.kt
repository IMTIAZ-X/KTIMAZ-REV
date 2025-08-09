package com.imtiaz.ktimazrev.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imtiaz.ktimazrev.R
import com.imtiaz.ktimazrev.model.Bookmark
import com.imtiaz.ktimazrev.model.Instruction
import com.imtiaz.ktimazrev.model.Symbol
import com.imtiaz.ktimazrev.model.toHexString
import com.imtiaz.ktimazrev.model.toRawBytesHexString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisassemblyView(
    instructions: List<Instruction>,
    symbols: List<Symbol>,
    bookmarks: List<Bookmark>,
    onAddBookmark: (address: Long, name: String, comment: String) -> Unit
) {
    if (instructions.isEmpty()) {
        Text(
            text = "No disassembly data available. Load an ELF file and select a section.",
            modifier = Modifier.fillMaxSize().wrapContentSize(),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        itemsIndexed(instructions) { index, instruction ->
            val symbolAtAddress = symbols.firstOrNull { it.value == instruction.address }
            val bookmarkAtAddress = bookmarks.firstOrNull { it.address == instruction.address }

            // Display symbol name if exists at this address
            if (symbolAtAddress != null) {
                Text(
                    text = "--- ${symbolAtAddress.name} ---",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Individual instruction row
            var showBookmarkDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (bookmarkAtAddress != null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .combinedClickable(
                        onClick = {
                            // Handle click (e.g., navigate to xrefs, or simply select)
                            // println("Clicked instruction at 0x${instruction.address.toHexString()}")
                        },
                        onLongClick = {
                            showBookmarkDialog = true
                        }
                    )
                    .padding(vertical = 2.dp)
            ) {
                // Bookmark Icon
                if (bookmarkAtAddress != null) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Bookmark",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(20.dp)) // Maintain alignment
                }

                // Address
                Text(
                    text = instruction.address.toHexString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(90.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Raw Bytes
                Text(
                    text = instruction.rawBytes.toRawBytesHexString(instruction.byteLength),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(70.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Mnemonic
                Text(
                    text = instruction.mnemonic,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(60.dp)
                )

                // Operands
                Text(
                    text = instruction.operands,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Comment (e.g., resolved symbol for branch target)
                if (instruction.isBranch && instruction.branchTarget != 0L) {
                    val targetSymbol = symbols.firstOrNull { it.value == instruction.branchTarget }?.name
                    if (targetSymbol != null) {
                        Text(
                            text = "; -> $targetSymbol",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.tertiary, // Use a distinct color for comments
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                } else if (instruction.comment.isNotBlank()) {
                    Text(
                        text = "; ${instruction.comment}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (showBookmarkDialog) {
                BookmarkDialog(
                    address = instruction.address,
                    initialName = bookmarkAtAddress?.name ?: "",
                    initialComment = bookmarkAtAddress?.comment ?: "",
                    onDismiss = { showBookmarkDialog = false },
                    onSave = { address, name, comment ->
                        onAddBookmark(address, name, comment)
                        showBookmarkDialog = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDialog(
    address: Long,
    initialName: String,
    initialComment: String,
    onDismiss: () -> Unit,
    onSave: (address: Long, name: String, comment: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var comment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add/Edit Bookmark at ${address.toHexString()}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.bookmark_name_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(stringResource(id = R.string.bookmark_comment_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(address, name, comment) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}