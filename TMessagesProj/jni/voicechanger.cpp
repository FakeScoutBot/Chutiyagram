// Voice changer: pitch shifting (duration preserving) for recorded voice messages.
// Wraps SoundTouch. Samples are 16-bit mono PCM, converted to float internally.
#include <jni.h>
#include <stdint.h>
#include <vector>
#include <algorithm>
#include "SoundTouch.h"
#include "voip/tgcalls/group/VoicePitchShifter.h"

using namespace soundtouch;

namespace {

struct VoiceChanger {
    SoundTouch st;
    std::vector<float> inBuf;
    std::vector<float> outBuf;
};

// Drains everything SoundTouch has ready into out (16-bit PCM). Returns bytes written.
int drain(VoiceChanger *vc, int16_t *out, int maxSamples) {
    int total = 0;
    while (total < maxSamples) {
        int want = std::min<int>((int) vc->outBuf.size(), maxSamples - total);
        uint32_t got = vc->st.receiveSamples(vc->outBuf.data(), (uint32_t) want);
        if (got == 0) {
            break;
        }
        for (uint32_t i = 0; i < got; i++) {
            float v = vc->outBuf[i] * 32768.0f;
            if (v > 32767.0f) v = 32767.0f;
            else if (v < -32768.0f) v = -32768.0f;
            out[total + i] = (int16_t) v;
        }
        total += (int) got;
    }
    return total * 2;
}

}

extern "C" {

JNIEXPORT jlong Java_org_telegram_messenger_VoiceChanger_nativeCreate(JNIEnv *env, jclass clazz, jint sampleRate, jfloat semitones) {
    VoiceChanger *vc = new VoiceChanger();
    vc->st.setSampleRate((uint32_t) sampleRate);
    vc->st.setChannels(1);
    vc->st.setPitchSemiTones(semitones);
    // speech-tuned time-domain stretch parameters
    vc->st.setSetting(SETTING_SEQUENCE_MS, 40);
    vc->st.setSetting(SETTING_SEEKWINDOW_MS, 15);
    vc->st.setSetting(SETTING_OVERLAP_MS, 8);
    vc->inBuf.resize(8192);
    vc->outBuf.resize(8192);
    return (jlong) (intptr_t) vc;
}

JNIEXPORT void Java_org_telegram_messenger_VoiceChanger_nativeSetPitch(JNIEnv *env, jclass clazz, jlong handle, jfloat semitones) {
    VoiceChanger *vc = (VoiceChanger *) (intptr_t) handle;
    if (vc != nullptr) {
        vc->st.setPitchSemiTones(semitones);
    }
}

// Feeds inLen bytes of PCM16 and writes whatever is ready into out. Returns number of bytes written to out.
JNIEXPORT jint Java_org_telegram_messenger_VoiceChanger_nativeProcess(JNIEnv *env, jclass clazz, jlong handle, jobject in, jint inLen, jobject out, jint outCapacity) {
    VoiceChanger *vc = (VoiceChanger *) (intptr_t) handle;
    if (vc == nullptr) {
        return 0;
    }
    int16_t *src = (int16_t *) env->GetDirectBufferAddress(in);
    int16_t *dst = (int16_t *) env->GetDirectBufferAddress(out);
    if (src == nullptr || dst == nullptr) {
        return 0;
    }
    int samples = inLen / 2;
    int offset = 0;
    while (offset < samples) {
        int chunk = std::min<int>((int) vc->inBuf.size(), samples - offset);
        for (int i = 0; i < chunk; i++) {
            vc->inBuf[i] = src[offset + i] / 32768.0f;
        }
        vc->st.putSamples(vc->inBuf.data(), (uint32_t) chunk);
        offset += chunk;
    }
    return drain(vc, dst, outCapacity / 2);
}

// Pushes the remaining internal samples through and returns them. Call once at the end of a recording.
JNIEXPORT jint Java_org_telegram_messenger_VoiceChanger_nativeFlush(JNIEnv *env, jclass clazz, jlong handle, jobject out, jint outCapacity) {
    VoiceChanger *vc = (VoiceChanger *) (intptr_t) handle;
    if (vc == nullptr) {
        return 0;
    }
    int16_t *dst = (int16_t *) env->GetDirectBufferAddress(out);
    if (dst == nullptr) {
        return 0;
    }
    vc->st.flush();
    return drain(vc, dst, outCapacity / 2);
}

// Live state for calls: read by the capture post-processor on every 10 ms frame, so changes apply mid-call.
JNIEXPORT void Java_org_telegram_messenger_VoiceChanger_nativeSetCallState(JNIEnv *env, jclass clazz, jboolean enabled, jfloat semitones) {
    tgcalls::VoiceChangerCallState &state = tgcalls::voiceChangerCallState();
    state.semitones.store(semitones, std::memory_order_relaxed);
    state.enabled.store(enabled != JNI_FALSE, std::memory_order_relaxed);
}

JNIEXPORT void Java_org_telegram_messenger_VoiceChanger_nativeDestroy(JNIEnv *env, jclass clazz, jlong handle) {
    delete (VoiceChanger *) (intptr_t) handle;
}

}
