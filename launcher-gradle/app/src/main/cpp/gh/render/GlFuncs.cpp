#include "GlFuncs.h"
#include <dlfcn.h>
#include <string>

#define GHGL_IMPL(ret, name, args) ret (*gh_##name) args = nullptr;
GHGL_IMPL(void, glActiveTexture, (GLenum))
GHGL_IMPL(void, glAttachShader, (GLuint, GLuint))
GHGL_IMPL(void, glBindBuffer, (GLenum, GLuint))
GHGL_IMPL(void, glBindTexture, (GLenum, GLuint))
GHGL_IMPL(void, glBindVertexArray, (GLuint))
GHGL_IMPL(void, glBlendFunc, (GLenum, GLenum))
GHGL_IMPL(void, glBufferData, (GLenum, GLsizeiptr, const void*, GLenum))
GHGL_IMPL(void, glClear, (GLenum))
GHGL_IMPL(void, glClearColor, (GLfloat, GLfloat, GLfloat, GLfloat))
GHGL_IMPL(void, glCompileShader, (GLuint))
GHGL_IMPL(GLuint, glCreateProgram, (void))
GHGL_IMPL(GLuint, glCreateShader, (GLenum))
GHGL_IMPL(void, glCullFace, (GLenum))
GHGL_IMPL(void, glDeleteBuffers, (GLsizei, const GLuint*))
GHGL_IMPL(void, glDeleteProgram, (GLuint))
GHGL_IMPL(void, glDeleteShader, (GLuint))
GHGL_IMPL(void, glDeleteTextures, (GLsizei, const GLuint*))
GHGL_IMPL(void, glDeleteVertexArrays, (GLsizei, const GLuint*))
GHGL_IMPL(void, glDepthFunc, (GLenum))
GHGL_IMPL(void, glDisable, (GLenum))
GHGL_IMPL(void, glDisableVertexAttribArray, (GLuint))
GHGL_IMPL(void, glDrawArrays, (GLenum, GLint, GLsizei))
GHGL_IMPL(void, glDrawElements, (GLenum, GLsizei, GLenum, const void*))
GHGL_IMPL(void, glEnable, (GLenum))
GHGL_IMPL(void, glEnableVertexAttribArray, (GLuint))
GHGL_IMPL(void, glFrontFace, (GLenum))
GHGL_IMPL(void, glGenBuffers, (GLsizei, GLuint*))
GHGL_IMPL(void, glGenTextures, (GLsizei, GLuint*))
GHGL_IMPL(void, glGenVertexArrays, (GLsizei, GLuint*))
GHGL_IMPL(GLenum, glGetError, (void))
GHGL_IMPL(void, glGetProgramInfoLog, (GLuint, GLsizei, GLsizei*, char*))
GHGL_IMPL(void, glGetProgramiv, (GLuint, GLenum, GLint*))
GHGL_IMPL(void, glGetShaderInfoLog, (GLuint, GLsizei, GLsizei*, char*))
GHGL_IMPL(void, glGetShaderiv, (GLuint, GLenum, GLint*))
GHGL_IMPL(GLint, glGetUniformLocation, (GLuint, const char*))
GHGL_IMPL(void, glLinkProgram, (GLuint))
GHGL_IMPL(void, glShaderSource, (GLuint, GLsizei, const char* const*, const GLint*))
GHGL_IMPL(void, glTexImage2D, (GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*))
GHGL_IMPL(void, glCompressedTexImage2D, (GLenum, GLint, GLenum, GLsizei, GLsizei, GLint, GLsizei, const void*))
GHGL_IMPL(void, glTexParameteri, (GLenum, GLenum, GLint))
GHGL_IMPL(void, glUniform1f, (GLint, GLfloat))
GHGL_IMPL(void, glUniform1i, (GLint, GLint))
GHGL_IMPL(void, glUniform3f, (GLint, GLfloat, GLfloat, GLfloat))
GHGL_IMPL(void, glUniformMatrix4fv, (GLint, GLsizei, GLboolean, const GLfloat*))
GHGL_IMPL(void, glUseProgram, (GLuint))
GHGL_IMPL(void, glValidateProgram, (GLuint))
GHGL_IMPL(void, glVertexAttribPointer, (GLuint, GLint, GLenum, GLboolean, GLsizei, const void*))
GHGL_IMPL(void, glViewport, (GLint, GLint, GLsizei, GLsizei))
GHGL_IMPL(const GLubyte*, glGetString, (GLenum))
GHGL_IMPL(void, glGetIntegerv, (GLenum, GLint*))
#undef GHGL_IMPL

namespace gh {

bool loadGlFunctions() {
    void* h = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) return false;

#define LOAD(name) \
    if (!gh_##name) { \
        gh_##name = (decltype(gh_##name))dlsym(h, #name); \
        if (!gh_##name) gh_##name = (decltype(gh_##name))eglGetProcAddress(#name); \
    }
    LOAD(glActiveTexture) LOAD(glAttachShader) LOAD(glBindBuffer) LOAD(glBindTexture)
    LOAD(glBindVertexArray) LOAD(glBlendFunc) LOAD(glBufferData) LOAD(glClear)
    LOAD(glClearColor) LOAD(glCompileShader) LOAD(glCreateProgram) LOAD(glCreateShader)
    LOAD(glCullFace) LOAD(glDeleteBuffers) LOAD(glDeleteProgram) LOAD(glDeleteShader)
    LOAD(glDeleteTextures) LOAD(glDeleteVertexArrays) LOAD(glDepthFunc) LOAD(glDisable)
    LOAD(glDisableVertexAttribArray) LOAD(glDrawArrays) LOAD(glDrawElements) LOAD(glEnable)
    LOAD(glEnableVertexAttribArray) LOAD(glFrontFace) LOAD(glGenBuffers) LOAD(glGenTextures)
    LOAD(glGenVertexArrays) LOAD(glGetError) LOAD(glGetProgramInfoLog) LOAD(glGetProgramiv)
    LOAD(glGetShaderInfoLog) LOAD(glGetShaderiv) LOAD(glGetUniformLocation) LOAD(glLinkProgram)
    LOAD(glShaderSource) LOAD(glTexImage2D) LOAD(glCompressedTexImage2D) LOAD(glTexParameteri)
    LOAD(glUniform1f) LOAD(glUniform1i) LOAD(glUniform3f) LOAD(glUniformMatrix4fv)
    LOAD(glUseProgram) LOAD(glValidateProgram) LOAD(glVertexAttribPointer) LOAD(glViewport)
    LOAD(glGetString) LOAD(glGetIntegerv)
#undef LOAD

    bool ok = gh_glCreateShader && gh_glBindVertexArray && gh_glCompressedTexImage2D;
    return ok;
}

} // namespace gh
