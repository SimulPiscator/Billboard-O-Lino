package org.simulpiscator.billboardolino;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

class EInkFb {

    static final String TAG = "bbl:EInkFb";
    enum Orientation {
        PORTRAIT(0), LANDSCAPE(1), PORTRAIT_UD(2), LANDSCAPE_UD(3);
        Orientation(int v) { value = v; }
        public int value;
    }
    enum WaveformMode {
        INIT(0), DU(1), GC16(2), GC4(3), A2(4);
        WaveformMode(int v) { value = v; }
        public int value;
    }

    EInkFb() { this("/dev/graphics/fb0"); }
    EInkFb(String device) {
        mNativeFb = nativeOpen(device);
        if(!nativeIsOpen(mNativeFb))
            throw new RuntimeException(nativeGetErrorString(mNativeFb));
    }
    @Override
    protected void finalize() throws Throwable {
        nativeClose(mNativeFb);
        super.finalize();
    }

    boolean open(String device) {
        nativeClose(mNativeFb);
        mNativeFb = nativeOpen(device);
        return nativeIsOpen(mNativeFb);
    }
    void close() { nativeClose(mNativeFb); mNativeFb = 0; }
    boolean isOpen() { return nativeIsOpen(mNativeFb); }
    int getError() { return nativeGetError(mNativeFb); }
    String getErrorString() { return "Framebuffer: " + nativeGetErrorString(mNativeFb); }

    // a subset of the android.graphics.Bitmap interface
    int getHeight() { return nativeGetHeight(mNativeFb); }
    int getWidth() { return nativeGetWidth(mNativeFb); }
    int getByteCount() { return nativeGetByteCount(mNativeFb); }
    int getRowBytes() { return nativeGetRowBytes(mNativeFb); }
    Bitmap.Config getConfig() {
        switch(nativeGetConfig(mNativeFb)) {
            case 2:
                return Bitmap.Config.ALPHA_8;
            case 4:
                return Bitmap.Config.RGB_565;
            case 5:
                return Bitmap.Config.ARGB_4444;
            case 6:
                return Bitmap.Config.ARGB_8888;
            default:
                return null;
        }
    }
    Orientation getOrientation() {
        boolean isLandscape = getHeight() < getWidth();
        switch(nativeGetRotate(mNativeFb)) {
            case 0: // FB_ROTATE_UR
                return isLandscape ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
            case 1: // FB_ROTATE_CW
                return isLandscape ? Orientation.PORTRAIT : Orientation.LANDSCAPE_UD;
            case 2: // FB_ROTATE_UD
                return isLandscape ? Orientation.LANDSCAPE_UD : Orientation.PORTRAIT_UD;
            case 3: // FB_ROTATE_CCW
                return isLandscape ? Orientation.PORTRAIT_UD : Orientation.LANDSCAPE;
            default:
                return null;
        }
    }

