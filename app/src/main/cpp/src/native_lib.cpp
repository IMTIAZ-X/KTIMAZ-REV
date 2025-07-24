#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <android/log.h> // For Android logging

#include "../include/utils.h"
#include "../include/elf_parser.h"
#include "../include/arm_disassembler.h"

// Android log tags
#define LOG_TAG_JNI "NativeDisassemblerJNI"
#define LOGI_JNI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_JNI, __VA_ARGS__)
#define LOGE_JNI(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_JNI, __VA_ARGS__)

// Global references for JNIEnv (not usually recommended for long-term storage,
// but for static library init and persistent objects, it's necessary with care).
// Better practice for thread management: obtain JNIEnv for current thread if needed.
static JavaVM* g_vm = nullptr;

// Global instance of the thread pool
static std::unique_ptr<SimpleThreadPool> g_thread_pool;

// Global instance of the ELF parser and disassembler (managed here for simplicity)
static std::unique_ptr<ElfParser> g_elf_parser;
static std::unique_ptr<ArmDisassembler> g_arm_disassembler;
static MappedFile g_mapped_file;
static std::mutex g_parser_mutex; // Protects g_elf_parser and g_arm_disassembler access

// JNI_OnLoad is called when the native library is loaded by System.loadLibrary()
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    LOGI_JNI("JNI_OnLoad called.");

    // Initialize the global thread pool
    g_thread_pool = std::make_unique<SimpleThreadPool>(std::thread::hardware_concurrency().load() / 2); // Use half logical cores
    if (g_thread_pool == nullptr) {
        LOGE_JNI("Failed to initialize global thread pool!");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6; // Or JNI_VERSION_1_4, JNI_VERSION_1_8 etc.
}

