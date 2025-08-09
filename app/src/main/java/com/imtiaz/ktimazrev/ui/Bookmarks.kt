package com.imtiaz.ktimazrev.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imtiaz.ktimazrev.R
import com.imtiaz.ktimazrev.model.Bookmark
import com.imtiaz.ktimazrev.model.toHexString

@Composable
fun BookmarksView(
    bookmarks: List<Bookmark>,
    onRemoveBookmark: (Bookmark) -> Unit,
    onNavigateToAddress: (Long) -> Unit // Callback to navigate to address in disassembly view
) {
    if (bookmarks.isEmpty()) {
        Text(
            text = stringResource(R.string.no_bookmarks),
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
        items(bookmarks, key = { it.address }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAddress(bookmark.address) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${bookmark.name} @ ${bookmark.address.toHexString()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (bookmark.comment.isNotBlank()) {
                        Text(
                            text = bookmark.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onRemoveBookmark(bookmark) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove Bookmark")
                }
            }
            //Divider() // Optional: add a divider between bookmarks
        }
    }
}