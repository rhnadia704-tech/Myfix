#include "fodfix_core.h"

#include <dlfcn.h>
#include <jni.h>
#include <mutex>
#include <string>

namespace {

JavaVM* g_jvm = nullptr;
std::mutex g_cb_mutex;
jobject g_callback_obj = nullptr;
jmethodID g_on_line_method = nullptr;
void* g_external_handle = nullptr;

using ext_stop_fn_t = void (*)(void);
ext_stop_fn_t g_external_stop_fn = nullptr;

void jni_live_callback(const char* line, void* /* user_data */) {
    if (!line || !g_jvm) return;

    JNIEnv* env = nullptr;
    bool attached = false;
    jint status = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) {
            return;
        }
        attached = true;
    } else if (status != JNI_OK || !env) {
        return;
    }

    jobject local_cb = nullptr;
    jmethodID method_id = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_cb_mutex);
        if (g_callback_obj && g_on_line_method) {
            local_cb = env->NewLocalRef(g_callback_obj);
            method_id = g_on_line_method;
        }
    }

    if (local_cb && method_id) {
        jstring j_line = env->NewStringUTF(line);
        if (j_line) {
            env->CallVoidMethod(local_cb, method_id, j_line);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(j_line);
        }
        env->DeleteLocalRef(local_cb);
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

void clear_jni_callback(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (g_callback_obj && env) {
        env->DeleteGlobalRef(g_callback_obj);
    }
    g_callback_obj = nullptr;
    g_on_line_method = nullptr;
}

void set_jni_callback(JNIEnv* env, jobject listener) {
    clear_jni_callback(env);
    if (!listener || !env) return;
    jclass cls = env->GetObjectClass(listener);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onNativeLogLine", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    if (!mid) return;
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    g_callback_obj = env->NewGlobalRef(listener);
    g_on_line_method = mid;
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_example_nativebridge_FodFixNative_nativeGetVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
    return env->NewStringUTF(fodfix_get_version());
}

JNIEXPORT jstring JNICALL
Java_com_example_nativebridge_FodFixNative_nativeRunDiagnostic(
    JNIEnv* env,
    jobject /* thiz */
) {
    fodfix_init();
    char buf[8192] = {0};
    fodfix_run_self_test(buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jstring JNICALL
Java_com_example_nativebridge_FodFixNative_nativeProbeVendorHals(
    JNIEnv* env,
    jobject /* thiz */
) {
    char buf[8192] = {0};
    fodfix_probe_hal_env(buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jstring JNICALL
Java_com_example_nativebridge_FodFixNative_nativeVerifySystemLibrary(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_path
) {
    if (!j_path) {
        return env->NewStringUTF("[ERR] Null path provided\n");
    }
    const char* c_path = env->GetStringUTFChars(j_path, nullptr);
    char buf[16384] = {0};
    fodfix_verify_library(c_path, buf, sizeof(buf));
    env->ReleaseStringUTFChars(j_path, c_path);
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL
Java_com_example_nativebridge_FodFixNative_nativeStartLiveMonitor(
    JNIEnv* env,
    jobject /* thiz */,
    jobject listener
) {
    set_jni_callback(env, listener);
    return fodfix_start_live_monitor(jni_live_callback, nullptr);
}

JNIEXPORT void JNICALL
Java_com_example_nativebridge_FodFixNative_nativeStopLiveMonitor(
    JNIEnv* env,
    jobject /* thiz */
) {
    fodfix_stop_live_monitor();
    if (g_external_stop_fn) {
        g_external_stop_fn();
        g_external_stop_fn = nullptr;
    }
    if (g_external_handle) {
        dlclose(g_external_handle);
        g_external_handle = nullptr;
    }
    clear_jni_callback(env);
}

JNIEXPORT jint JNICALL
Java_com_example_nativebridge_FodFixNative_nativeStartExternalLiveMonitor(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_path,
    jobject listener
) {
    if (!j_path) return -1;
    fodfix_stop_live_monitor();
    if (g_external_stop_fn) {
        g_external_stop_fn();
        g_external_stop_fn = nullptr;
    }
    if (g_external_handle) {
        dlclose(g_external_handle);
        g_external_handle = nullptr;
    }

    const char* c_path = env->GetStringUTFChars(j_path, nullptr);
    void* handle = dlopen(c_path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(j_path, c_path);
    if (!handle) {
        return -2;
    }

    using start_fn_t = int (*)(fodfix_log_callback_t, void*);
    auto start_fn = reinterpret_cast<start_fn_t>(dlsym(handle, "fodfix_start_live_monitor"));
    auto stop_fn = reinterpret_cast<ext_stop_fn_t>(dlsym(handle, "fodfix_stop_live_monitor"));
    if (!start_fn || !stop_fn) {
        dlclose(handle);
        return -3;
    }

    set_jni_callback(env, listener);
    g_external_handle = handle;
    g_external_stop_fn = stop_fn;
    return start_fn(jni_live_callback, nullptr);
}

} // extern "C"
