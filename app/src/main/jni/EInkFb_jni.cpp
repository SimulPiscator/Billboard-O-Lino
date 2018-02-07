#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/fb.h>
#include <linux/ioctl.h>
#include <sys/mman.h>
#include "mxcfb.h"

namespace Bitmap {
    namespace Config {
        enum {
            ALPHA_8 = 2,
            RGB_565 = 4,
            ARGB_4444 = 5,
            ARGB_8888 = 6,
        };
    } // namespace Config
} // namespace Bitmap

struct NativeFramebuffer {
public:
    enum WaveformMode {
        INIT = 0, DU = 1, GC16 = 2, GC4 = 3, A2 = 4,
    };

    NativeFramebuffer(const char*);
    ~NativeFramebuffer();

    bool isOpen() const { return mFd >= 0; }
    int getError() const { return mErrno; }

    int getWidth() const { return mVarinfo.xres; }
    int getHeight() const { return mVarinfo.yres; }
    int getConfig() const;
    int getRotate() const { return mVarinfo.rotate; }

    uint32_t getHWRowBytes() const;
    uint32_t getFrameRowBytes() const;
    uint32_t getFrameBytes() const;
    uint32_t getFrameOffset() const;

    bool copyPixelsFromBuffer(const void* src);
    bool copyPixelsToBuffer(void* dst);

    // E-ink specific
    int getPowerDownDelay();
    bool setPowerDownDelay(int delay);

    bool refresh(uint32_t waveform_mode, uint32_t id);
    bool wait(uint32_t id);
    static uint32_t nextRefreshId();

private:
    uint8_t* mFbmem;
    uint32_t mFbmemBytes;
    int mFd, mErrno;
    fb_var_screeninfo mVarinfo;
};

NativeFramebuffer::NativeFramebuffer(const char* devname)
        : mFbmem(nullptr), mFbmemBytes(0), mFd(-1), mErrno(0)
{
    ::memset(&mVarinfo, 0, sizeof(mVarinfo));
    mFd = ::open(devname, O_RDWR|O_EXCL);
    if(mFd < 0) {
        mErrno = errno;
        return;
    }
    fb_fix_screeninfo fixinfo = {0};

    if (::ioctl(mFd, FBIOGET_FSCREENINFO, &fixinfo) < 0)
        mErrno = errno;
    else if (::ioctl(mFd, FBIOGET_VSCREENINFO, &mVarinfo) < 0)
        mErrno = errno;
    if(mErrno)
        return;

    mErrno = ENOTSUP;
    if(fixinfo.type != FB_TYPE_PACKED_PIXELS)
        return;
    if(fixinfo.visual != FB_VISUAL_TRUECOLOR)
        return;
    mErrno = 0;

    mFbmemBytes = mVarinfo.xres_virtual * mVarinfo.yres_virtual * mVarinfo.bits_per_pixel / 8;
    mFbmem = static_cast<uint8_t *>(::mmap(nullptr, mFbmemBytes, PROT_READ | PROT_WRITE,
                                        MAP_SHARED, mFd, 0));
    if (!mFbmem)
        mErrno = errno;
}

NativeFramebuffer::~NativeFramebuffer()
{
    if(mFbmem)
        ::munmap(mFbmem, mFbmemBytes);
    if(mFd >= 0)
        ::close(mFd);
}

int
NativeFramebuffer::getConfig() const
{
    if (mVarinfo.grayscale == 1 && mVarinfo.bits_per_pixel == 8)
        return Bitmap::Config::ALPHA_8;
    if (mVarinfo.grayscale == 0 && mVarinfo.bits_per_pixel == 32)
        return Bitmap::Config::ARGB_8888;
    if (mVarinfo.grayscale == 0 && mVarinfo.transp.length == 0 && mVarinfo.bits_per_pixel == 16)
        return Bitmap::Config::RGB_565;
    if (mVarinfo.grayscale == 0 && mVarinfo.bits_per_pixel == 16)
        return Bitmap::Config::ARGB_4444;
    return -1;
}

uint32_t
NativeFramebuffer::getHWRowBytes() const
{
    return mVarinfo.xres_virtual * mVarinfo.bits_per_pixel / 8;
}

uint32_t
NativeFramebuffer::getFrameRowBytes() const
{
    return mVarinfo.xres * mVarinfo.bits_per_pixel / 8;
}

uint32_t
NativeFramebuffer::getFrameOffset() const
{
    return (mVarinfo.xoffset + mVarinfo.yoffset * mVarinfo.xres_virtual) * mVarinfo.bits_per_pixel / 8;
}

uint32_t
NativeFramebuffer::getFrameBytes() const
{
    return (mVarinfo.xres * mVarinfo.yres) * mVarinfo.bits_per_pixel / 8;
}

bool
NativeFramebuffer::copyPixelsFromBuffer(const void *src)
{
    if(!src) {
        mErrno = EFAULT;
        return false;
    }
    if(!mFbmem)
        return false;
    const uint8_t* pSrc = static_cast<const uint8_t*>(src),
        *pSrcEnd = pSrc + getFrameBytes();
    uint8_t* pDest = mFbmem + getFrameOffset(),
        *pDestEnd = mFbmem + mFbmemBytes;
    while(pSrc < pSrcEnd && pDest < pDestEnd) {
        ::memcpy(pDest, pSrc, getFrameRowBytes());
        pSrc += getFrameRowBytes();
        pDest += getHWRowBytes();
    }
    return true;
}

