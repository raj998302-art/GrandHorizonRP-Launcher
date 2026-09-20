#pragma once
// host-test stub
typedef void* EGLDisplay; typedef void* EGLConfig; typedef void* EGLSurface; typedef void* EGLContext;
#define EGL_NO_DISPLAY ((EGLDisplay)0)
extern "C" void* eglGetProcAddress(const char*);
