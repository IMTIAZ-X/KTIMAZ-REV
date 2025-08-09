#ifndef MOBILE_ARM_DISASSEMBLER_ARM_DISASSEMBLER_H
#define MOBILE_ARM_DISASSEMBLER_ARM_DISASSEMBLER_H

#include <string>
#include <vector>
#include <cstdint>

// Represents a disassembled instruction
struct DisassembledInstruction {
    uint64_t address;
    uint32_t bytes;      // Raw instruction bytes (e.g., 4 bytes for ARM/ARM64, 2/4 for Thumb)
    std::string mnemonic;
    std::string operands;
    std::string comment; // For potential inline comments (e.g., resolved symbol)
    bool is_branch;
    uint64_t branch_target; // If it's a branch, its target address
};

// Simplified ARM/Thumb/ARM64 Disassembler Interface
class ArmDisassembler {
public:
    ArmDisassembler();
    ~ArmDisassembler();

    // Disassemble a block of bytes starting from a given virtual address.
    // Assumes `data` points to the raw instruction bytes.
    // `data_size` is the length of the data block.
    // `base_address` is the virtual address corresponding to the start of `data`.
    // `is_thumb` indicates if the current context is Thumb mode.
    std::vector<DisassembledInstruction> disassemble_block(
        const uint8_t* data, size_t data_size, uint64_t base_address, bool is_thumb_mode);

private:
    // Internal helper for decoding a single instruction
    DisassembledInstruction decode_instruction(
        const uint8_t* instr_bytes, uint64_t current_address, bool is_thumb_mode, int& instruction_size);

    // ARM instruction decoding methods
    void decode_arm_instruction(uint32_t instruction, DisassembledInstruction& instr, uint64_t current_address);
    void decode_data_processing(uint32_t instruction, DisassembledInstruction& instr, const std::string& cond_suffix);
    void decode_load_store(uint32_t instruction, DisassembledInstruction& instr, const std::string& cond_suffix);
    
    // Thumb instruction decoding methods
    void decode_thumb16_instruction(uint16_t instruction, DisassembledInstruction& instr);
    void decode_thumb32_instruction(uint32_t instruction, DisassembledInstruction& instr);
    void decode_thumb_data_processing(uint16_t instruction, DisassembledInstruction& instr);
    
    // Helper methods
    std::string get_condition_suffix(uint8_t condition);

    // Placeholder for actual ARM/Thumb/ARM64 decoding logic
    // In a real implementation, this would involve complex bitwise operations
    // and lookup tables, or delegation to a library like Capstone.
    std::string get_mnemonic(uint32_t instruction, bool is_thumb_mode);
    std::string get_operands(uint32_t instruction, bool is_thumb_mode);
};

#endif //MOBILE_ARM_DISASSEMBLER_ARM_DISASSEMBLER_H