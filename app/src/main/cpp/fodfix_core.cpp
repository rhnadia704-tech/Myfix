#include "fodfix_core.h"

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <atomic>
#include <chrono>
#include <map>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "libfodfix"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char* kVersion = "1.1.0-arm64";

std::atomic<bool> g_live_running{false};
std::thread g_live_thread;
std::mutex g_live_mutex;

struct FodNodeCandidate {
    const char* label;
    const char* path;
};

constexpr FodNodeCandidate kFodSysfsNodes[] = {
    {"drm_disp_param", "/sys/class/drm/card0-DSI-1/disp_param"},
    {"drm_hbm", "/sys/class/drm/card0-DSI-1/hbm"},
    {"drm_fod_ui", "/sys/class/drm/card0-DSI-1/fod_ui_ready"},
    {"fp_fod_status", "/sys/devices/platform/soc/soc:fingerprint_fod/fod_status"},
    {"touch_fod_press", "/sys/devices/virtual/touch/touch_dev/fod_press_status"},
    {"touch_fod_gesture", "/sys/class/touch/touch_dev/fod_longpress_gesture_enabled"},
    {"goodix_fod_status", "/sys/devices/platform/goodix_ts.0/fod_status"},
    {"oplus_hbm", "/sys/kernel/oplus_display/hbm"},
    {"oplus_dimlayer", "/sys/kernel/oplus_display/dimlayer_bl_en"},
    {"sec_fod_pos", "/sys/class/sec/tsp/fod_pos"},
    {"sec_fp_type", "/sys/class/fingerprint/fingerprint/type_check"},
    {"xiaomi_fod_ui", "/sys/devices/virtual/mi_display/disp_feature/disp-DSI-0/fod_ui_ready"},
    {"mi_disp_hbm", "/sys/devices/virtual/mi_display/disp_feature/disp-DSI-0/hbm_mode"}
};

constexpr const char* kFpDevNodes[] = {
    "/dev/goodix_fp",
    "/dev/silead_fp",
    "/dev/esfp0",
    "/dev/qbt2000",
    "/dev/qbt1000",
    "/dev/fpc1020",
    "/dev/focaltech_fp"
};

constexpr const char* kFodProps[] = {
    "ro.board.platform",
    "ro.hardware",
    "ro.hardware.fingerprint",
    "ro.vendor.build.fingerprint",
    "persist.vendor.sys.fp.fod.location.X_Y",
    "persist.vendor.sys.fp.fod.size.width_height",
    "ro.hardware.fp.fod",
    "sys.fp.fod.status",
    "vendor.sys.fp.fod.hbm",
    "sys.myfix.bridge.ready"
};

std::string get_sys_prop(const char* key) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    if (len > 0) {
        return std::string(value, static_cast<size_t>(len));
    }
    return "";
}

