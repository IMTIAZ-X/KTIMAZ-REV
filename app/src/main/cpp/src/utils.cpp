#include "../include/utils.h"
#include <android/log.h> // For Android logging
#include <sys/mman.h>    // For mmap, munmap
#include <sys/stat.h>    // For stat
#include <fcntl.h>       // For open, O_RDONLY
#include <unistd.h>      // For close
#include <stdexcept>     // For std::runtime_error
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>

// Android log tags
#define LOG_TAG "NativeDisassembler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MappedFile map_file(const std::string& file_path) {
    MappedFile mapped_file = {nullptr, 0, -1};

    int fd = open(file_path.c_str(), O_RDONLY);
    if (fd == -1) {
        log_error("Failed to open file: " + file_path);
        return mapped_file;
    }

    struct stat st;
    if (fstat(fd, &st) == -1) {
        log_error("Failed to get file size for: " + file_path);
        close(fd);
        return mapped_file;
    }

    if (st.st_size == 0) {
        log_info("File is empty: " + file_path);
        close(fd);
        return mapped_file;
    }

    void* addr = mmap(nullptr, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (addr == MAP_FAILED) {
        log_error("Failed to mmap file: " + file_path);
        close(fd);
        return mapped_file;
    }

    mapped_file.data = static_cast<const uint8_t*>(addr);
    mapped_file.size = st.st_size;
    mapped_file.fd = fd; // Keep the file descriptor open while mapped
    log_info("Successfully mmap'd file: " + file_path + ", size: " + std::to_string(st.st_size) + " bytes");
    return mapped_file;
}

void unmap_file(MappedFile& mapped_file) {
    if (mapped_file.data != nullptr) {
        if (munmap(const_cast<uint8_t*>(mapped_file.data), mapped_file.size) == -1) {
            log_error("Failed to munmap file.");
        }
        mapped_file.data = nullptr;
        mapped_file.size = 0;
    }
    if (mapped_file.fd != -1) {
        close(mapped_file.fd);
        mapped_file.fd = -1;
    }
    log_info("File unmapped and closed.");
}

void log_error(const std::string& message) {
    LOGE("%s", message.c_str());
}

void log_info(const std::string& message) {
    LOGI("%s", message.c_str());
}

std::string jstring_to_cpp_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* c_str = env->GetStringUTFChars(jstr, nullptr);
    std::string cpp_str(c_str);
    env->ReleaseStringUTFChars(jstr, c_str);
    return cpp_str;
}

jstring cpp_string_to_jstring(JNIEnv* env, const std::string& cpp_str) {
    return env->NewStringUTF(cpp_str.c_str());
}

// SimpleThreadPool implementation
SimpleThreadPool::SimpleThreadPool(int num_threads) : stop(false) {
    for (int i = 0; i < num_threads; ++i) {
        workers.emplace_back([this] {
            while (true) {
                std::function<void()> task;
                {
                    std::unique_lock<std::mutex> lock(this->queue_mutex);
                    this->condition.wait(lock, [this] { return this->stop || !this->tasks.empty(); });
                    if (this->stop && this->tasks.empty()) {
                        return;
                    }
                    task = this->tasks.front();
                    this->tasks.pop();
                }
                task();
            }
        });
    }
    LOGI("SimpleThreadPool created with %d threads.", num_threads);
}

SimpleThreadPool::~SimpleThreadPool() {
    shutdown();
}

void SimpleThreadPool::enqueue(std::function<void()> task) {
    {
        std::unique_lock<std::mutex> lock(queue_mutex);
        if (stop) {
            log_error("enqueue on stopped ThreadPool");
            return;
        }
        tasks.push(std::move(task));
    }
    condition.notify_one();
}

void SimpleThreadPool::shutdown() {
    {
        std::unique_lock<std::mutex> lock(queue_mutex);
        stop = true;
    }
    condition.notify_all();
    for (std::thread& worker : workers) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    LOGI("SimpleThreadPool shut down.");
}