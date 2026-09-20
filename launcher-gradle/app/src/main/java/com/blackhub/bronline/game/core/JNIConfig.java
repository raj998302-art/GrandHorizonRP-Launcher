package com.blackhub.bronline.game.core;

/**
 * Engine config tree bridge (handles are engine-side pointers).
 * Signatures match the original smali contract exactly.
 */
public final class JNIConfig {

    private long mNativeHandle;

    private static native long nativeGameSettings();

    private native void nativeDestroy(long handle);

    private native long nativeGetChild(long handle, String name);

    private native float nativeGetFloat(long handle);

    private native long nativeGetIntPacked(long handle);

    private native String nativeGetString(long handle);

    private native boolean nativeIsValid(long handle);

    private native boolean nativeSetFloat(long handle, float value);

    private native boolean nativeSetInt(long handle, int value);

    private native boolean nativeSetString(long handle, String value);

    public static JNIConfig gameSettings() {
        JNIConfig config = new JNIConfig();
        config.mNativeHandle = nativeGameSettings();
        return config;
    }

    public boolean isValid() {
        return mNativeHandle != 0 && nativeIsValid(mNativeHandle);
    }

    public JNIConfig getChild(String name) {
        if (mNativeHandle == 0 || name == null) return null;
        long child = nativeGetChild(mNativeHandle, name);
        if (child == 0) return null;
        JNIConfig result = new JNIConfig();
        result.mNativeHandle = child;
        return result;
    }

    public String getString() {
        return mNativeHandle == 0 ? null : nativeGetString(mNativeHandle);
    }

    public float getFloat() {
        return mNativeHandle == 0 ? 0f : nativeGetFloat(mNativeHandle);
    }

    public int getInt() {
        if (mNativeHandle == 0) return 0;
        long packed = nativeGetIntPacked(mNativeHandle);
        return (int) (packed & 0xFFFFFFFFL);
    }

    public boolean setFloat(float value) {
        return mNativeHandle != 0 && nativeSetFloat(mNativeHandle, value);
    }

    public boolean setInt(int value) {
        return mNativeHandle != 0 && nativeSetInt(mNativeHandle, value);
    }

    public boolean setString(String value) {
        return mNativeHandle != 0 && nativeSetString(mNativeHandle, value);
    }

    public void destroy() {
        if (mNativeHandle != 0) {
            nativeDestroy(mNativeHandle);
            mNativeHandle = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }
}
