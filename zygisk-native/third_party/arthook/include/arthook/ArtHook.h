// SPDX-License-Identifier: Apache-2.0
#ifndef ARTHOOK_ARTHOOK_H_
#define ARTHOOK_ARTHOOK_H_

#include <jni.h>

namespace arthook {

enum class Status {
    kOk,
    kNotInitialized,
    kLayoutDiscoveryFailed,
    kMethodNotFound,
    kTrampolineAllocFailed,
    kAlreadyHooked,
    kNotHooked,
    kInvalidArgument,
    kOutOfMemory,
    kNoJniBridge,
    kDeoptUnavailable,
    kInternalError,
};

Status Initialize(JNIEnv* env, bool verify = false);

namespace detail {
Status AcquireJniEnv(JNIEnv** env, JavaVM** vm, bool* attached);
}

template <class Fn>
Status AttachToJavaVM(Fn&& body) {
    JNIEnv* env = nullptr;
    JavaVM* vm = nullptr;
    bool attached = false;
    Status s = detail::AcquireJniEnv(&env, &vm, &attached);
    if (s != Status::kOk) return s;
    struct Detacher {
        JNIEnv* env;
        JavaVM* vm;
        bool attached;
        ~Detacher() {
            if (attached && vm) {
                if (env && env->ExceptionCheck()) {
                    env->ExceptionDescribe();
                    env->ExceptionClear();
                }
                vm->DetachCurrentThread();
            }
        }
    } guard{env, vm, attached};
    body(env);
    return Status::kOk;
}

Status Hook(JNIEnv* env, jclass clazz, const char* name, const char* signature,
            void* replacement, void** backup_out = nullptr);
Status Hook(JNIEnv* env, const char* class_name, const char* name, const char* signature,
            void* replacement, void** backup_out = nullptr);

Status Deoptimize(JNIEnv* env, jclass clazz, const char* name, const char* signature);
Status Unhook(JNIEnv* env, jclass clazz, const char* name, const char* signature);
Status Unhook(JNIEnv* env, const char* class_name, const char* name, const char* signature);

bool IsInitialized();
const char* StatusToString(Status s);

}  // namespace arthook

#endif  // ARTHOOK_ARTHOOK_H_