bool
NativeFramebuffer::copyPixelsToBuffer(void *dst)
{
    if(!dst) {
        mErrno = EFAULT;
        return false;
    }
    if(!mFbmem)
        return false;
    const uint8_t* pSrc = mFbmem + getFrameOffset(),
            *pSrcEnd = mFbmem + mFbmemBytes;
    uint8_t* pDest = static_cast<uint8_t*>(dst),
            *pDestEnd = pDest + getFrameBytes();
    while(pSrc < pSrcEnd && pDest < pDestEnd) {
        ::memcpy(pDest, pSrc, getFrameRowBytes());
        pSrc += getHWRowBytes();
        pDest += getFrameRowBytes();
    }
    return true;
}

bool
NativeFramebuffer::refresh(uint32_t waveform_mode, uint32_t marker)
{
    mxcfb_update_data data = { 0 };
    data.update_region.left = 0;
    data.update_region.top = 0;
    data.update_region.width = mVarinfo.xres;
    data.update_region.height = mVarinfo.yres;
    data.waveform_mode = waveform_mode;
    switch(waveform_mode) {
        case DU:
        case A2:
            data.flags |= EPDC_FLAG_USE_DITHERING_Y1;
            break;
        case GC4:
            data.flags |= EPDC_FLAG_USE_DITHERING_Y4;
            break;
    }
    data.update_mode = UPDATE_MODE_FULL;
    data.update_marker = marker;
    data.temp = TEMP_USE_AMBIENT;
    int maxRepeats = 5;
    while(--maxRepeats > 0 && ::ioctl(mFd, MXCFB_SEND_UPDATE, &data) < 0) {
        mErrno = errno;
        ::usleep(300000);
    }
    return maxRepeats > 0;
}

bool
NativeFramebuffer::wait(uint32_t marker)
{
    mxcfb_update_marker_data data = { 0 };
    data.update_marker = marker;
    if(::ioctl(mFd, MXCFB_WAIT_FOR_UPDATE_COMPLETE, &data) < 0) {
        mErrno = errno;
        return false;
    }
    return true;
}

uint32_t
NativeFramebuffer::nextRefreshId()
{
    static uint32_t id = 0;
    ++id;
    // Refresh ID must not be zero, and should be unique across clients.
    return 'bbl:' ^ (id & 0xffff);
}

bool
NativeFramebuffer::setPowerDownDelay(int delay)
{
    if(::ioctl(mFd, MXCFB_SET_PWRDOWN_DELAY, &delay) < 0) {
        mErrno = errno;
        return false;
    }
    return true;
}

int
NativeFramebuffer::getPowerDownDelay()
{
    int delay = -2;
    if(::ioctl(mFd, MXCFB_GET_PWRDOWN_DELAY, &delay) < 0) {
        mErrno = errno;
        return -2;
    }
    return delay;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeOpen(JNIEnv *env, jobject, jstring devname)
{
    const char* devname_utf = env->GetStringUTFChars(devname, nullptr);
    auto p = new NativeFramebuffer(devname_utf);
    env->ReleaseStringUTFChars(devname, devname_utf);
    return reinterpret_cast<jint>(p);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeClose(JNIEnv *env, jobject, jint obj)
{
    delete reinterpret_cast<NativeFramebuffer*>(obj);
}

extern "C"
JNIEXPORT bool JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeCopyPixelsFromBuffer(JNIEnv *env, jobject, jint obj, jobject src)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    if(p) {
        void* addr = env->GetDirectBufferAddress(src);
        return p->copyPixelsFromBuffer(addr);
    }
    return false;
}

extern "C"
JNIEXPORT bool JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeCopyPixelsToBuffer(JNIEnv *env, jobject, jint obj, jobject dst)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    if(p) {
        void* addr = env->GetDirectBufferAddress(dst);
        return p->copyPixelsToBuffer(addr);
    }
    return false;
}

extern "C"
JNIEXPORT bool JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeIsOpen(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->isOpen() : false;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetByteCount(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getFrameBytes() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetRowBytes(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getFrameRowBytes() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetConfig(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getConfig() : -1;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetHeight(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getHeight() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetWidth(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getWidth() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetRotate(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getRotate() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetPowerDownDelay(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getPowerDownDelay() : -1;
}

extern "C"
JNIEXPORT bool JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeSetPowerDownDelay(JNIEnv *env, jobject, jint obj, jint delay)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->setPowerDownDelay(delay) : false;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeRefresh(JNIEnv *env, jobject, jint obj, jint waveform_mode)
{
    int id = NativeFramebuffer::nextRefreshId();
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    if(!p || !p->refresh(waveform_mode, id))
        return 0;
    return id;
}

extern "C"
JNIEXPORT bool JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeWait(JNIEnv *env, jobject, jint obj, jint id)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->wait(id) : false;
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetError(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    return p ? p->getError() : 0;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_simulpiscator_billboardolino_EInkFb_nativeGetErrorString(JNIEnv *env, jobject, jint obj)
{
    auto p = reinterpret_cast<NativeFramebuffer*>(obj);
    int err = p ? p->getError() : 0;
    return env->NewStringUTF(::strerror(err));
}