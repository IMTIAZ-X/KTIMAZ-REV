#include "../include/arm_disassembler.h"
#include "../include/utils.h"
#include <cstring>
#include <iomanip>
#include <sstream>
#include <map>

// ARM instruction type identification masks
#define ARM_BRANCH_MASK     0x0E000000
#define ARM_BRANCH_VAL      0x0A000000
#define ARM_BLX_MASK        0x0E000000
#define ARM_BLX_VAL         0x0B000000
#define ARM_DATA_PROC_MASK  0x0C000000
#define ARM_DATA_PROC_VAL   0x00000000
#define ARM_LOAD_STORE_MASK 0x0C000000
#define ARM_LOAD_STORE_VAL  0x04000000

// Thumb instruction identification
#define THUMB_BRANCH_MASK   0xF000
#define THUMB_BRANCH_VAL    0xD000
#define THUMB_BL_MASK       0xF800
#define THUMB_BL_VAL        0xF000

ArmDisassembler::ArmDisassembler() {
    log_info("ARM Disassembler initialized.");
}

ArmDisassembler::~ArmDisassembler() {
    log_info("ARM Disassembler destroyed.");
}

std::vector<DisassembledInstruction> ArmDisassembler::disassemble_block(
    const uint8_t* data, size_t data_size, uint64_t base_address, bool is_thumb_mode) {
    
    std::vector<DisassembledInstruction> instructions;
    
    if (data == nullptr || data_size == 0) {
        log_error("Invalid data provided to disassemble_block.");
        return instructions;
    }
    
    size_t offset = 0;
    uint64_t current_address = base_address;
    
    while (offset < data_size) {
        int instruction_size = 0;
        DisassembledInstruction instr = decode_instruction(
            data + offset, current_address, is_thumb_mode, instruction_size);
        
        if (instruction_size <= 0) {
            // Invalid instruction, use default size
            instruction_size = is_thumb_mode ? 2 : 4;
            instr.mnemonic = "???";
            instr.operands = "";
            instr.bytes = 0;
            instr.is_branch = false;
            instr.branch_target = 0;
        }
        
        // Ensure we don't read past the end
        if (offset + instruction_size > data_size) {
            instruction_size = data_size - offset;
        }
        
        instructions.push_back(instr);
        offset += instruction_size;
        current_address += instruction_size;
    }
    
    return instructions;
}

DisassembledInstruction ArmDisassembler::decode_instruction(
    const uint8_t* instr_bytes, uint64_t current_address, bool is_thumb_mode, int& instruction_size) {
    
    DisassembledInstruction instr;
    instr.address = current_address;
    instr.is_branch = false;
    instr.branch_target = 0;
    instr.comment = "";
    
    if (is_thumb_mode) {
        // Thumb mode - 16-bit instructions
        instruction_size = 2;
        uint16_t instruction = (instr_bytes[1] << 8) | instr_bytes[0]; // Little-endian
        instr.bytes = instruction;
        
        // Check for 32-bit Thumb instructions (Thumb-2)
        if ((instruction & 0xF800) == 0xF000 || (instruction & 0xF800) == 0xF800 || (instruction & 0xE800) == 0xE800) {
            // This is a 32-bit Thumb instruction
            if (instruction_size + 2 <= 4) { // Make sure we have enough data
                instruction_size = 4;
                uint32_t full_instruction = instruction | ((instr_bytes[3] << 24) | (instr_bytes[2] << 16));
                instr.bytes = full_instruction;
                decode_thumb32_instruction(full_instruction, instr);
            } else {
                decode_thumb16_instruction(instruction, instr);
            }
        } else {
            decode_thumb16_instruction(instruction, instr);
        }
    } else {
        // ARM mode - 32-bit instructions
        instruction_size = 4;
        uint32_t instruction = (instr_bytes[3] << 24) | (instr_bytes[2] << 16) | 
                              (instr_bytes[1] << 8) | instr_bytes[0]; // Little-endian
        instr.bytes = instruction;
        decode_arm_instruction(instruction, instr, current_address);
    }
    
    return instr;
}

