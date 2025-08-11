// app/src/test/java/com/imtiaz/ktimazrev/ExampleUnitTest.kt
package com.imtiaz.ktimazrev

import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun hexString_conversion_isCorrect() {
        val address = 0x1000L
        val hexString = "0x" + String.format("%08X", address)
        assertEquals("0x00001000", hexString)
    }
    
    @Test
    fun instruction_properties_areValid() {
        // Test instruction data class
        val instruction = com.imtiaz.ktimazrev.model.Instruction(
            address = 0x1000L,
            mnemonic = "MOV",
            operands = "R0, #0",
            rawBytes = 0xE3A00000L,
            byteLength = 4,
            isBranch = false
        )
        
        assertEquals(0x1000L, instruction.address)
        assertEquals("MOV", instruction.mnemonic)
        assertEquals("R0, #0", instruction.operands)
        assertEquals(4, instruction.byteLength)
        assertFalse(instruction.isBranch)
    }
}
