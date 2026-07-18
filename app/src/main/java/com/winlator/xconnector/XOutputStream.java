package com.winlator.xconnector;

import com.winlator.xserver.XServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import dalvik.annotation.optimization.CriticalNative;

public class XOutputStream {
    private final ReentrantLock lock = new ReentrantLock();
    private final long nativePtr;
    private volatile boolean destroyed = false;

    static {
        System.loadLibrary("winlator");
    }

    public XOutputStream(int clientFd, int initialCapacity) {
        nativePtr = nativeAllocate(clientFd, initialCapacity);
    }

    public void setAncillaryFd(int ancillaryFd) {
        if (!destroyed) setAncillaryFd(nativePtr, ancillaryFd);
    }

    public void writeByte(byte value) {
        if (!destroyed) writeByte(nativePtr, value);
    }

    public void writeShort(short value) {
        if (!destroyed) writeShort(nativePtr, value);
    }

    public void writeInt(int value) {
        if (!destroyed) writeInt(nativePtr, value);
    }

    public void writeLong(long value) {
        if (!destroyed) writeLong(nativePtr, value);
    }

    public void writeString8(String str) {
        byte[] bytes = str.getBytes(XServer.LATIN1_CHARSET);
        int length = -str.length() & 3;
        write(bytes);
        if (length > 0) writePad(length);
    }

    public void write(byte[] data) {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int offset, int length) {
        if (destroyed) return;
        for (int i = offset; i < length; i++) writeByte(nativePtr, data[i]);
    }

    public void writeAt(int position, byte[] data) {
        if (!destroyed) writeAt(nativePtr, position, data);
    }

    public void write(ByteBuffer data) {
        if (destroyed) return;
        if (data.isDirect()) {
            writeByteBuffer(nativePtr, data, data.position(), data.remaining());
        }
        else {
            for (int i = data.position(), length = data.remaining(); i < length; i++) {
                writeByte(nativePtr, data.get(i));
            }
        }
    }

    public void writePad(int length) {
        if (!destroyed) writePad(nativePtr, length);
    }

    public XStreamLock lock() {
        return new OutputStreamLock();
    }

    public void destroy() {
        lock.lock();
        try {
            if (!destroyed) {
                destroy(nativePtr);
                destroyed = true;
            }
        }
        finally {
            lock.unlock();
        }
    }

    private class OutputStreamLock implements XStreamLock {
        public OutputStreamLock() {
            lock.lock();
        }

        @Override
        public void close() throws IOException {
            try {
                if (!destroyed && !sendData(nativePtr)) throw new IOException("Failed to send data.");
            }
            finally {
                lock.unlock();
            }
        }
    }

    public int length() {
        return !destroyed ? length(nativePtr) : 0;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    private native long nativeAllocate(int fd, int initialCapacity);

    @CriticalNative
    private static native void setAncillaryFd(long nativePtr, int ancillaryFd);

    @CriticalNative
    private static native void writeByte(long nativePtr, byte value);

    @CriticalNative
    private static native void writeShort(long nativePtr, short value);

    @CriticalNative
    private static native void writeInt(long nativePtr, int value);

    @CriticalNative
    private static native void writeLong(long nativePtr, long value);

    @CriticalNative
    private static native void writePad(long nativePtr, int length);

    private static native void writeAt(long nativePtr, int position, byte[] data);

    private static native void writeByteBuffer(long nativePtr, ByteBuffer data, int offset, int length);

    private static native boolean sendData(long nativePtr);

    private static native void destroy(long nativePtr);

    @CriticalNative
    private static native int length(long nativePtr);
}
