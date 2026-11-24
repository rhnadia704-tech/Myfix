#ifndef MYFIX_FODFIX_CORE_H
#define MYFIX_FODFIX_CORE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FODFIX_API __attribute__((visibility("default")))

typedef struct {
    int status_code;
    int android_api_level;
    int vendor_hal_count;
    int system_bridge_loaded;
} fodfix_result_t;

typedef void (*fodfix_log_callback_t)(const char* line, void* user_data);

FODFIX_API const char* fodfix_get_version(void);
FODFIX_API int fodfix_init(void);
FODFIX_API fodfix_result_t fodfix_run_self_test(char* out_buf, size_t out_buf_size);
FODFIX_API int fodfix_probe_hal_env(char* out_buf, size_t out_buf_size);
FODFIX_API int fodfix_verify_library(const char* lib_path, char* out_buf, size_t out_buf_size);
FODFIX_API int fodfix_start_live_monitor(fodfix_log_callback_t callback, void* user_data);
FODFIX_API void fodfix_stop_live_monitor(void);

#ifdef __cplusplus
}
#endif

#endif // MYFIX_FODFIX_CORE_H
