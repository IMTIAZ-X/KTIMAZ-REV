#ifndef MOBILE_ARM_DISASSEMBLER_ELF_PARSER_H
#define MOBILE_ARM_DISASSEMBLER_ELF_PARSER_H

#include <vector>
#include <string>
#include <cstdint>
#include <map>
#include <stdexcept>
#include <string_view>

// Forward declaration
struct MappedFile;

// ELF Header structure (simplified for common fields)
struct ElfHeader {
    uint8_t  e_ident[16];   // ELF Identification
    uint16_t e_type;        // Object file type
    uint16_t e_machine;     // Machine type
    uint32_t e_version;     // ELF version
    uint64_t e_entry;       // Entry point address
    uint64_t e_phoff;       // Program header table file offset
    uint64_t e_shoff;       // Section header table file offset
    uint32_t e_flags;       // Processor-specific flags
    uint16_t e_ehsize;      // ELF header size
    uint16_t e_phentsize;   // Size of program header entry
    uint16_t e_phnum;       // Number of program header entries
    uint16_t e_shentsize;   // Size of section header entry
    uint16_t e_shnum;       // Number of section header entries
    uint16_t e_shstrndx;    // Section header string table index

    bool is_64bit;
    bool is_little_endian;
};

// Section Header structure (simplified)
struct SectionHeader {
    uint32_t sh_name;       // Section name (offset into string table)
    uint32_t sh_type;       // Section type
    uint64_t sh_flags;      // Section flags
    uint64_t sh_addr;       // Section virtual address at execution
    uint64_t sh_offset;     // Section file offset
    uint64_t sh_size;       // Section size in bytes
    uint32_t sh_link;       // Link to another section
    uint32_t sh_info;       // Additional section information
    uint64_t sh_addralign;  // Section alignment
    uint64_t sh_entsize;    // Entry size if section holds table

    std::string name;       // Resolved section name
};

// Symbol Table Entry structure (simplified)
struct SymbolEntry {
    uint32_t st_name;       // Symbol name (offset into string table)
    uint8_t  st_info;       // Type and binding attributes
    uint8_t  st_other;      // Visibility
    uint16_t st_shndx;      // Section index
    uint64_t st_value;      // Symbol value
    uint64_t st_size;       // Symbol size

    std::string name;       // Resolved symbol name
};

// Main ELF Parser class
class ElfParser {
public:
    explicit ElfParser(const MappedFile& file);
    ~ElfParser();

    bool parse();

    const ElfHeader& get_header() const { return header_; }
    const std::vector<SectionHeader>& get_section_headers() const { return section_headers_; }
    const std::vector<SymbolEntry>& get_symbols() const { return symbols_; }
    const uint8_t* get_section_data(const std::string& section_name) const;
    size_t get_section_size(const std::string& section_name) const;
    uint64_t get_section_address(const std::string& section_name) const;

private:
    const MappedFile& file_;
    ElfHeader header_;
    std::vector<SectionHeader> section_headers_;
    std::vector<SymbolEntry> symbols_;
    std::string_view shstrtab_data_; // Section Header String Table data
    std::string_view strtab_data_;   // String Table data (for symbols)
    std::string_view dynstrtab_data_; // Dynamic String Table data (for dynamic symbols)

    bool read_elf_header();
    bool read_section_headers();
    bool resolve_section_names();
    bool read_symbols();
    void resolve_symbol_names();

    // Helper to read data safely with endianness handling
    template<typename T>
    T read_value(size_t offset) const;
};

// Helper function declaration
bool sh_type_is_dynamic(uint32_t sh_type);

#endif //MOBILE_ARM_DISASSEMBLER_ELF_PARSER_H