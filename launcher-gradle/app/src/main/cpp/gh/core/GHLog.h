// GHEngine — from-scratch native engine for GRAND HORIZON RP.
// File logging (mirrors the launcher's GHRPLog on the Java side).
#pragma once
#include <android/log.h>
#include <cstdio>
#include <cstdarg>
#include <ctime>

namespace gh {

class Log {
public:
    static void setFile(const char* path) {
        if (g_fp) fclose(g_fp);
        g_fp = path ? fopen(path, "ab") : nullptr;
    }
    static void v(const char* tag, const char* fmt, ...) {
        char buf[1024];
        va_list ap; va_start(ap, fmt);
        vsnprintf(buf, sizeof(buf), fmt, ap);
        va_end(ap);
        __android_log_print(ANDROID_LOG_INFO, "GHEngine", "[%s] %s", tag, buf);
        if (g_fp) { fprintf(g_fp, "[%lld] [%s] %s\n", (long long)nowMs(), tag, buf); fflush(g_fp); }
    }
    static void e(const char* tag, const char* fmt, ...) {
        char buf[1024];
        va_list ap; va_start(ap, fmt);
        vsnprintf(buf, sizeof(buf), fmt, ap);
        va_end(ap);
        __android_log_print(ANDROID_LOG_ERROR, "GHEngine", "[%s] %s", tag, buf);
        if (g_fp) { fprintf(g_fp, "[%lld] [ERR %s] %s\n", (long long)nowMs(), tag, buf); fflush(g_fp); }
    }
private:
    static long long nowMs() {
        struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
        return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    }
    inline static FILE* g_fp = nullptr;
};

#define GHLOG(...)   ::gh::Log::v("engine", __VA_ARGS__)
#define GHERR(...)   ::gh::Log::e("engine", __VA_ARGS__)

} // namespace gh
