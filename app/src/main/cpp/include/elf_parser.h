#include "../include/elf_parser.h"
#include "../include/utils.h"
#include <cstring> // For memcpy
#include <algorithm> // For std::min
#include <endian.h> // For htobe64, le64toh etc. (Linux/Android specific)

// ELF magic numbers
const uint8_t ELFMAG[4] = {0x7F, 'E', 'L', 'F'};

// ELF constants
enum ElfIdent {
    EI_MAG0       = 0,     // File magic byte 0
    EI_MAG1       = 1,     // File magic byte 1
    EI_MAG2       = 2,     // File magic byte 2
    EI_MAG3       = 3,     // File magic byte 3
    EI_CLASS      = 4,     // File class (32/64-bit)
    EI_DATA       = 5,     // Data encoding (endianness)
    EI_VERSION    = 6,     // ELF header version
    EI_OSABI      = 7,     // OS/ABI identification
    EI_ABIVERSION = 8,     // ABI version
    EI_PAD        = 9      // Start of padding bytes
};

enum ElfClass {
    ELFCLASSNONE = 0, // Invalid class
    ELFCLASS32   = 1, // 32-bit objects
    ELFCLASS64   = 2  // 64-bit objects
};

enum ElfData {
    ELFDATANONE = 0, // Invalid data encoding
    ELFDATA2LSB = 1, // Little-endian
    ELFDATA2MSB = 2  // Big-endian
};

enum ElfVersion {
    EV_NONE    = 0, // Invalid version
    EV_CURRENT = 1  // Current version
};

enum SectionType {
    SHT_NULL     = 0, // Inactive
    SHT_PROGBITS = 1, // Program data
    SHT_SYMTAB   = 2, // Symbol table
    SHT_STRTAB   = 3, // String table
    SHT_RELA     = 4, // Relocation entries, addends
    SHT_HASH     = 5, // Symbol hash table
    SHT_DYNAMIC  = 6, // Dynamic linking information
    SHT_NOTE     = 7, // Notes
    SHT_NOBITS   = 8, // Program space with no data (bss)
    SHT_REL      = 9, // Relocation entries, no addends
    SHT_SHLIB    = 10, // Reserved
    SHT_DYNSYM   = 11 // Dynamic linker symbol table
};

enum SectionIndex {
    SHN_UNDEF = 0 // Undefined section
};

// Helper for endianness conversion
template<typename T>
T swap_endian(T val) {
    if constexpr (sizeof(T) == 2) {
        return be16toh(val);
    } else if constexpr (sizeof(T) == 4) {
        return be32toh(val);
    } else if constexpr (sizeof(T) == 8) {
        return be64toh(val);
    }
    return val; // Should not happen for standard types
}

template<typename T>
T ElfParser::read_value(size_t offset) const {
    if (offset + sizeof(T) > file_.size) {
        throw std::runtime_error("Attempted to read past end of file in read_value.");
    }
    T value;
    memcpy(&value, file_.data + offset, sizeof(T));
    if (!header_.is_little_endian) { // Convert from Big Endian to Host Endian
        value = swap_endian(value);
    }
    return value;
}

ElfParser::ElfParser(const MappedFile& file) : file_(file) {
    if (file_.data == nullptr || file_.size < 64) { // Minimum ELF header size is 64 for 64-bit
        throw std::runtime_error("Invalid or empty MappedFile for ElfParser.");
    }
}

ElfParser::~ElfParser() {
    // MappedFile is managed externally, no need to unmap here.
}

bool ElfParser::parse() {
    log_info("Starting ELF parsing...");

    if (!read_elf_header()) {
        log_error("Failed to read ELF header.");
        return false;
    }
    log_info("ELF Header parsed successfully.");

    if (!read_section_headers()) {
        log_error("Failed to read section headers.");
        return false;
    }
    log_info("Section headers parsed successfully.");

    if (!resolve_section_names()) {
        log_error("Failed to resolve section names.");
        return false;
    }
    log_info("Section names resolved successfully.");

    if (!read_symbols()) {
        log_error("Failed to read symbols.");
        return false;
    }
    log_info("Symbols parsed successfully.");

    resolve_symbol_names();
    log_info("Symbol names resolved successfully.");

    return true;
}

