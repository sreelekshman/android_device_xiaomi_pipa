/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/log.h>
#include <ctype.h>
#include <cutils/properties.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

const char kPackageName[] = "xiaomi-keyboard";

/********************************************
 * Configuration Constants
 ********************************************/
#define BUFFER_SIZE 256
#define NANODEV_PATH "/dev/nanodev0"
#define DEBOUNCE_COUNT 3
#define VERSION_STRING "2.0.0"

/********************************************
 * Message Protocol Definitions
 ********************************************/
#define MSG_TYPE_SLEEP 37
#define MSG_TYPE_WAKE 40
#define MSG_HEADER_1 0x31
#define MSG_HEADER_2 0x38

// Device path - found dynamically
char* EVENT_PATH = NULL;

// Logging macros
#define TAG "xiaomi-keyboard"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#define LOG_IMPORTANT(fmt, ...) do { \
    time_t now = time(NULL); \
    struct tm* tm_info = localtime(&now); \
    char time_str[20]; \
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", tm_info); \
    __android_log_print(ANDROID_LOG_INFO, TAG, "[%s] " fmt, time_str, ##__VA_ARGS__); \
} while(0)

// Global state
int fd;
bool kb_status = true;
volatile sig_atomic_t terminate = 0;
time_t last_monitor_activity = 0;

// Thread synchronization
pthread_mutex_t kb_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t kb_cond = PTHREAD_COND_INITIALIZER;
bool kb_thread_paused = false;

/**
 * Find the keyboard event input device path
 */
char* find_keyboard_input_path() {
    static char path_buffer[128] = "/dev/input/event12";
    const char* input_dir = "/dev/input";
    DIR* dir = opendir(input_dir);
    
    if (!dir) {
        LOGE("Failed to open input directory");
        return path_buffer;
    }
    
    FILE* device_file;
    char name_path[128];
    char device_name[256];
    struct dirent* entry;
    
    const char* keyboard_identifiers[] = {"xiaomi", "keyboard", "pipa", "XKBD"};
    const int num_identifiers = 4;
    
    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5) == 0) {
            snprintf(name_path, sizeof(name_path), 
                     "/sys/class/input/%s/device/name", entry->d_name);
            
            device_file = fopen(name_path, "r");
            if (device_file && fgets(device_name, sizeof(device_name), device_file)) {
                // Convert to lowercase
                for (char* p = device_name; *p; p++) {
                    *p = tolower(*p);
                }
                
                for (int i = 0; i < num_identifiers; i++) {
                    if (strstr(device_name, keyboard_identifiers[i])) {
                        snprintf(path_buffer, sizeof(path_buffer), 
                                 "/dev/input/%s", entry->d_name);
                        LOGI("Found keyboard at: %s", path_buffer);
                        fclose(device_file);
                        closedir(dir);
                        return path_buffer;
                    }
                }
                fclose(device_file);
            }
        }
    }
    
    closedir(dir);
    LOGW("Could not find keyboard device, using default path");
    return path_buffer;
}

/**
 * Set keyboard state
 */
void set_kb_state(bool value, bool force) {
    if (kb_status != value || force) {
        kb_status = value;
        LOGI("Setting keyboard state to: %d", value);
        unsigned char buf[3] = {0x32, 0xFF, (unsigned char)value};
        if (write(fd, &buf, 3) != 3) {
            LOGE("Failed to write keyboard state");
        }
    }
}

/**
 * Keyboard monitoring thread with reduced frequency
 */
void *keyboard_monitor_thread(void *arg) {
    (void)arg;
    
    int connection_state_count = 0;
    bool last_state = access(EVENT_PATH, F_OK) != -1;
    
    LOGI("Keyboard monitor thread started");
    
    while (!terminate) {
        bool current_state = (access(EVENT_PATH, F_OK) != -1);
        
        // Debounce connection state changes
        if (current_state != last_state) {
            connection_state_count++;
            LOGD("Keyboard connection change detected (%d/%d)", 
                 connection_state_count, DEBOUNCE_COUNT);
        } else {
            connection_state_count = 0;
        }
        
        // Process state change after debounce
        if (connection_state_count >= DEBOUNCE_COUNT) {
            last_state = current_state;
            connection_state_count = 0;
            
            pthread_mutex_lock(&kb_mutex);
            last_monitor_activity = time(NULL);
            
            if (!kb_thread_paused) {
                if (current_state && !kb_status) {
                    LOGI("Keyboard connected - enabling");
                    set_kb_state(true, false);
                } else if (!current_state && kb_status) {
                    LOGI("Keyboard disconnected - disabling");
                    set_kb_state(false, false);
                }
            }
            pthread_mutex_unlock(&kb_mutex);
        }
        
        // Sleep in smaller chunks for better responsiveness
        for (int i = 0; i < 5 && !terminate; i++) {
            usleep(200000); // 200ms * 5 = 1 second total
        }
    }
    
    LOGI("Keyboard monitor thread exiting");
    return NULL;
}

/**
 * Handle power events (wake/sleep)
 */
