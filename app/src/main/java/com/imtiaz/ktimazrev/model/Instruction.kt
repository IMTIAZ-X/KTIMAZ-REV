package com.imtiaz.ktimazrev.model

data class Instruction(
    val address: Long,
    val mnemonic: String,
    val operands: String,
    val comment: String = "",
    val rawBytes: Long, // Use Long for raw bytes (e.g., 0xDEADBEEF)
    val byteLength: Int, // Length of the instruction in bytes (2 or 4)
    val isBranch: Boolean,
    val branchTarget: Long = 0L
)

fun Long.toHexString(length: Int = 8): String {
    return "0x" + String.format("%0${length}X", this)
}

fun ByteArray.toHexString(bytesPerLine: Int = 16): String {
    val hexChars = CharArray(size * 2)
    for (i in indices) {
        val v = get(i).toInt() and 0xFF
        hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
        hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
    }
    val sb = StringBuilder()
    for (i in hexChars.indices) {
        sb.append(hexChars[i])
        if ((i + 1) % 2 == 0 && (i + 1) % (bytesPerLine * 2) != 0) {
            sb.append(" ")
        }
    }
    return sb.toString()
}

fun ByteArray.toAsciiString(): String {
    return joinToString("") {
        val char = it.toChar()
        if (char.isLetterOrDigit() || char.isWhitespace() || char in "!@#$%^&*()_+-=[]{}|;':\",.<>/?`~") {
            char.toString()
        } else {
            "."
        }
    }
}

// Extension function to convert a rawBytes Long to a displayable hex string
fun Long.toRawBytesHexString(byteLength: Int): String {
    return when (byteLength) {
        2 -> String.format("%04X", this and 0xFFFF)
        4 -> String.format("%08X", this)
        else -> String.format("%08X", this) // Default to 4 bytes
    }
}