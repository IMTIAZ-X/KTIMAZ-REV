package com.imtiaz.ktimazrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imtiaz.ktimazrev.model.toAsciiString
import com.imtiaz.ktimazrev.model.toHexString

@Composable
fun HexViewer(hexData: ByteArray, startOffset: Long, bytesPerRow: Int = 16) {
    if (hexData.isEmpty()) {
        Text(
            text = "No hex data available. Please load an ELF file.",
            modifier = Modifier.fillMaxSize().wrapContentSize(),
            color = MaterialTheme.colorScheme.onBackground
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        val numRows = (hexData.size + bytesPerRow - 1) / bytesPerRow

        itemsIndexed(Array(numRows) { it }) { index, _ ->
            val rowStart = index * bytesPerRow
            val rowEnd = (rowStart + bytesPerRow).coerceAtMost(hexData.size)
            val rowBytes = hexData.sliceArray(rowStart until rowEnd)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                // Offset
                Text(
                    text = String.format("0x%08X", startOffset + rowStart),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Using a more subtle color
                    modifier = Modifier.width(90.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Hex bytes
                Text(
                    text = rowBytes.toHexString(bytesPerRow),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // ASCII representation
                Text(
                    text = rowBytes.toAsciiString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(bytesPerRow.times(8).dp) // Estimate width for ASCII
                )
            }
        }
    }
}