bool ElfParser::read_elf_header() {
    // Check ELF magic
    if (memcmp(file_.data, ELFMAG, 4) != 0) {
        log_error("Not an ELF file (magic mismatch).");
        return false;
    }

    // Copy the full e_ident array
    memcpy(header_.e_ident, file_.data, 16);

    // Determine 32/64-bit and endianness from e_ident
    header_.is_64bit = (header_.e_ident[EI_CLASS] == ELFCLASS64);
    header_.is_little_endian = (header_.e_ident[EI_DATA] == ELFDATA2LSB);

    if (header_.is_64bit && file_.size < 64) { // ELF64 header size
        log_error("File too small for ELF64 header.");
        return false;
    } else if (!header_.is_64bit && file_.size < 52) { // ELF32 header size
        log_error("File too small for ELF32 header.");
        return false;
    }

    auto read_u16 = [&](size_t offset) {
        uint16_t val;
        memcpy(&val, file_.data + offset, 2);
        return header_.is_little_endian ? val : swap_endian(val);
    };
    auto read_u32 = [&](size_t offset) {
        uint32_t val;
        memcpy(&val, file_.data + offset, 4);
        return header_.is_little_endian ? val : swap_endian(val);
    };
    auto read_u64 = [&](size_t offset) {
        uint64_t val;
        memcpy(&val, file_.data + offset, 8);
        return header_.is_little_endian ? val : swap_endian(val);
    };

    header_.e_type = read_u16(0x10);
    header_.e_machine = read_u16(0x12);
    header_.e_version = read_u32(0x14);
    if (header_.is_64bit) {
        header_.e_entry = read_u64(0x18);
        header_.e_phoff = read_u64(0x20);
        header_.e_shoff = read_u64(0x28);
        header_.e_flags = read_u32(0x30);
        header_.e_ehsize = read_u16(0x34);
        header_.e_phentsize = read_u16(0x36);
        header_.e_phnum = read_u16(0x38);
        header_.e_shentsize = read_u16(0x3A);
        header_.e_shnum = read_u16(0x3C);
        header_.e_shstrndx = read_u16(0x3E);
    } else { // 32-bit
        header_.e_entry = read_u32(0x18);
        header_.e_phoff = read_u32(0x1C);
        header_.e_shoff = read_u32(0x20);
        header_.e_flags = read_u32(0x24);
        header_.e_ehsize = read_u16(0x28);
        header_.e_phentsize = read_u16(0x2A);
        header_.e_phnum = read_u16(0x2C);
        header_.e_shentsize = read_u16(0x2E);
        header_.e_shnum = read_u16(0x30);
        header_.e_shstrndx = read_u16(0x32);
    }

    // Basic validation
    if (header_.e_version != EV_CURRENT) {
        log_error("Unsupported ELF version: " + std::to_string(header_.e_version));
        return false;
    }
    if (header_.e_shentsize == 0 || header_.e_shnum == 0) {
        log_info("No section headers found or zero size.");
        return true; // Not necessarily an error for some stripped binaries
    }
    if (header_.e_shstrndx >= header_.e_shnum) {
        log_error("Invalid section header string table index.");
        return false;
    }

    return true;
}

bool ElfParser::read_section_headers() {
    if (header_.e_shoff == 0 || header_.e_shnum == 0 || header_.e_shentsize == 0) {
        log_info("No section headers to read.");
        return true;
    }

    size_t sh_table_offset = header_.e_shoff;
    size_t sh_entry_size = header_.e_shentsize;
    size_t sh_num = header_.e_shnum;

    if (sh_table_offset + sh_num * sh_entry_size > file_.size) {
        log_error("Section header table extends beyond file size.");
        return false;
    }

    section_headers_.reserve(sh_num);

    for (size_t i = 0; i < sh_num; ++i) {
        size_t current_sh_offset = sh_table_offset + i * sh_entry_size;
        SectionHeader sh;

        if (header_.is_64bit) {
            sh.sh_name = read_value<uint32_t>(current_sh_offset + 0x00);
            sh.sh_type = read_value<uint32_t>(current_sh_offset + 0x04);
            sh.sh_flags = read_value<uint64_t>(current_sh_offset + 0x08);
            sh.sh_addr = read_value<uint64_t>(current_sh_offset + 0x10);
            sh.sh_offset = read_value<uint64_t>(current_sh_offset + 0x18);
            sh.sh_size = read_value<uint64_t>(current_sh_offset + 0x20);
            sh.sh_link = read_value<uint32_t>(current_sh_offset + 0x28);
            sh.sh_info = read_value<uint32_t>(current_sh_offset + 0x2C);
            sh.sh_addralign = read_value<uint64_t>(current_sh_offset + 0x30);
            sh.sh_entsize = read_value<uint64_t>(current_sh_offset + 0x38);
        } else { // 32-bit
            sh.sh_name = read_value<uint32_t>(current_sh_offset + 0x00);
            sh.sh_type = read_value<uint32_t>(current_sh_offset + 0x04);
            sh.sh_flags = read_value<uint32_t>(current_sh_offset + 0x08);
            sh.sh_addr = read_value<uint32_t>(current_sh_offset + 0x0C);
            sh.sh_offset = read_value<uint32_t>(current_sh_offset + 0x10);
            sh.sh_size = read_value<uint32_t>(current_sh_offset + 0x14);
            sh.sh_link = read_value<uint32_t>(current_sh_offset + 0x18);
            sh.sh_info = read_value<uint32_t>(current_sh_offset + 0x1C);
            sh.sh_addralign = read_value<uint32_t>(current_sh_offset + 0x20);
            sh.sh_entsize = read_value<uint32_t>(current_sh_offset + 0x24);
        }
        section_headers_.push_back(sh);
    }
    return true;
}

