package com.imtiaz.ktimazstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
// Jetpack Compose imports for drawing and theming
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.toPx
import com.imtiaz.ktimazstudio.ui.theme.MobileARMDisassemblerTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.disassembler.model.Instruction
import com.example.disassembler.model.Symbol
import com.example.disassembler.model.toHexString
import android.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.toPx
import kotlin.math.max

@Composable
fun GraphCanvas(instructions: List<Instruction>, symbols: List<Symbol>) {
    if (instructions.isEmpty()) {
        Text(
            text = "No graph data available. Load an ELF file and section.",
            modifier = Modifier.fillMaxSize().wrapContentSize(),
            color = MaterialTheme.colorScheme.onBackground
        )
        return
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val instructionHeight = with(LocalDensity.current) { 20.sp.toPx() } // Approximate height per instruction line
    val nodePadding = 10.dp.toPx()
    val nodeWidth = with(LocalDensity.current) { 250.dp.toPx() } // Fixed width for nodes

    // Simplified node layout - just linear for now
    val nodes = remember(instructions) {
        val map = mutableMapOf<Long, Offset>()
        var y = nodePadding
        instructions.forEach { instr ->
            map[instr.address] = Offset(nodePadding, y)
            y += instructionHeight
        }
        map
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = max(0.5f, scale * zoom) // Prevent too small zoom
                    offset += pan / scale // Adjust pan based on current scale
                }
            }
    ) {
        val transformedOffset = offset
        val transformedScale = scale

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 12.sp.toPx()
                typeface = android.graphics.Typeface.MONOSPACE
            }
            val branchPaint = Paint().apply {
                color = android.graphics.Color.RED
                strokeWidth = 2f
            }
            val edgePaint = Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 1.5f
            }

            // Draw nodes (simplified as just instruction lines)
            instructions.forEach { instr ->
                val nodePos = nodes[instr.address]
                if (nodePos != null) {
                    val displayX = nodePos.x * transformedScale + transformedOffset.x
                    val displayY = nodePos.y * transformedScale + transformedOffset.y

                    if (displayX < size.width && displayY < size.height &&
                        displayX + nodeWidth * transformedScale > 0 && displayY + instructionHeight * transformedScale > 0) {
                        // Draw instruction text
                        val instructionText = "${instr.address.toHexString()}: ${instr.mnemonic} ${instr.operands}"
                        canvas.nativeCanvas.drawText(
                            instructionText,
                            displayX,
                            displayY + instructionHeight / 2, // Centered vertically
                            paint.apply { color = MaterialTheme.colorScheme.onBackground.toArgb() }
                        )

                        // Draw branch lines
                        if (instr.isBranch && instr.branchTarget != 0L) {
                            val targetNodePos = nodes[instr.branchTarget]
                            if (targetNodePos != null) {
                                val targetDisplayX = targetNodePos.x * transformedScale + transformedOffset.x
                                val targetDisplayY = targetNodePos.y * transformedScale + transformedOffset.y

                                val startPoint = Offset(displayX + nodeWidth * transformedScale, displayY + instructionHeight / 2)
                                val endPoint = Offset(targetDisplayX, targetDisplayY + instructionHeight / 2)

                                drawLine(
                                    color = Color(branchPaint.color),
                                    start = startPoint,
                                    end = endPoint,
                                    strokeWidth = branchPaint.strokeWidth,
                                    alpha = 0.7f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GraphCanvasPreview() {
    MobileARMDisassemblerTheme {
        val sampleInstructions = listOf(
            Instruction(0x1000, "MOV", "R0, #0", rawBytes = 0xE3A00000, byteLength = 4, isBranch = false),
            Instruction(0x1004, "ADD", "R1, R0, #1", rawBytes = 0xE2801001, byteLength = 4, isBranch = false),
            Instruction(0x1008, "CMP", "R1, #10", rawBytes = 0xE351000A, byteLength = 4, isBranch = false),
            Instruction(0x100C, "BLT", "0x1000", rawBytes = 0xDA000000, byteLength = 4, isBranch = true, branchTarget = 0x1000),
            Instruction(0x1010, "BX", "LR", rawBytes = 0xE12FFF1E, byteLength = 4, isBranch = true, branchTarget = 0L)
        )
        GraphCanvas(instructions = sampleInstructions, symbols = emptyList())
    }
}