// JNI_OnUnload is called when the native library is unloaded
extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI_JNI("JNI_OnUnload called.");
    if (g_thread_pool) {
        g_thread_pool->shutdown();
        g_thread_pool.reset();
    }
    std::lock_guard<std::mutex> lock(g_parser_mutex);
    if (g_elf_parser) {
        g_elf_parser.reset();
    }
    if (g_arm_disassembler) {
        g_arm_disassembler.reset();
    }
    if (g_mapped_file.data) {
        unmap_file(g_mapped_file);
    }
    g_vm = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_disassembler_viewmodel_FileLoaderViewModel_loadFileAndParseNative(
    JNIEnv* env,
    jobject thiz, /* FileLoaderViewModel instance */
    jstring j_file_path) {

    std::string file_path = jstring_to_cpp_string(env, j_file_path);
    LOGI_JNI("Received request to load and parse: %s", file_path.c_str());

    // Get a reference to the Kotlin ViewModel for callbacks
    jclass cls = env->GetObjectClass(thiz);
    jmethodID onParsingStartedMethod = env->GetMethodID(cls, "onParsingStarted", "()V");
    jmethodID onParsingProgressMethod = env->GetMethodID(cls, "onParsingProgress", "(I)V");
    jmethodID onParsingFinishedMethod = env->GetMethodID(cls, "onParsingFinished", "(Z)V");
    jmethodID onFileReadErrorMethod = env->GetMethodID(cls, "onFileReadError", "(Ljava/lang/String;)V");

    if (!onParsingStartedMethod || !onParsingProgressMethod || !onParsingFinishedMethod || !onFileReadErrorMethod) {
        LOGE_JNI("Failed to find Kotlin ViewModel callback methods!");
        return;
    }

    // Call onParsingStarted immediately
    env->CallVoidMethod(thiz, onParsingStartedMethod);

    // Enqueue the parsing task to the thread pool
    if (g_thread_pool) {
        g_thread_pool->enqueue([env, thiz, file_path,
                                onParsingStartedMethod, onParsingProgressMethod,
                                onParsingFinishedMethod, onFileReadErrorMethod]() {

            JNIEnv* current_env;
            // Attach current thread to JVM if not already attached
            bool attached = false;
            if (g_vm->GetEnv(reinterpret_cast<void**>(&current_env), JNI_VERSION_1_6) != JNI_OK) {
                if (g_vm->AttachCurrentThread(&current_env, nullptr) != JNI_OK) {
                    LOGE_JNI("Failed to attach current thread to JVM!");
                    // Callback Java error
                    current_env->CallVoidMethod(thiz, onFileReadErrorMethod, cpp_string_to_jstring(current_env, "Failed to attach thread for parsing."));
                    return;
                }
                attached = true;
            }

            // Perform parsing
            bool success = false;
            std::string error_message = "";
            {
                std::lock_guard<std::mutex> lock(g_parser_mutex); // Protect global parser objects
                try {
                    if (g_mapped_file.data) { // Unmap previous file if any
                        unmap_file(g_mapped_file);
                    }
                    g_mapped_file = map_file(file_path);
                    if (g_mapped_file.data == nullptr) {
                        error_message = "Failed to map file for parsing: " + file_path;
                        throw std::runtime_error(error_message);
                    }
                    current_env->CallVoidMethod(thiz, onParsingProgressMethod, 10); // Update progress
                    g_elf_parser = std::make_unique<ElfParser>(g_mapped_file);
                    if (!g_elf_parser->parse()) {
                        error_message = "ELF parsing failed for: " + file_path;
                        throw std::runtime_error(error_message);
                    }
                    current_env->CallVoidMethod(thiz, onParsingProgressMethod, 50); // Update progress

                    // Initialize disassembler only if ELF parsing was successful
                    g_arm_disassembler = std::make_unique<ArmDisassembler>();

                    success = true;
                } catch (const std::exception& e) {
                    LOGE_JNI("Error during parsing: %s", e.what());
                    error_message = "Error during parsing: " + std::string(e.what());
                    success = false;
                }
            } // End lock_guard

            // Call appropriate callback
            if (success) {
                current_env->CallVoidMethod(thiz, onParsingFinishedMethod, JNI_TRUE);
            } else {
                current_env->CallVoidMethod(thiz, onParsingFinishedMethod, JNI_FALSE);
                current_env->CallVoidMethod(thiz, onFileReadErrorMethod, cpp_string_to_jstring(current_env, error_message));
            }

            // Detach current thread from JVM if it was attached here
            if (attached) {
                g_vm->DetachCurrentThread();
            }
        });
    } else {
        LOGE_JNI("Thread pool not initialized!");
        env->CallVoidMethod(thiz, onFileReadErrorMethod, cpp_string_to_jstring(env, "Native thread pool not ready."));
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_disassembler_viewmodel_DisassemblyViewModel_getDisassembledInstructionsNative(
    JNIEnv* env,
    jobject thiz, /* DisassemblyViewModel instance */
    jstring j_section_name,
    jlong j_base_address,
    jboolean j_is_thumb_mode) {

    std::vector<DisassembledInstruction> instructions;
    std::string section_name = jstring_to_cpp_string(env, j_section_name);
    uint64_t base_address = static_cast<uint64_t>(j_base_address);
    bool is_thumb_mode = static_cast<bool>(j_is_thumb_mode);

    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser || !g_arm_disassembler) {
            LOGE_JNI("Parser or disassembler not initialized.");
            return nullptr;
        }

        const uint8_t* section_data = g_elf_parser->get_section_data(section_name);
        size_t section_size = g_elf_parser->get_section_size(section_name);
        if (section_data == nullptr || section_size == 0) {
            LOGE_JNI("Section data not found or empty for section: %s", section_name.c_str());
            return nullptr;
        }

        instructions = g_arm_disassembler->disassemble_block(
            section_data, section_size, base_address, is_thumb_mode);
    }

    // Convert C++ vector of DisassembledInstruction to Java array of Instruction objects
    jclass instruction_class = env->FindClass("com/example/disassembler/model/Instruction");
    if (!instruction_class) {
        LOGE_JNI("Failed to find Instruction Java class.");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(instruction_class, "<init>", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;JIZZ)V");
    if (!constructor) {
        LOGE_JNI("Failed to find Instruction constructor.");
        return nullptr;
    }

    jobjectArray java_instructions_array = env->NewObjectArray(
        instructions.size(), instruction_class, nullptr);
    if (!java_instructions_array) {
        LOGE_JNI("Failed to create Java Instruction array.");
        return nullptr;
    }

    for (size_t i = 0; i < instructions.size(); ++i) {
        const auto& instr = instructions[i];
        jstring j_mnemonic = cpp_string_to_jstring(env, instr.mnemonic);
        jstring j_operands = cpp_string_to_jstring(env, instr.operands);
        jstring j_comment = cpp_string_to_jstring(env, instr.comment);

        jobject java_instr = env->NewObject(instruction_class, constructor,
                                            static_cast<jlong>(instr.address),
                                            j_mnemonic,
                                            j_operands,
                                            j_comment,
                                            static_cast<jlong>(instr.bytes),
                                            static_cast<jint>(4), // Assuming 4 bytes for raw, or calculate actual
                                            static_cast<jboolean>(instr.is_branch),
                                            static_cast<jlong>(instr.branch_target));

        if (!java_instr) {
            LOGE_JNI("Failed to create Java Instruction object at index %zu.", i);
            // Clean up resources if an error occurs mid-loop
            for (size_t j = 0; j < i; ++j) {
                env->DeleteLocalRef(env->GetObjectArrayElement(java_instructions_array, j));
            }
            env->DeleteLocalRef(java_instructions_array);
            return nullptr;
        }
        env->SetObjectArrayElement(java_instructions_array, i, java_instr);
        env->DeleteLocalRef(java_instr); // Release local reference
    }

    return java_instructions_array;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_disassembler_viewmodel_DisassemblyViewModel_getElfSectionNamesNative(
    JNIEnv* env,
    jobject thiz) {

    std::vector<std::string> section_names;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized for getElfSectionNamesNative.");
            return nullptr;
        }
        for (const auto& sh : g_elf_parser->get_section_headers()) {
            if (!sh.name.empty()) { // Only return named sections
                section_names.push_back(sh.name);
            }
        }
    }

    jclass string_class = env->FindClass("java/lang/String");
    if (!string_class) {
        LOGE_JNI("Failed to find String Java class.");
        return nullptr;
    }

    jobjectArray java_string_array = env->NewObjectArray(
        section_names.size(), string_class, nullptr);
    if (!java_string_array) {
        LOGE_JNI("Failed to create Java String array.");
        return nullptr;
    }

    for (size_t i = 0; i < section_names.size(); ++i) {
        env->SetObjectArrayElement(java_string_array, i, cpp_string_to_jstring(env, section_names[i]));
    }

    return java_string_array;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_disassembler_viewmodel_DisassemblyViewModel_getElfSymbolsNative(
    JNIEnv* env,
    jobject thiz) {

    std::vector<SymbolEntry> symbols;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized for getElfSymbolsNative.");
            return nullptr;
        }
        symbols = g_elf_parser->get_symbols();
    }

    jclass symbol_class = env->FindClass("com/example/disassembler/model/Symbol");
    if (!symbol_class) {
        LOGE_JNI("Failed to find Symbol Java class.");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(symbol_class, "<init>", "(Ljava/lang/String;JJLjava/lang/String;)V");
    if (!constructor) {
        LOGE_JNI("Failed to find Symbol constructor.");
        return nullptr;
    }

    jobjectArray java_symbols_array = env->NewObjectArray(
        symbols.size(), symbol_class, nullptr);
    if (!java_symbols_array) {
        LOGE_JNI("Failed to create Java Symbol array.");
        return nullptr;
    }

    for (size_t i = 0; i < symbols.size(); ++i) {
        const auto& sym = symbols[i];
        jstring j_name = cpp_string_to_jstring(env, sym.name);
        jstring j_section_name = cpp_string_to_jstring(env, g_elf_parser->get_section_headers()[sym.st_shndx].name);

        jobject java_sym = env->NewObject(symbol_class, constructor,
                                            j_name,
                                            static_cast<jlong>(sym.st_value),
                                            static_cast<jlong>(sym.st_size),
                                            j_section_name);

        if (!java_sym) {
            LOGE_JNI("Failed to create Java Symbol object at index %zu.", i);
            for (size_t j = 0; j < i; ++j) {
                env->DeleteLocalRef(env->GetObjectArrayElement(java_symbols_array, j));
            }
            env->DeleteLocalRef(java_symbols_array);
            return nullptr;
        }
        env->SetObjectArrayElement(java_symbols_array, i, java_sym);
        env->DeleteLocalRef(java_sym); // Release local reference
    }

    return java_symbols_array;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_disassembler_viewmodel_DisassemblyViewModel_getHexDumpNative(
    JNIEnv* env,
    jobject thiz, /* DisassemblyViewModel instance */
    jstring j_section_name,
    jlong j_offset,
    jint j_length) {

    std::string section_name = jstring_to_cpp_string(env, j_section_name);
    size_t offset = static_cast<size_t>(j_offset);
    size_t length = static_cast<size_t>(j_length);

    jbyteArray result = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized for getHexDumpNative.");
            return nullptr;
        }

        const uint8_t* section_data = g_elf_parser->get_section_data(section_name);
        size_t section_size = g_elf_parser->get_section_size(section_name);

        if (section_data == nullptr || section_size == 0) {
            LOGE_JNI("Section data not found or empty for section: %s", section_name.c_str());
            return nullptr;
        }

        // Ensure we don't read past the end of the section
        size_t bytes_to_read = std::min(length, section_size - offset);
        if (offset >= section_size || bytes_to_read == 0) {
            LOGI_JNI("Hex dump request out of bounds or zero length for section: %s, offset: %zu, size: %zu",
                     section_name.c_str(), offset, section_size);
            return env->NewByteArray(0); // Return empty array
        }

        result = env->NewByteArray(bytes_to_read);
        if (result) {
            env->SetByteArrayRegion(result, 0, bytes_to_read, reinterpret_cast<const jbyte*>(section_data + offset));
        } else {
            LOGE_JNI("Failed to allocate new byte array for hex dump.");
        }
    }
    return result;
}