bool contains_ci(const std::string& haystack, const char* needle) {
    if (!needle || !*needle) return false;
    size_t nlen = strlen(needle);
    if (haystack.size() < nlen) return false;
    for (size_t i = 0; i <= haystack.size() - nlen; ++i) {
        bool match = true;
        for (size_t j = 0; j < nlen; ++j) {
            char a = haystack[i + j];
            char b = needle[j];
            if (a >= 'A' && a <= 'Z') a = static_cast<char>(a - 'A' + 'a');
            if (b >= 'A' && b <= 'Z') b = static_cast<char>(b - 'A' + 'a');
            if (a != b) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}

bool is_fod_or_fp_candidate(const std::string& name) {
    static const char* kPatterns[] = {
        "fingerprint", "fod", "udfps", "biometrics",
        "goodix", "gf_hal", "silead", "focal", "egis", "qbt", "fpc"
    };
    for (const char* pat : kPatterns) {
        if (contains_ci(name, pat)) return true;
    }
    return false;
}

std::vector<std::string> scan_dir_for_hal(const char* dir_path) {
    std::vector<std::string> found;
    DIR* dir = opendir(dir_path);
    if (!dir) return found;
    struct dirent* entry = nullptr;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        std::string name(entry->d_name);
        if (is_fod_or_fp_candidate(name)) {
            found.push_back(std::string(dir_path) + "/" + name);
        }
    }
    closedir(dir);
    return found;
}

std::string read_small_file(const char* path) {
    int fd = open(path, O_RDONLY | O_NONBLOCK);
    if (fd < 0) return "";
    char buf[256] = {0};
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return "";
    std::string val(buf, static_cast<size_t>(n));
    while (!val.empty() && (val.back() == '\n' || val.back() == '\r' || val.back() == ' ')) {
        val.pop_back();
    }
    return val;
}

std::map<std::string, std::string> scan_fp_hal_processes() {
    std::map<std::string, std::string> procs;
    DIR* proc_dir = opendir("/proc");
    if (!proc_dir) return procs;
    struct dirent* entry = nullptr;
    while ((entry = readdir(proc_dir)) != nullptr) {
        if (entry->d_name[0] < '0' || entry->d_name[0] > '9') continue;
        std::string cmd_path = std::string("/proc/") + entry->d_name + "/cmdline";
        int fd = open(cmd_path.c_str(), O_RDONLY);
        if (fd < 0) continue;
        char buf[256] = {0};
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        for (ssize_t i = 0; i < n - 1; ++i) {
            if (buf[i] == '\0') buf[i] = ' ';
        }
        std::string cmdline(buf);
        if (is_fod_or_fp_candidate(cmdline) &&
            !contains_ci(cmdline, "com.aistudio.myfix") &&
            !contains_ci(cmdline, "logcat")) {
            procs[entry->d_name] = cmdline;
        }
    }
    closedir(proc_dir);
    return procs;
}

void write_to_buf(const std::string& text, char* out_buf, size_t out_buf_size) {
    if (!out_buf || out_buf_size == 0) return;
    size_t copy_len = text.size() < (out_buf_size - 1) ? text.size() : (out_buf_size - 1);
    memcpy(out_buf, text.data(), copy_len);
    out_buf[copy_len] = '\0';
}

void emit_line(fodfix_log_callback_t cb, void* user_data, const std::string& line) {
    if (cb) {
        cb(line.c_str(), user_data);
    }
}

bool is_relevant_logcat_line(const std::string& line) {
    return contains_ci(line, "fingerprint") ||
           contains_ci(line, "biometric") ||
           contains_ci(line, "fod") ||
           contains_ci(line, "udfps") ||
           contains_ci(line, "hbm") ||
           contains_ci(line, "goodix") ||
           contains_ci(line, "gf_hal") ||
           contains_ci(line, "silead") ||
           contains_ci(line, "focal") ||
           contains_ci(line, "egis") ||
           contains_ci(line, "illumination");
}

} // namespace

