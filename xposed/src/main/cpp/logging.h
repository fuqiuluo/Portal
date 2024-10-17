#ifndef _LOGGING_H
#define _LOGGING_H

#include <android/log.h>

#ifndef LOG_TAG
#define LOG_TAG  "LSPosed-Bridge"
#endif

#ifdef LOG_DISABLED
#define LOGD(...)
#define LOGV(...)
#define LOGI(...)
#define LOGW(...)
#define LOGE(...)
#else
#ifndef NDEBUG
#define LOGD(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[Portal][DEBUG] %s:%d#%s" ": " fmt, __FILE_NAME__, __LINE__, __PRETTY_FUNCTION__ __VA_OPT__(,) __VA_ARGS__)
#define LOGV(fmt, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[Portal][VERBOSE] %s:%d#%s" ": " fmt, __FILE_NAME__, __LINE__, __PRETTY_FUNCTION__ __VA_OPT__(,) __VA_ARGS__)
#else
#define LOGD(...)
#define LOGV(...)
#endif
#define LOGW(fmt, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[Portal][ERROR] %s:%d#%s" ": " fmt, __FILE_NAME__, __LINE__, __PRETTY_FUNCTION__ __VA_OPT__(,) __VA_ARGS__)
#define LOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[Portal][INFO] %s:%d#%s" ": " fmt, __FILE_NAME__, __LINE__, __PRETTY_FUNCTION__ __VA_OPT__(,) __VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[Portal][ERROR] %s:%d#%s" ": " fmt, __FILE_NAME__, __LINE__, __PRETTY_FUNCTION__ __VA_OPT__(,) __VA_ARGS__)
#endif

#endif // _LOGGING_H