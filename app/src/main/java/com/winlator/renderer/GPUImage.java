package com.winlator.renderer;

import android.util.Log;
import androidx.annotation.Keep;

import com.winlator.xserver.Drawable;
import com.winlator.xserver.Window;
import com.winlator.xserver.XServer;

import java.nio.ByteBuffer;

public class GPUImage extends Texture {
    private long hardwareBufferPtr;
    private long imageKHRPtr;
    private ByteBuffer virtualData;
    private short stride;
    private boolean locked = false;
    private int nativeHandle;

    static {
        System.loadLibrary("winlator");
    }

    public GPUImage(Drawable owner) {
        this(owner, true, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess) {
        this(owner, cpuAccess, true);
    }

    public GPUImage(Drawable owner, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        super(owner);
        hardwareBufferPtr = createHardwareBuffer(owner.width, owner.height, cpuAccess, useHALPixelFormatBGRA8888);
        if (cpuAccess && hardwareBufferPtr != 0) {
            virtualData = lockHardwareBuffer(hardwareBufferPtr);
            locked = true;
        }
    }

    @Override
    public void allocateTexture(short width, short height, ByteBuffer data) {
        if (isAllocated()) return;
        super.allocateTexture(width, height, null);
        imageKHRPtr = createImageKHR(hardwareBufferPtr, textureId);
    }

    @Override
    public void onContextLost() {
        /* the hardware buffer (and its contents) survive, only the texture binding has to be redone */
        imageKHRPtr = 0;
        super.onContextLost();
    }

    @Override
    public void updateFromDrawable() {
        if (!isAllocated() && owner != null) allocateTexture(owner.width, owner.height, null);
        needsUpdate = false;
    }

    public short getStride() {
        return stride;
    }

    @Keep
    private void setStride(short stride) {
        this.stride = stride;
    }

    public int getNativeHandle() {
        return nativeHandle;
    }

    @Keep
    private void setNativeHandle(int nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public ByteBuffer getVirtualData() {
        return virtualData;
    }

    @Override
    public void destroy() {
        destroyImageKHR(imageKHRPtr);
        destroyHardwareBuffer(hardwareBufferPtr, locked);
        virtualData = null;
        imageKHRPtr = 0;
        hardwareBufferPtr = 0;
        super.destroy();
    }

    public long getHardwareBufferPtr() {
        return hardwareBufferPtr;
    }

    public static GPUImage createOrObtain(XServer xServer, Drawable drawable, boolean cpuAccess, boolean useHALPixelFormatBGRA8888) {
        final Texture texture = drawable.getTexture();
        if (!(texture instanceof GPUImage)) {
            xServer.getRenderer().xServerView.queueEvent(texture::destroy);
            GPUImage gpuImage = new GPUImage(drawable, cpuAccess, useHALPixelFormatBGRA8888);
            drawable.setTexture(gpuImage);

            // A composite-redirected surface shares its texture with the parent
            // window (see XComposite), keep the parent on the new image.
            Window window = xServer.windowManager.getWindow(drawable.id);
            Window redirectParent = window != null ? (Window)window.getTag("compositeRedirectParent") : null;
            if (redirectParent != null && window.isSurface()) redirectParent.getContent().setTexture(gpuImage);
            Log.d("WINDBG", "gpuimage 0x" + Integer.toHexString(drawable.id) + " cpu " + cpuAccess + " redirectParent " + (redirectParent != null ? "0x" + Integer.toHexString(redirectParent.id) : "none")); // TEMP DEBUG
            return gpuImage;
        }
        else return (GPUImage)texture;
    }

    private native long createHardwareBuffer(short width, short height, boolean cpuAccess, boolean useHALPixelFormatBGRA8888);

    private native void destroyHardwareBuffer(long hardwareBufferPtr, boolean locked);

    private native ByteBuffer lockHardwareBuffer(long hardwareBufferPtr);

    private native long createImageKHR(long hardwareBufferPtr, int textureId);

    private native void destroyImageKHR(long imageKHRPtr);
}