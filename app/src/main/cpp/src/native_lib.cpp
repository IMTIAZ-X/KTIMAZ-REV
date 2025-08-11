#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <android/log.h>

#include "../include/utils.h"
#include "../include/elf_parser.h"
#include "../include/arm_disassembler.h"

// Android log tags
#define LOG_TAG_JNI "NativeDisassemblerJNI"
#define LOGI_JNI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_JNI, __VA_ARGS__)
#define LOGE_JNI(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_JNI, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static std::unique_ptr<SimpleThreadPool> g_thread_pool;
static std::unique_ptr<ElfParser> g_elf_parser;
static std::unique_ptr<ArmDisassembler> g_arm_disassembler;
static MappedFile g_mapped_file;
static std::mutex g_parser_mutex;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    LOGI_JNI("JNI_OnLoad called.");
    
    g_thread_pool = std::make_unique<SimpleThreadPool>(4);
    if (g_thread_pool == nullptr) {
        LOGE_JNI("Failed to initialize global thread pool!");
        return JNI_ERR;
    }
    
    return JNI_VERSION_1_6;
}

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
Java_com_imtiaz_ktimazrev_viewmodel_FileLoaderViewModel_loadFileAndParseNative(
    JNIEnv* env,
    jobject thiz,
    jstring j_file_path) {

    std::string file_path = jstring_to_cpp_string(env, j_file_path);
    LOGI_JNI("Loading file: %s", file_path.c_str());

    jclass cls = env->GetObjectClass(thiz);
    jmethodID onParsingStartedMethod = env->GetMethodID(cls, "onParsingStarted", "()V");
    jmethodID onParsingProgressMethod = env->GetMethodID(cls, "onParsingProgress", "(I)V");
    jmethodID onParsingFinishedMethod = env->GetMethodID(cls, "onParsingFinished", "(Z)V");
    jmethodID onFileReadErrorMethod = env->GetMethodID(cls, "onFileReadError", "(Ljava/lang/String;)V");

    if (!onParsingStartedMethod || !onParsingProgressMethod || !onParsingFinishedMethod || !onFileReadErrorMethod) {
        LOGE_JNI("Failed to find ViewModel callback methods!");
        return;
    }

    env->CallVoidMethod(thiz, onParsingStartedMethod);

    if (g_thread_pool) {
        g_thread_pool->enqueue([=]() {
            JNIEnv* current_env;
            bool attached = false;
            if (g_vm->GetEnv(reinterpret_cast<void**>(&current_env), JNI_VERSION_1_6) != JNI_OK) {
                if (g_vm->AttachCurrentThread(&current_env, nullptr) != JNI_OK) {
                    LOGE_JNI("Failed to attach thread!");
                    return;
                }
                attached = true;
            }

            bool success = false;
            std::string error_message = "";
            
            {
                std::lock_guard<std::mutex> lock(g_parser_mutex);
                try {
                    if (g_mapped_file.data) {
                        unmap_file(g_mapped_file);
                    }
                    
                    g_mapped_file = map_file(file_path);
                    if (g_mapped_file.data == nullptr) {
                        throw std::runtime_error("Failed to map file: " + file_path);
                    }
                    
                    current_env->CallVoidMethod(thiz, onParsingProgressMethod, 30);
                    
                    g_elf_parser = std::make_unique<ElfParser>(g_mapped_file);
                    if (!g_elf_parser->parse()) {
                        throw std::runtime_error("ELF parsing failed");
                    }
                    
                    current_env->CallVoidMethod(thiz, onParsingProgressMethod, 70);
                    
                    g_arm_disassembler = std::make_unique<ArmDisassembler>();
                    
                    current_env->CallVoidMethod(thiz, onParsingProgressMethod, 100);
                    success = true;
                    
                } catch (const std::exception& e) {
                    LOGE_JNI("Parsing error: %s", e.what());
                    error_message = e.what();
                    success = false;
                }
            }

            if (success) {
                current_env->CallVoidMethod(thiz, onParsingFinishedMethod, JNI_TRUE);
            } else {
                current_env->CallVoidMethod(thiz, onParsingFinishedMethod, JNI_FALSE);
                current_env->CallVoidMethod(thiz, onFileReadErrorMethod, 
                    cpp_string_to_jstring(current_env, error_message));
            }

            if (attached) {
                g_vm->DetachCurrentThread();
            }
        });
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_imtiaz_ktimazrev_viewmodel_DisassemblyViewModel_getDisassembledInstructionsNative(
    JNIEnv* env,
    jobject thiz,
    jstring j_section_name,
    jlong j_base_address,
    jboolean j_is_thumb_mode) {

    std::string section_name = jstring_to_cpp_string(env, j_section_name);
    uint64_t base_address = static_cast<uint64_t>(j_base_address);
    bool is_thumb_mode = static_cast<bool>(j_is_thumb_mode);

    std::vector<DisassembledInstruction> instructions;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser || !g_arm_disassembler) {
            LOGE_JNI("Parser not initialized");
            return nullptr;
        }

        const uint8_t* section_data = g_elf_parser->get_section_data(section_name);
        size_t section_size = g_elf_parser->get_section_size(section_name);
        
        if (section_data == nullptr || section_size == 0) {
            LOGE_JNI("Section not found: %s", section_name.c_str());
            return nullptr;
        }

        instructions = g_arm_disassembler->disassemble_block(
            section_data, section_size, base_address, is_thumb_mode);
    }

    jclass instruction_class = env->FindClass("com/imtiaz/ktimazrev/model/Instruction");
    if (!instruction_class) {
        LOGE_JNI("Failed to find Instruction class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(instruction_class, "<init>", 
        "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;JIZJ)V");
    if (!constructor) {
        LOGE_JNI("Failed to find Instruction constructor");
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(instructions.size(), instruction_class, nullptr);
    if (!result) return nullptr;

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
            static_cast<jint>(4),
            static_cast<jboolean>(instr.is_branch),
            static_cast<jlong>(instr.branch_target));

        if (java_instr) {
            env->SetObjectArrayElement(result, i, java_instr);
            env->DeleteLocalRef(java_instr);
        }
        
        env->DeleteLocalRef(j_mnemonic);
        env->DeleteLocalRef(j_operands);
        env->DeleteLocalRef(j_comment);
    }

    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_imtiaz_ktimazrev_viewmodel_FileLoaderViewModel_getElfSectionNamesNative(
    JNIEnv* env,
    jobject thiz) {

    std::vector<std::string> section_names;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized");
            return nullptr;
        }
        
        for (const auto& sh : g_elf_parser->get_section_headers()) {
            if (!sh.name.empty() && sh.name != "<invalid_name>") {
                section_names.push_back(sh.name);
            }
        }
    }

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(section_names.size(), string_class, nullptr);

    for (size_t i = 0; i < section_names.size(); ++i) {
        jstring j_str = cpp_string_to_jstring(env, section_names[i]);
        env->SetObjectArrayElement(result, i, j_str);
        env->DeleteLocalRef(j_str);
    }

    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_imtiaz_ktimazrev_viewmodel_FileLoaderViewModel_getElfSymbolsNative(
    JNIEnv* env,
    jobject thiz) {

    std::vector<SymbolEntry> symbols;
    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized");
            return nullptr;
        }
        symbols = g_elf_parser->get_symbols();
    }

    jclass symbol_class = env->FindClass("com/imtiaz/ktimazrev/model/Symbol");
    if (!symbol_class) {
        LOGE_JNI("Failed to find Symbol class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(symbol_class, "<init>", 
        "(Ljava/lang/String;JJLjava/lang/String;)V");
    if (!constructor) {
        LOGE_JNI("Failed to find Symbol constructor");
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(symbols.size(), symbol_class, nullptr);

    for (size_t i = 0; i < symbols.size(); ++i) {
        const auto& sym = symbols[i];
        
        jstring j_name = cpp_string_to_jstring(env, sym.name);
        jstring j_section = cpp_string_to_jstring(env, 
            sym.st_shndx < g_elf_parser->get_section_headers().size() ? 
            g_elf_parser->get_section_headers()[sym.st_shndx].name : "unknown");

        jobject java_sym = env->NewObject(symbol_class, constructor,
            j_name,
            static_cast<jlong>(sym.st_value),
            static_cast<jlong>(sym.st_size),
            j_section);

        if (java_sym) {
            env->SetObjectArrayElement(result, i, java_sym);
            env->DeleteLocalRef(java_sym);
        }
        
        env->DeleteLocalRef(j_name);
        env->DeleteLocalRef(j_section);
    }

    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_imtiaz_ktimazrev_viewmodel_DisassemblyViewModel_getHexDumpNative(
    JNIEnv* env,
    jobject thiz,
    jstring j_section_name,
    jlong j_offset,
    jint j_length) {

    std::string section_name = jstring_to_cpp_string(env, j_section_name);
    size_t offset = static_cast<size_t>(j_offset);
    size_t length = static_cast<size_t>(j_length);

    {
        std::lock_guard<std::mutex> lock(g_parser_mutex);
        if (!g_elf_parser) {
            LOGE_JNI("ELF parser not initialized");
            return nullptr;
        }

        const uint8_t* section_data = g_elf_parser->get_section_data(section_name);
        size_t section_size = g_elf_parser->get_section_size(section_name);

        if (section_data == nullptr || section_size == 0) {
            LOGE_JNI("Section not found: %s", section_name.c_str());
            return env->NewByteArray(0);
        }

        size_t bytes_to_read = std::min(length, section_size - offset);
        if (offset >= section_size) {
            return env->NewByteArray(0);
        }

        jbyteArray result = env->NewByteArray(bytes_to_read);
        if (result) {
            env->SetByteArrayRegion(result, 0, bytes_to_read, 
                reinterpret_cast<const jbyte*>(section_data + offset));
        }
        return result;
    }
}