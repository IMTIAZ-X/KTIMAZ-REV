#ifndef MOBILE_ARM_DISASSEMBLER_UTILS_H
#define MOBILE_ARM_DISASSEMBLER_UTILS_H

#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <memory>
#include <functional>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>

// Simple struct to hold memory-mapped file data
struct MappedFile {
    const uint8_t* data;
    size_t size;
    int fd; // File descriptor
};

// Function to memory-map a file
MappedFile map_file(const std::string& file_path);

// Function to unmap a file
void unmap_file(MappedFile& mapped_file);

// Basic error logging for native code
void log_error(const std::string& message);
void log_info(const std::string& message);

// Converts a wide character string to UTF-8 (Android JNI specific)
std::string jstring_to_cpp_string(JNIEnv* env, jstring jstr);

// Converts a C++ string to Java string (Android JNI specific)
jstring cpp_string_to_jstring(JNIEnv* env, const std::string& cpp_str);

// Simple thread pool implementation
class SimpleThreadPool {
public:
    SimpleThreadPool(int num_threads = 4);
    ~SimpleThreadPool();

    void enqueue(std::function<void()> task);
    void shutdown();

private:
    std::vector<std::thread> workers;
    std::queue<std::function<void()>> tasks;
    std::mutex queue_mutex;
    std::condition_variable condition;
    bool stop;
};

#endif //MOBILE_ARM_DISASSEMBLER_UTILS_H