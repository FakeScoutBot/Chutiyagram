#include "group/VoicePitchShifter.h"

#include <algorithm>
#include <cmath>
#include <vector>

#include "SoundTouch.h"

namespace tgcalls {

namespace {

constexpr float kInt16Scale = 32768.0f;
// Soft limiter used with the makeup gain: below the knee the signal is untouched, above it it is compressed
// smoothly towards full scale so boosted peaks never hard clip.
constexpr float kLimiterKnee = 22000.0f;
constexpr float kFullScale = 32767.0f;
constexpr float kMinSemitones = 0.01f;
// Hard cap on the adaptive delay so a stalled burst can never grow the latency without bound.
constexpr size_t kMaxExtraDelayMs = 120;

}

struct VoicePitchShifter::Impl {
    soundtouch::SoundTouch st;
    int sampleRate = 0;
    float semitones = 0.0f;

    std::vector<float> scratchIn;
    std::vector<float> scratchOut;

    // Delay line holding shifted audio that has not been played yet.
    std::vector<float> fifo;
    size_t fifoRead = 0;

    // Playout hysteresis: while buffering we emit silence until `startThreshold` samples are available.
    bool playing = false;
    size_t startThreshold = 0;
    size_t maxThreshold = 0;

    size_t available() const {
        return fifo.size() - fifoRead;
    }

    void configure(int rate) {
        sampleRate = rate;
        st.clear();
        st.setSampleRate((uint32_t) rate);
        st.setChannels(1);
        // Same speech tuning as the voice message recorder, so quality matches.
        st.setSetting(SETTING_SEQUENCE_MS, 40);
        st.setSetting(SETTING_SEEKWINDOW_MS, 15);
        st.setSetting(SETTING_OVERLAP_MS, 8);
        st.setPitchSemiTones(semitones);
        maxThreshold = (size_t) rate * kMaxExtraDelayMs / 1000;
        restart();
    }

    void restart() {
        st.clear();
        fifo.clear();
        fifoRead = 0;
        playing = false;
        startThreshold = 0;
    }

    void compact() {
        if (fifoRead > 0 && fifoRead >= fifo.size() / 2) {
            fifo.erase(fifo.begin(), fifo.begin() + (long) fifoRead);
            fifoRead = 0;
        }
    }
};

VoicePitchShifter::VoicePitchShifter() : _impl(new Impl()) {
    _impl->scratchIn.resize(2048);
    _impl->scratchOut.resize(4096);
}

VoicePitchShifter::~VoicePitchShifter() = default;

void VoicePitchShifter::reset() {
    _impl->restart();
}

size_t VoicePitchShifter::latencySamples() const {
    // SoundTouch's own start-up latency plus whatever the adaptive delay line settled on.
    return (size_t) _impl->st.getSetting(SETTING_INITIAL_LATENCY) + _impl->startThreshold;
}

bool VoicePitchShifter::process(float *samples, size_t numSamples, int sampleRate, float semitones, float gainDb) {
    Impl &d = *_impl;
    if (samples == nullptr || numSamples == 0 || sampleRate <= 0) {
        return false;
    }
    if (std::fabs(semitones) < kMinSemitones) {
        if (d.sampleRate != 0 && (d.playing || d.available() != 0)) {
            d.restart();
        }
        return false;
    }
    if (sampleRate != d.sampleRate) {
        d.semitones = semitones;
        d.configure(sampleRate);
    } else if (semitones != d.semitones) {
        d.semitones = semitones;
        d.st.setPitchSemiTones(semitones);
    }

    // feed
    size_t offset = 0;
    while (offset < numSamples) {
        size_t chunk = std::min(d.scratchIn.size(), numSamples - offset);
        for (size_t i = 0; i < chunk; i++) {
            d.scratchIn[i] = samples[offset + i] / kInt16Scale;
        }
        d.st.putSamples(d.scratchIn.data(), (uint32_t) chunk);
        offset += chunk;
    }

    // collect whatever SoundTouch has ready
    for (;;) {
        uint32_t got = d.st.receiveSamples(d.scratchOut.data(), (uint32_t) d.scratchOut.size());
        if (got == 0) {
            break;
        }
        d.fifo.insert(d.fifo.end(), d.scratchOut.begin(), d.scratchOut.begin() + got);
    }

    // play out exactly numSamples
    if (!d.playing && d.available() >= std::max(d.startThreshold, numSamples)) {
        d.playing = true;
    }
    size_t produced = 0;
    if (d.playing) {
        produced = std::min(numSamples, d.available());
        for (size_t i = 0; i < produced; i++) {
            float v = d.fifo[d.fifoRead + i];
            v *= kInt16Scale;
            samples[i] = std::max(-32768.0f, std::min(32767.0f, v));
        }
        d.fifoRead += produced;
        if (produced < numSamples) {
            // Underrun: a burst arrived late. Go back to buffering and wait for a little more next time.
            d.playing = false;
            d.startThreshold = std::min(d.maxThreshold, d.startThreshold + std::max<size_t>(numSamples, (size_t) sampleRate / 200));
        }
    }
    for (size_t i = produced; i < numSamples; i++) {
        samples[i] = 0.0f;
    }
    if (gainDb > 0.05f) {
        const float gain = std::pow(10.0f, gainDb / 20.0f);
        for (size_t i = 0; i < produced; i++) {
            float v = samples[i] * gain;
            float a = std::fabs(v);
            if (a > kLimiterKnee) {
                float shaped = kLimiterKnee + (kFullScale - kLimiterKnee) * std::tanh((a - kLimiterKnee) / (kFullScale - kLimiterKnee));
                v = v < 0 ? -shaped : shaped;
            }
            samples[i] = v;
        }
    }
    d.compact();
    return true;
}

} // namespace tgcalls