void handle_power_event(char *buffer) {
    bool is_wake = (buffer[6] == 1);
    
    pthread_mutex_lock(&kb_mutex);
    if (is_wake) {
        kb_thread_paused = false;
        last_monitor_activity = time(NULL);
        pthread_cond_signal(&kb_cond);
    } else {
        kb_thread_paused = true;
    }
    pthread_mutex_unlock(&kb_mutex);
    
    if (is_wake) {
        LOGI("Wake event - enabling keyboard monitoring");
        bool keyboard_connected = (access(EVENT_PATH, F_OK) != -1);
        LOGI("Keyboard %s on wake", keyboard_connected ? "connected" : "disconnected");
        
        if (keyboard_connected) {
            set_kb_state(true, true);
        } else {
            kb_status = false;
        }
    } else {
        LOGI("Sleep event - pausing keyboard monitoring");
    }
}

/**
 * Main event handler
 */
void handle_event(char *buffer, ssize_t bytes_read) {
    // Basic validation
    if (bytes_read < 7 || buffer[1] != MSG_HEADER_1 || buffer[2] != MSG_HEADER_2) {
        return;
    }
    
    // Handle power events
    if ((buffer[4] == MSG_TYPE_SLEEP || buffer[4] == MSG_TYPE_WAKE) && buffer[5] == 1) {
        handle_power_event(buffer);
    }
}

/**
 * Attempt to reconnect to device
 */
int reconnect_device() {
    int attempts = 0;
    const int max_attempts = 5;
    int new_fd = -1;
    
    LOGI("Starting device reconnection");
    
    while (attempts < max_attempts && new_fd == -1 && !terminate) {
        LOGI("Reconnect attempt %d/%d", attempts + 1, max_attempts);
        new_fd = open(NANODEV_PATH, O_RDWR);
        
        if (new_fd != -1) {
            LOGI("Successfully reconnected");
            return new_fd;
        }
        
        int sleep_time = (attempts < 3) ? (1 << attempts) : 4;
        sleep(sleep_time);
        attempts++;
    }
    
    LOGE("Failed to reconnect after %d attempts", attempts);
    return -1;
}

/**
 * Signal handler
 */
void signal_handler(int signum) {
    LOGI("Caught signal %d, terminating...", signum);
    terminate = 1;
}

/**
 * Cleanup resources
 */
void cleanup_resources(pthread_t monitor_thread) {
    LOGI("Performing cleanup...");
    
    pthread_mutex_lock(&kb_mutex);
    terminate = 1;
    pthread_cond_signal(&kb_cond);
    pthread_mutex_unlock(&kb_mutex);
    
    pthread_join(monitor_thread, NULL);
    
    if (fd != -1) {
        close(fd);
        fd = -1;
    }
}

/**
 * Main function
 */
int main() {
    time_t start_time = time(NULL);
    struct tm* tm_info = localtime(&start_time);
    char time_str[64];
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", tm_info);
    
    LOG_IMPORTANT("Xiaomi keyboard service v%s starting at %s", VERSION_STRING, time_str);
    LOG_IMPORTANT("This service is user-controlled via Settings > Connected devices");

    // Find keyboard device
    EVENT_PATH = find_keyboard_input_path();
    LOGI("Using keyboard input path: %s", EVENT_PATH);

    // Open nanodev
    fd = open(NANODEV_PATH, O_RDWR);
    if (fd == -1) {
        LOGE("Error opening nanodev device: %s", strerror(errno));
        return errno;
    }

    // Check initial keyboard status
    if (access(EVENT_PATH, F_OK) == -1) {
        kb_status = false;
        LOGW("Keyboard not found, starting disabled");
    } else {
        LOGI("Keyboard found, starting enabled");
        set_kb_state(true, true);
    }

    // Create monitor thread
    pthread_t monitor_thread;
    if (pthread_create(&monitor_thread, NULL, keyboard_monitor_thread, NULL) != 0) {
        LOGE("Failed to create monitor thread");
        close(fd);
        return EXIT_FAILURE;
    }

    // Setup signal handling
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);

    // Main event loop
    ssize_t bytes_read;
    char buffer[BUFFER_SIZE];
    int recoveries = 0;
    const int MAX_RECOVERIES = 3;

    LOGI("Main loop starting");
    while (!terminate) {
        bytes_read = read(fd, buffer, BUFFER_SIZE);
        
        if (bytes_read > 0) {
            recoveries = 0;
            handle_event(buffer, bytes_read);
        } 
        else if (bytes_read == 0) {
            usleep(100000); // 100ms
        } 
        else {
            LOGE("Error reading device: %s", strerror(errno));
            
            if (++recoveries > MAX_RECOVERIES) {
                LOGE("Exceeded maximum recovery attempts");
                break;
            }
            
            close(fd);
            fd = reconnect_device();
            
            if (fd == -1) {
                LOGE("Could not recover device connection");
                break;
            }
        }
    }

    // Exit status
    time_t end_time = time(NULL);
    double runtime = difftime(end_time, start_time);
    LOGI("Service exiting after %.1f seconds", runtime);

    cleanup_resources(monitor_thread);
    return 0;
}
