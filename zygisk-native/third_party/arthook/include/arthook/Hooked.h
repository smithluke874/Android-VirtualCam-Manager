// SPDX-License-Identifier: Apache-2.0
//
// RAII handle for a single arthook installation, with a typed `invoke()`
// that calls the original method uniformly for native and non-native
// targets, instance and static.

#ifndef ARTHOOK_HOOKED_H_
#define ARTHOOK_HOOKED_H_

#include <jni.h>

#include <string>
#include <type_traits>

#include "arthook/ArtHook.h"

namespace arthook {

namespace detail {
void RefreshBackupClass(void* backup, void* target);
}

class Hooked {
public:
    Hooked() = default;
    Hooked(const Hooked&) = delete;
    Hooked& operator=(const Hooked&) = delete;
    Hooked(Hooked&& other) noexcept;
    Hooked& operator=(Hooked&& other) noexcept;
    ~Hooked();

    Status install(
        JNIEnv* env, jclass clazz, const char* name, const char* signature, void* replacement);

    Status install(
        JNIEnv* env, const char* class_name, const char* name, const char* signature,
        void* replacement);

    void release(JNIEnv* env);

    bool installed() const { return decl_ != nullptr; }
    bool target_is_native() const { return installed() && backup_jm_ == nullptr; }
    bool target_is_static() const { return is_static_; }

    template <typename Ret, typename... Args>
    Ret invoke(JNIEnv* env, jobject thiz, Args... args);

private:
    void* backup_fn_ = nullptr;
    jmethodID backup_jm_ = nullptr;
    void* target_ = nullptr;
    jclass decl_ = nullptr;
    bool is_static_ = false;
    std::string name_;
    std::string sig_;
};

}  // namespace arthook

#endif  // ARTHOOK_HOOKED_H_