    Bitmap getBitmap() {
        ByteBuffer buf = ByteBuffer.allocateDirect(getByteCount());
        this.copyPixelsToBuffer(buf);
        buf.rewind();
        Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(), getConfig());
        b.copyPixelsFromBuffer(buf);
        return b;
    }

    void putBitmap(Bitmap inBitmap) {
        Bitmap b = null;
        if(inBitmap.getConfig() == getConfig()
            && inBitmap.getWidth() == getWidth()
            && inBitmap.getHeight() == getHeight())
        {
            b = inBitmap;
        }
        else
        {
            Log.d(TAG, "putBitmapAsync: format mismatch, rescaling");
            b = Bitmap.createBitmap(getWidth(), getHeight(), getConfig());
            Canvas c = new Canvas(b);
            c.drawBitmap(inBitmap, 0, 0, null);
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(getByteCount());
        b.copyPixelsToBuffer(buf);
        buf.rewind();
        this.copyPixelsFromBuffer(buf);
    }

    /**
     * Copy framebuffer pixels into the specified buffer (allocated by the
     * caller). An exception is thrown if the buffer is not large enough to
     * hold all of the pixels (taking into account the number of bytes per
     * pixel) or if the Buffer subclass is not one of the supported types
     * (ByteBuffer, ShortBuffer, IntBuffer).
     */
    void copyPixelsToBuffer(Buffer dst) {
        if(!isOpen())
            throw new RuntimeException("no framebuffer device open");
        int elements = dst.remaining();
        int shift;
        if (dst instanceof ByteBuffer) {
            shift = 0;
        } else if (dst instanceof ShortBuffer) {
            shift = 1;
        } else if (dst instanceof IntBuffer) {
            shift = 2;
        } else {
            throw new RuntimeException("unsupported Buffer subclass");
        }
        long bufferSize = (long)elements << shift;
        long pixelSize = getByteCount();
        if (bufferSize < pixelSize) {
            throw new RuntimeException("Buffer not large enough for pixels");
        }
        if(!nativeCopyPixelsToBuffer(mNativeFb, dst))
            throw new RuntimeException(getErrorString());
        int position = dst.position();
        position += pixelSize >> shift;
        dst.position(position);
    }
    /**
     * Copy the pixels from the buffer, beginning at the current position,
     * overwriting pixels in the framebuffer.
     */
    void copyPixelsFromBuffer(Buffer src) {
        if(!isOpen())
            throw new RuntimeException("no framebuffer device open");
        int elements = src.remaining();
        int shift;
        if (src instanceof ByteBuffer) {
            shift = 0;
        } else if (src instanceof ShortBuffer) {
            shift = 1;
        } else if (src instanceof IntBuffer) {
            shift = 2;
        } else {
            throw new RuntimeException("unsupported Buffer subclass");
        }
        long bufferBytes = (long)elements << shift;
        long bitmapBytes = getByteCount();
        if (bufferBytes < bitmapBytes) {
            throw new RuntimeException("Buffer not large enough for pixels");
        }
        if(!nativeCopyPixelsFromBuffer(mNativeFb, src))
            throw new RuntimeException(getErrorString());
    }

    /**
     * Trigger display refresh. Returns a refresh ID to be used with wait().
     */
    int refreshAsync(WaveformMode mode) {
        return nativeRefresh(mNativeFb, mode.value, 0);
    }

    /**
     * Wait for refresh with given ID to complete.
     * @param id
     */
    boolean wait(int id) {
        return nativeWait(mNativeFb, id);
    }

    /**
     * Trigger display refresh, and wait for completion.
     */
    boolean refreshSync(WaveformMode mode) {
        int id = refreshAsync(mode);
        if(id != 0)
            return wait(id);
        return false;
    }

    /**
     * E-ink power down delay, -1 for off
     * @param delay
     */
    void setPowerDownDelay(int delay) {
        if(!nativeSetPowerDownDelay(mNativeFb, delay))
            throw new RuntimeException(getErrorString());
    }

    int getPowerDownDelay() {
        int delay = nativeGetPowerDownDelay(mNativeFb);
        if(delay == -2)
            throw new RuntimeException(getErrorString());
        return delay;
    }

    static { System.loadLibrary("EInkFb_jni"); }
    private int mNativeFb = 0;

    private native int nativeOpen(String device);
    private native void nativeClose(int p);
    private native boolean nativeIsOpen(int p);

    private native int nativeGetHeight(int p);
    private native int nativeGetWidth(int p);
    private native int nativeGetByteCount(int p);
    private native int nativeGetRowBytes(int p);
    private native int nativeGetConfig(int p);
    private native int nativeGetRotate(int p);

    private native boolean nativeCopyPixelsFromBuffer(int p, Buffer src);
    private native boolean nativeCopyPixelsToBuffer(int p, Buffer dst);

    private native int nativeGetPowerDownDelay(int p);
    private native boolean nativeSetPowerDownDelay(int p, int delay);
    private native int nativeRefresh(int p, int waveformMode, int refreshId);
    private native boolean nativeWait(int p, int refreshId);

    private native int nativeGetError(int p);
    private native String nativeGetErrorString(int p);
}
