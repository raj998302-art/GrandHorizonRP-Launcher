package com.blackhub.bronline.game;

/**
 * Engine render bridge (private native instance methods — exact original contract).
 */
public class GameRender {
    private native void initGameRender();

    private native void nativeRequestRender(int a, int b, int c, int d, int e,
                                            float f0, float f1, float f2, float f3, float f4,
                                            float f5, float f6, float f7, float f8);

    private native void nativeRequestRenderTexture(byte[] data, int a);

    private native void nativeRequestRenderTexturePlate(int a, byte[] b, byte[] c, int d,
                                                        float f0, float f1, float f2, float f3);

    /** Called by the renderer host when the render surface is ready. */
    public void onRenderInitialized() {
        initGameRender();
    }
}
