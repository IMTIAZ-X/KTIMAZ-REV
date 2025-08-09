package com.imtiaz.ktimazrev.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionSelector(
    sectionNames: List<String>,
    selectedSection: String?,
    onSectionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = selectedSection ?: "Select a section"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        TextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("ELF Section") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            sectionNames.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section) },
                    onClick = {
                        onSectionSelected(section)
                        expanded = false
                    }
                )
            }
        }
    }
}