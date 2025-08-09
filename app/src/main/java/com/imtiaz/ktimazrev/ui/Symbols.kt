package com.imtiaz.ktimazrev.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imtiaz.ktimazrev.model.Symbol
import com.imtiaz.ktimazrev.model.toHexString

@Composable
fun SymbolsView(symbols: List<Symbol>) {
    if (symbols.isEmpty()) {
        Text(
            text = "No symbols found or loaded. Please load an ELF file.",
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
        items(symbols, key = { it.name + it.value }) { symbol ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO: Navigate to symbol address in disassembly view */ }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = symbol.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Address: ${symbol.value.toHexString()} | Size: ${symbol.size} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Section: ${symbol.sectionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            //Divider()
        }
    }
}