void ArmDisassembler::decode_arm_instruction(uint32_t instruction, DisassembledInstruction& instr, uint64_t current_address) {
    // Check condition code
    uint8_t condition = (instruction >> 28) & 0xF;
    std::string cond_suffix = get_condition_suffix(condition);
    
    if ((instruction & ARM_BRANCH_MASK) == ARM_BRANCH_VAL) {
        // Branch instruction
        instr.is_branch = true;
        bool link = instruction & 0x01000000;
        instr.mnemonic = link ? ("BL" + cond_suffix) : ("B" + cond_suffix);
        
        // Calculate branch target
        int32_t offset = (instruction & 0x00FFFFFF) << 2;
        if (offset & 0x02000000) { // Sign extend
            offset |= 0xFC000000;
        }
        instr.branch_target = current_address + 8 + offset; // PC + 8 + offset
        
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instr.branch_target;
        instr.operands = ss.str();
    }
    else if ((instruction & ARM_DATA_PROC_MASK) == ARM_DATA_PROC_VAL) {
        // Data processing instruction
        decode_data_processing(instruction, instr, cond_suffix);
    }
    else if ((instruction & ARM_LOAD_STORE_MASK) == ARM_LOAD_STORE_VAL) {
        // Load/Store instruction
        decode_load_store(instruction, instr, cond_suffix);
    }
    else {
        // Unknown instruction
        instr.mnemonic = "UNK" + cond_suffix;
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instruction;
        instr.operands = ss.str();
    }
}

void ArmDisassembler::decode_thumb16_instruction(uint16_t instruction, DisassembledInstruction& instr) {
    if ((instruction & 0xF000) == 0xD000) {
        // Conditional branch
        instr.is_branch = true;
        uint8_t condition = (instruction >> 8) & 0xF;
        instr.mnemonic = "B" + get_condition_suffix(condition);
        
        // Calculate branch target (sign-extended 8-bit offset * 2)
        int16_t offset = (int8_t)(instruction & 0xFF) * 2;
        instr.branch_target = instr.address + 4 + offset; // PC + 4 + offset
        
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instr.branch_target;
        instr.operands = ss.str();
    }
    else if ((instruction & 0xF800) == 0xE000) {
        // Unconditional branch
        instr.is_branch = true;
        instr.mnemonic = "B";
        
        // Calculate branch target (sign-extended 11-bit offset * 2)
        int16_t offset = (instruction & 0x7FF) * 2;
        if (offset & 0x800) { // Sign extend
            offset |= 0xF000;
        }
        instr.branch_target = instr.address + 4 + offset;
        
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instr.branch_target;
        instr.operands = ss.str();
    }
    else {
        // Other Thumb instructions - simplified decoding
        decode_thumb_data_processing(instruction, instr);
    }
}

void ArmDisassembler::decode_thumb32_instruction(uint32_t instruction, DisassembledInstruction& instr) {
    // Simplified Thumb-2 decoding
    if ((instruction & 0xF800D000) == 0xF000D000) {
        // BL instruction
        instr.is_branch = true;
        instr.mnemonic = "BL";
        
        // Complex offset calculation for Thumb-2 BL
        uint32_t s = (instruction >> 26) & 1;
        uint32_t j1 = (instruction >> 13) & 1;
        uint32_t j2 = (instruction >> 11) & 1;
        uint32_t imm10 = (instruction >> 16) & 0x3FF;
        uint32_t imm11 = instruction & 0x7FF;
        
        uint32_t i1 = !(j1 ^ s);
        uint32_t i2 = !(j2 ^ s);
        
        int32_t offset = (s << 24) | (i1 << 23) | (i2 << 22) | (imm10 << 12) | (imm11 << 1);
        if (s) offset |= 0xFE000000; // Sign extend
        
        instr.branch_target = instr.address + 4 + offset;
        
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instr.branch_target;
        instr.operands = ss.str();
    }
    else {
        // Other Thumb-2 instructions
        instr.mnemonic = "T32_UNK";
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instruction;
        instr.operands = ss.str();
    }
}

