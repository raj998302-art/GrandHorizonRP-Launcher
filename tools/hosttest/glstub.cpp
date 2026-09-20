// Host-test stubs for the GL entry points used by BtxTexture.cpp.
#include <GLES2/gl2.h>
typedef void (*Fn0)();
void (*gh_glGenTextures)(GLsizei, GLuint*) = nullptr;
void (*gh_glBindTexture)(GLenum, GLuint) = nullptr;
void (*gh_glTexParameteri)(GLenum, GLenum, GLint) = nullptr;
void (*gh_glCompressedTexImage2D)(GLenum, GLint, GLenum, GLsizei, GLsizei, GLint, GLsizei, const void*) = nullptr;
GLenum (*gh_glGetError)(void) = nullptr;
void (*gh_glDeleteTextures)(GLsizei, const GLuint*) = nullptr;
#include <cstdarg>
#include <cstdio>
extern "C" int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    va_list ap; va_start(ap, fmt);
    fprintf(stderr, "[GH:%d][%s] ", prio, tag);
    vfprintf(stderr, fmt, ap);
    fprintf(stderr, "\n");
    va_end(ap);
    return 0;
}