extern "C" {

FODFIX_API const char* fodfix_get_version(void) {
    return kVersion;
}

FODFIX_API int fodfix_init(void) {
    ALOGI("[CORE] Initialization started (version=%s)", kVersion);
    return 0;
}

FODFIX_API fodfix_result_t fodfix_run_self_test(char* out_buf, size_t out_buf_size) {
    fodfix_result_t res{};
    res.status_code = 0;

    std::ostringstream oss;
    oss << "[CORE] Initialization started\n";
    oss << "[CORE] Library loaded\n";

    std::string sdk_str = get_sys_prop("ro.build.version.sdk");
    std::string release_str = get_sys_prop("ro.build.version.release");
    std::string abi_str = get_sys_prop("ro.product.cpu.abi");

    res.android_api_level = sdk_str.empty() ? 0 : atoi(sdk_str.c_str());

    if (!sdk_str.empty() || !abi_str.empty()) {
        oss << "[CORE] Android environment detected\n";
    } else {
        oss << "[CORE] Android environment detected (minimal props)\n";
    }

    void* self_handle = dlopen(nullptr, RTLD_LAZY);
    if (self_handle != nullptr) {
        dlclose(self_handle);
    }

    oss << "[CORE] Runtime test completed\n";
    oss << "[HAL] Discovery pending\n";
    oss << "[CORE] Bridge version=" << kVersion
        << " | API=" << (sdk_str.empty() ? "?" : sdk_str)
        << " (Android " << (release_str.empty() ? "?" : release_str) << ")"
        << " | ABI=" << (abi_str.empty() ? "?" : abi_str) << "\n";

    write_to_buf(oss.str(), out_buf, out_buf_size);
    return res;
}

FODFIX_API int fodfix_probe_hal_env(char* out_buf, size_t out_buf_size) {
    std::ostringstream oss;
    int total_found = 0;

    static const char* kHalDirs[] = {
        "/vendor/lib64/hw",
        "/vendor/lib/hw",
        "/vendor/lib64",
        "/odm/lib64/hw",
        "/vendor/bin/hw",
        "/system/lib64"
    };

    for (const char* dir_path : kHalDirs) {
        auto matches = scan_dir_for_hal(dir_path);
        for (const auto& item : matches) {
            oss << "[HAL] Module: " << item << "\n";
            ++total_found;
        }
    }

    for (const char* dev_path : kFpDevNodes) {
        struct stat st{};
        if (stat(dev_path, &st) == 0) {
            oss << "[HAL] Device node present: " << dev_path
                << (access(dev_path, R_OK) == 0 ? " (readable)" : " (restricted)") << "\n";
            ++total_found;
        }
    }

    auto procs = scan_fp_hal_processes();
    for (const auto& kv : procs) {
        oss << "[HAL] Active process pid=" << kv.first << ": " << kv.second << "\n";
        ++total_found;
    }

    int fod_nodes_found = 0;
    for (const auto& node : kFodSysfsNodes) {
        struct stat st{};
        if (stat(node.path, &st) == 0) {
            std::string val = read_small_file(node.path);
            oss << "[FOD] Sysfs " << node.label << "="
                << (val.empty() ? "<restricted>" : val)
                << " (" << node.path << ")\n";
            ++fod_nodes_found;
        }
    }
    if (fod_nodes_found == 0) {
        oss << "[FOD] No direct FOD sysfs nodes readable in app context\n";
    }

    for (const char* key : kFodProps) {
        std::string val = get_sys_prop(key);
        if (!val.empty()) {
            const char* tag = contains_ci(key, "fod") ? "[FOD]" : "[HAL]";
            oss << tag << " " << key << "=" << val << "\n";
        }
    }

    write_to_buf(oss.str(), out_buf, out_buf_size);
    return total_found + fod_nodes_found;
}

FODFIX_API int fodfix_verify_library(const char* lib_path, char* out_buf, size_t out_buf_size) {
    std::ostringstream oss;
    if (!lib_path || !*lib_path) {
        write_to_buf("[ERR] Invalid library path\n", out_buf, out_buf_size);
        return -1;
    }

    struct stat st{};
    if (stat(lib_path, &st) != 0) {
        oss << "[ERR] Target missing or inaccessible (" << lib_path << "): " << strerror(errno) << "\n";
        write_to_buf(oss.str(), out_buf, out_buf_size);
        return -2;
    }

    dlerror();
    void* handle = dlopen(lib_path, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        const char* err = dlerror();
        oss << "[ERR] dlopen failed for " << lib_path << ": " << (err ? err : "unknown error") << "\n";
        write_to_buf(oss.str(), out_buf, out_buf_size);
        return -3;
    }

    using self_test_fn = fodfix_result_t (*)(char*, size_t);
    using probe_fn = int (*)(char*, size_t);

    auto self_fn = reinterpret_cast<self_test_fn>(dlsym(handle, "fodfix_run_self_test"));
    auto hal_fn = reinterpret_cast<probe_fn>(dlsym(handle, "fodfix_probe_hal_env"));
    if (!self_fn) {
        const char* err = dlerror();
        oss << "[ERR] Symbol fodfix_run_self_test not found: " << (err ? err : "unknown error") << "\n";
        dlclose(handle);
        write_to_buf(oss.str(), out_buf, out_buf_size);
        return -4;
    }

    char inner_buf[8192] = {0};
    self_fn(inner_buf, sizeof(inner_buf));
    oss << inner_buf;

    if (hal_fn) {
        char hal_buf[8192] = {0};
        hal_fn(hal_buf, sizeof(hal_buf));
        oss << hal_buf;
    }

    oss << "[CORE] Verified dlopen & symbol binding at " << lib_path
        << " (" << static_cast<long long>(st.st_size) << " bytes)\n";

    dlclose(handle);
    write_to_buf(oss.str(), out_buf, out_buf_size);
    return 0;
}

FODFIX_API int fodfix_start_live_monitor(fodfix_log_callback_t callback, void* user_data) {
    if (!callback) return -1;

    fodfix_stop_live_monitor();

    std::lock_guard<std::mutex> lock(g_live_mutex);
    g_live_running.store(true);

    g_live_thread = std::thread([callback, user_data]() {
        emit_line(callback, user_data, "[FOD] Live monitor active — interact with fingerprint sensor");

        // Snapshot initial readable sysfs FOD values and props
        std::map<std::string, std::string> last_sysfs;
        for (const auto& node : kFodSysfsNodes) {
            std::string val = read_small_file(node.path);
            if (!val.empty()) {
                last_sysfs[node.label] = val;
            }
        }

        std::map<std::string, std::string> last_props;
        for (const char* key : kFodProps) {
            last_props[key] = get_sys_prop(key);
        }

        std::map<std::string, std::string> last_procs = scan_fp_hal_processes();

        // Open non-blocking live logcat stream filtered to biometric/HAL/FOD tags
        FILE* log_pipe = popen(
            "logcat -v brief -T 1 *:S "
            "FingerprintService:V BiometricService:V BiometricPrompt:V "
            "UdfpsController:V AuthController:V "
            "android.hardware.biometrics.fingerprint:V "
            "vendor.xiaomi.hardware.fingerprintextension:V "
            "vendor.goodix.hardware.biometrics.fingerprint:V "
            "gf_hal:V fpc_fingerprint_hal:V silead_fp:V fod:V 2>/dev/null",
            "r"
        );
        if (log_pipe) {
            int fd = fileno(log_pipe);
            int flags = fcntl(fd, F_GETFL, 0);
            if (flags >= 0) {
                fcntl(fd, F_SETFL, flags | O_NONBLOCK);
            }
            emit_line(callback, user_data, "[HAL] Live biometric/HAL event stream attached");
        } else {
            emit_line(callback, user_data, "[HAL] Logcat stream unavailable; polling sysfs, props & HAL procs");
        }

        std::string line_acc;
        char read_buf[1024];
        int proc_tick = 0;

        while (g_live_running.load()) {
            // 1. Poll FOD/HBM sysfs state changes
            for (const auto& node : kFodSysfsNodes) {
                auto it = last_sysfs.find(node.label);
                if (it != last_sysfs.end()) {
                    std::string cur = read_small_file(node.path);
                    if (!cur.empty() && cur != it->second) {
                        emit_line(
                            callback,
                            user_data,
                            "[FOD] " + std::string(node.label) + " changed: " + it->second + " -> " + cur
                        );
                        it->second = cur;
                    }
                }
            }

            // 2. Poll FOD properties
            for (const char* key : kFodProps) {
                std::string cur = get_sys_prop(key);
                if (cur != last_props[key]) {
                    emit_line(
                        callback,
                        user_data,
                        "[FOD] Property " + std::string(key) + " -> " + (cur.empty() ? "<unset>" : cur)
                    );
                    last_props[key] = cur;
                }
            }

            // 3. Drain non-blocking logcat stream
            if (log_pipe) {
                while (g_live_running.load()) {
                    ssize_t n = read(fileno(log_pipe), read_buf, sizeof(read_buf));
                    if (n > 0) {
                        line_acc.append(read_buf, static_cast<size_t>(n));
                        size_t pos = 0;
                        while ((pos = line_acc.find('\n')) != std::string::npos) {
                            std::string single = line_acc.substr(0, pos);
                            line_acc.erase(0, pos + 1);
                            if (!single.empty() && is_relevant_logcat_line(single)) {
                                bool is_fod_evt = contains_ci(single, "fod") ||
                                                  contains_ci(single, "udfps") ||
                                                  contains_ci(single, "hbm") ||
                                                  contains_ci(single, "illumination");
                                std::string prefix = is_fod_evt ? "[FOD] " : "[HAL] ";
                                emit_line(callback, user_data, prefix + single);
                            }
                        }
                    } else {
                        break;
                    }
                }
            }

            // 4. Periodically check HAL daemon PIDs (~1.2s)
            if (++proc_tick >= 10) {
                proc_tick = 0;
                auto cur_procs = scan_fp_hal_processes();
                for (const auto& kv : cur_procs) {
                    if (last_procs.find(kv.first) == last_procs.end()) {
                        emit_line(
                            callback,
                            user_data,
                            "[HAL] Daemon started pid=" + kv.first + ": " + kv.second
                        );
                    }
                }
                for (const auto& kv : last_procs) {
                    if (cur_procs.find(kv.first) == cur_procs.end()) {
                        emit_line(
                            callback,
                            user_data,
                            "[HAL] Daemon exited pid=" + kv.first + ": " + kv.second
                        );
                    }
                }
                last_procs = std::move(cur_procs);
            }

            std::this_thread::sleep_for(std::chrono::milliseconds(120));
        }

        if (log_pipe) {
            pclose(log_pipe);
        }
        emit_line(callback, user_data, "[FOD] Live monitor stopped");
    });

    return 0;
}

FODFIX_API void fodfix_stop_live_monitor(void) {
    std::lock_guard<std::mutex> lock(g_live_mutex);
    g_live_running.store(false);
    if (g_live_thread.joinable()) {
        g_live_thread.join();
    }
}

} // extern "C"
