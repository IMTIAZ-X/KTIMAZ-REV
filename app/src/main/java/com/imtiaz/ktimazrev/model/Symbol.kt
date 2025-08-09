package com.imtiaz.ktimazrev.model

data class Symbol(
    val name: String,
    val value: Long, // Virtual address of the symbol
    val size: Long,  // Size of the symbol
    val sectionName: String // Section this symbol belongs to
)