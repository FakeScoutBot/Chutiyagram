#ifndef TGCALLS_GROUP_VOICE_PITCH_SHIFTER_H
#define TGCALLS_GROUP_VOICE_PITCH_SHIFTER_H

#include <atomic>
#include <cstddef>
#include <memory>

namespace tgcalls {

// Live voice changer settings shared between the Java UI and the real-time audio thread.
// Written through JNI (VoiceChanger.nativeSetCallState), read by the capture post-processors on every frame,
// so the slider can be moved in the middle of a call.
struct VoiceChangerCallState {
    std::atomic<bool> enabled{false};
    std::atomic<float> semitones{0.0f};
    // Makeup gain in dB applied to the shifted voice (with a soft limiter), to compensate for it sounding quieter.
    std::atomic<float> gainDb{0.0f};
};

inline VoiceChangerCallState &voiceChangerCallState() {
    static VoiceChangerCallState state;
    return state;
}

// Streaming, duration preserving pitch shifter (SoundTouch) for the real-time capture path.
//
// WebRTC hands the capture processor exactly 10 ms per call and expects exactly 10 ms back, while SoundTouch
// produces its output in bursts of a different size. A small adaptive delay line sits in between, so the
// processor always returns a full frame. It costs a fixed, small delay (see latencySamples()).
//
// Samples are mono floats in the 16-bit range (+-32768), the same scale WebRTC uses for AudioBuffer channels.
// Not thread safe: one instance per audio thread.
class VoicePitchShifter {
public:
    VoicePitchShifter();
    ~VoicePitchShifter();

    // Pitch shifts `samples` in place. Returns false (and leaves the samples untouched) when `semitones` is
    // effectively zero. A change of sample rate restarts the shifter.
    bool process(float *samples, size_t numSamples, int sampleRate, float semitones, float gainDb = 0.0f);

    // Drops buffered audio. Call when the effect is switched off so it restarts clean when switched on again.
    void reset();

    // Current end-to-end delay the effect adds, in samples at the configured sample rate.
    size_t latencySamples() const;

private:
    struct Impl;
    std::unique_ptr<Impl> _impl;
};

} // namespace tgcalls

#endif
