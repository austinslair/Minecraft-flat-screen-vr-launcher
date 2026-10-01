//
// Created by Judge on 12/23/2021.
//
#include <thread>
#include <string>
#include <errno.h>
#include <android/hardware_buffer.h>
#include <fcntl.h>
#include <unistd.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#include <environ/environ.h>
#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <openxr/openxr.h>
#include "log.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getEGLDisplay(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(eglGetCurrentDisplay());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getEGLContext(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(eglGetCurrentContext());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getEGLConfig(JNIEnv* env, jclass clazz) {
    // OpenXR requires the configuration belonging to the current context.
    // A new eglChooseConfig call can select an incompatible config.
    EGLDisplay display = eglGetCurrentDisplay();
    EGLContext context = eglGetCurrentContext();
    EGLint configId = 0;
    EGLConfig cfg = nullptr;
    EGLint count = 0;
    if (display == EGL_NO_DISPLAY || context == EGL_NO_CONTEXT ||
        !eglQueryContext(display, context, EGL_CONFIG_ID, &configId)) {
        return 0;
    }
    const EGLint attributes[] = {EGL_CONFIG_ID, configId, EGL_NONE};
    if (!eglChooseConfig(display, attributes, &cfg, 1, &count) || count != 1) {
        return 0;
    }
    return reinterpret_cast<jlong>(cfg);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getDalvikVM(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(pojav_environ->dalvikJavaVMPtr);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_vivecraft_util_VLoader_getDalvikActivity(JNIEnv* env, jclass clazz) {
    return reinterpret_cast<jlong>(pojav_environ->activity);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_vivecraft_util_VLoader_setupAndroid(JNIEnv* env, jclass clazz) {
    JNIEnv *newEnv;
    pojav_environ->dalvikJavaVMPtr->AttachCurrentThread(&newEnv, NULL);
    jclass apiClass = pojav_environ->apiClass;
    jfieldID fieldID = newEnv->GetStaticFieldID(apiClass, "gameReady", "Z");
    newEnv->SetStaticBooleanField(apiClass, fieldID, true);
}

extern "C"
JNIEXPORT void JNICALL
Java_pojlib_util_VLoader_setAndroidInitInfo(JNIEnv *env, jclass clazz, jobject ctx) {
    pojav_environ->activity = env->NewGlobalRef(ctx);
}