void ArmDisassembler::decode_data_processing(uint32_t instruction, DisassembledInstruction& instr, const std::string& cond_suffix) {
    uint8_t opcode = (instruction >> 21) & 0xF;
    uint8_t rd = (instruction >> 12) & 0xF;
    uint8_t rn = (instruction >> 16) & 0xF;
    
    static const char* opcodes[] = {
        "AND", "EOR", "SUB", "RSB", "ADD", "ADC", "SBC", "RSC",
        "TST", "TEQ", "CMP", "CMN", "ORR", "MOV", "BIC", "MVN"
    };
    
    instr.mnemonic = opcodes[opcode] + cond_suffix;
    
    // Simplified operand formatting
    std::stringstream ss;
    ss << "R" << rd;
    if (opcode != 13 && opcode != 15) { // Not MOV or MVN
        ss << ", R" << rn;
    }
    
    if (instruction & 0x02000000) {
        // Immediate operand
        uint32_t imm = instruction & 0xFF;
        uint8_t rotate = ((instruction >> 8) & 0xF) * 2;
        imm = (imm >> rotate) | (imm << (32 - rotate));
        ss << ", #0x" << std::hex << std::uppercase << imm;
    } else {
        // Register operand
        uint8_t rm = instruction & 0xF;
        ss << ", R" << rm;
    }
    
    instr.operands = ss.str();
}

void ArmDisassembler::decode_load_store(uint32_t instruction, DisassembledInstruction& instr, const std::string& cond_suffix) {
    bool load = instruction & 0x00100000;
    bool byte = instruction & 0x00400000;
    
    instr.mnemonic = (load ? "LDR" : "STR") + (byte ? "B" : "") + cond_suffix;
    
    uint8_t rt = (instruction >> 12) & 0xF;
    uint8_t rn = (instruction >> 16) & 0xF;
    
    std::stringstream ss;
    ss << "R" << rt << ", [R" << rn;
    
    if (instruction & 0x02000000) {
        // Register offset
        uint8_t rm = instruction & 0xF;
        ss << ", R" << rm << "]";
    } else {
        // Immediate offset
        uint16_t offset = instruction & 0xFFF;
        if (offset != 0) {
            ss << ", #" << (instruction & 0x00800000 ? "" : "-") << "0x" << std::hex << std::uppercase << offset;
        }
        ss << "]";
    }
    
    instr.operands = ss.str();
}

void ArmDisassembler::decode_thumb_data_processing(uint16_t instruction, DisassembledInstruction& instr) {
    // Simplified Thumb data processing
    if ((instruction & 0xFF00) == 0x2000) {
        // MOV immediate
        instr.mnemonic = "MOV";
        uint8_t rd = (instruction >> 8) & 0x7;
        uint8_t imm = instruction & 0xFF;
        
        std::stringstream ss;
        ss << "R" << rd << ", #0x" << std::hex << std::uppercase << imm;
        instr.operands = ss.str();
    }
    else if ((instruction & 0xFE00) == 0x1C00) {
        // ADD immediate
        instr.mnemonic = "ADD";
        uint8_t rd = instruction & 0x7;
        uint8_t rn = (instruction >> 3) & 0x7;
        uint8_t imm = (instruction >> 6) & 0x7;
        
        std::stringstream ss;
        ss << "R" << rd << ", R" << rn << ", #" << imm;
        instr.operands = ss.str();
    }
    else {
        // Unknown Thumb instruction
        instr.mnemonic = "T16_UNK";
        std::stringstream ss;
        ss << "0x" << std::hex << std::uppercase << instruction;
        instr.operands = ss.str();
    }
}

std::string ArmDisassembler::get_condition_suffix(uint8_t condition) {
    static const char* conditions[] = {
        "EQ", "NE", "CS", "CC", "MI", "PL", "VS", "VC",
        "HI", "LS", "GE", "LT", "GT", "LE", "", "NV"
    };
    return conditions[condition & 0xF];
}

std::string ArmDisassembler::get_mnemonic(uint32_t instruction, bool is_thumb_mode) {
    // Legacy method - functionality moved to decode_* methods
    return "LEGACY";
}

std::string ArmDisassembler::get_operands(uint32_t instruction, bool is_thumb_mode) {
    // Legacy method - functionality moved to decode_* methods
    return "";
}