
#include <dobby.h>
#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>
#include "sensor_hook.h"

bool enableSensorHook = false;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    doSensorHook();

    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_moe_fuqiuluo_dobby_Dobby_setStatus(JNIEnv *env, jobject thiz, jboolean status) {
    enableSensorHook = status;
}