bool ElfParser::resolve_section_names() {
    if (header_.e_shstrndx == SHN_UNDEF || section_headers_.empty()) {
        log_info("No section header string table or no sections to resolve names.");
        return true; // Not an error if file is stripped
    }

    const SectionHeader& shstrtab_sh = section_headers_[header_.e_shstrndx];
    if (shstrtab_sh.sh_type != SHT_STRTAB) {
        log_error("Section header string table has incorrect type.");
        return false;
    }

    if (shstrtab_sh.sh_offset + shstrtab_sh.sh_size > file_.size) {
        log_error("Section header string table extends beyond file size.");
        return false;
    }
    shstrtab_data_ = std::string_view(
        reinterpret_cast<const char*>(file_.data + shstrtab_sh.sh_offset),
        shstrtab_sh.sh_size
    );

    for (auto& sh : section_headers_) {
        if (sh.sh_name < shstrtab_data_.size()) {
            sh.name = shstrtab_data_.data() + sh.sh_name;
        } else {
            sh.name = "<invalid_name>";
            log_error("Invalid section name offset: " + std::to_string(sh.sh_name));
        }

        // Cache common string tables
        if (sh.name == ".strtab") {
            strtab_data_ = std::string_view(
                reinterpret_cast<const char*>(file_.data + sh.sh_offset), sh.sh_size
            );
        } else if (sh.name == ".dynstr") {
            dynstrtab_data_ = std::string_view(
                reinterpret_cast<const char*>(file_.data + sh.sh_offset), sh.sh_size
            );
        }
    }
    return true;
}

bool ElfParser::read_symbols() {
    for (const auto& sh : section_headers_) {
        if (sh.sh_type == SHT_SYMTAB || sh.sh_type == SHT_DYNSYM) {
            size_t sym_offset = sh.sh_offset;
            size_t sym_size = sh.sh_size;
            size_t sym_entry_size = sh.sh_entsize;

            if (sym_entry_size == 0) continue; // Should not happen for symbol tables

            size_t num_symbols = sym_size / sym_entry_size;
            if (sym_offset + sym_size > file_.size) {
                log_error("Symbol table extends beyond file size for section: " + sh.name);
                continue;
            }

            for (size_t i = 0; i < num_symbols; ++i) {
                size_t current_sym_offset = sym_offset + i * sym_entry_size;
                SymbolEntry sym;

                if (header_.is_64bit) {
                    sym.st_name = read_value<uint32_t>(current_sym_offset + 0x00);
                    sym.st_info = read_value<uint8_t>(current_sym_offset + 0x04);
                    sym.st_other = read_value<uint8_t>(current_sym_offset + 0x05);
                    sym.st_shndx = read_value<uint16_t>(current_sym_offset + 0x06);
                    sym.st_value = read_value<uint64_t>(current_sym_offset + 0x08);
                    sym.st_size = read_value<uint64_t>(current_sym_offset + 0x10);
                } else { // 32-bit
                    sym.st_name = read_value<uint32_t>(current_sym_offset + 0x00);
                    sym.st_value = read_value<uint32_t>(current_sym_offset + 0x04);
                    sym.st_size = read_value<uint32_t>(current_sym_offset + 0x08);
                    sym.st_info = read_value<uint8_t>(current_sym_offset + 0x0C);
                    sym.st_other = read_value<uint8_t>(current_sym_offset + 0x0D);
                    sym.st_shndx = read_value<uint16_t>(current_sym_offset + 0x0E);
                }
                symbols_.push_back(sym);
            }
        }
    }
    return true;
}

void ElfParser::resolve_symbol_names() {
    for (auto& sym : symbols_) {
        std::string_view* target_strtab = nullptr;

        // Determine which string table to use based on symbol binding/type or section type
        // This is a simplified heuristic; real ELF linking can be complex.
        if (sym.st_shndx < section_headers_.size() && 
            sh_type_is_dynamic(section_headers_[sym.st_shndx].sh_type)) {
             target_strtab = &dynstrtab_data_;
        } else {
            target_strtab = &strtab_data_;
        }

        if (target_strtab && !target_strtab->empty() && sym.st_name < target_strtab->size()) {
            sym.name = target_strtab->data() + sym.st_name;
        } else {
            sym.name = "<unnamed>";
        }
    }
}

const uint8_t* ElfParser::get_section_data(const std::string& section_name) const {
    for (const auto& sh : section_headers_) {
        if (sh.name == section_name) {
            if (sh.sh_offset + sh.sh_size > file_.size) {
                log_error("Section data extends beyond file bounds for: " + section_name);
                return nullptr;
            }
            return file_.data + sh.sh_offset;
        }
    }
    return nullptr;
}

size_t ElfParser::get_section_size(const std::string& section_name) const {
    for (const auto& sh : section_headers_) {
        if (sh.name == section_name) {
            return sh.sh_size;
        }
    }
    return 0;
}

uint64_t ElfParser::get_section_address(const std::string& section_name) const {
    for (const auto& sh : section_headers_) {
        if (sh.name == section_name) {
            return sh.sh_addr;
        }
    }
    return 0;
}

// Helper to check if a section type is typically associated with dynamic linking
bool sh_type_is_dynamic(uint32_t sh_type) {
    return sh_type == SHT_DYNAMIC || sh_type == SHT_DYNSYM;
}