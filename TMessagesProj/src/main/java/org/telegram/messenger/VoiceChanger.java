package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pitch shifter (duration preserving, SoundTouch based) applied to the raw 16-bit mono PCM
 * of voice messages right before it reaches the Opus encoder.
 *
 * An instance is not thread safe: use it from a single queue (MediaController uses fileEncodingQueue).
 */
public class VoiceChanger {

    public static final int MIN_SEMITONES = -12;
    public static final int MAX_SEMITONES = 12;
    public static final int DEFAULT_SEMITONES = -4;

    // Makeup gain for calls only (the shifted voice tends to sound quieter on the other side).
    public static final int MIN_GAIN_DB = 0;
    public static final int MAX_GAIN_DB = 12;
    public static final int DEFAULT_GAIN_DB = 6;

    private static final int FLUSH_CAPACITY = 48000 * 2; // up to 1 second of 48 kHz PCM16

    static {
        // The native code lives in the main tmessages library which is already loaded by the app.
        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);
    }

    private static native long nativeCreate(int sampleRate, float semitones);

    private static native void nativeSetPitch(long handle, float semitones);

    private static native int nativeProcess(long handle, ByteBuffer in, int inLen, ByteBuffer out, int outCapacity);

    private static native int nativeFlush(long handle, ByteBuffer out, int outCapacity);

    private static native void nativeDestroy(long handle);

    private static native void nativeSetCallState(boolean enabled, float semitones, float gainDb);

    private long handle;
    private ByteBuffer out;

    private VoiceChanger(int sampleRate, int semitones) {
        handle = nativeCreate(sampleRate, semitones);
        out = allocate(FLUSH_CAPACITY);
    }

    /** Active effect for the current settings, or null when the feature is off / has no audible effect. */
    public static VoiceChanger createIfEnabled(int sampleRate) {
        if (!SharedConfig.voiceChangerEnabled || SharedConfig.voiceChangerSemitones == 0) {
            return null;
        }
        try {
            return new VoiceChanger(sampleRate, clamp(SharedConfig.voiceChangerSemitones));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Pushes the current toggle and pitch to the native call engine. The call audio thread reads them on every
     * frame, so this can be invoked at any time, including in the middle of a call.
     */
    public static void syncCallState() {
        try {
            nativeSetCallState(SharedConfig.voiceChangerEnabled, clamp(SharedConfig.voiceChangerSemitones), clampGain(SharedConfig.voiceChangerGainDb));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String describe(int semitones) {
        if (semitones == 0) {
            return "Original";
        }
        return (semitones > 0 ? "+" : "") + semitones + (Math.abs(semitones) == 1 ? " semitone" : " semitones");
    }

    public static String describeGain(int gainDb) {
        return gainDb <= 0 ? "Off" : "+" + gainDb + " dB";
    }

    public static int clampGain(int gainDb) {
        return Math.max(MIN_GAIN_DB, Math.min(MAX_GAIN_DB, gainDb));
    }

    public static int clamp(int semitones) {
        return Math.max(MIN_SEMITONES, Math.min(MAX_SEMITONES, semitones));
    }

    public void setSemitones(int semitones) {
        if (handle != 0) {
            nativeSetPitch(handle, clamp(semitones));
        }
    }

    /**
     * Converts the PCM in {@code in} (position..limit is consumed) and returns the shifted PCM that is ready,
     * as a buffer positioned at 0. The returned buffer is reused by the next call.
     * When {@code last} is true the internal tail is flushed as well.
     */
    public ByteBuffer process(ByteBuffer in, boolean last) {
        int inLen = in.remaining();
        // pitch shifting keeps the duration, so the output stays close to the input size; keep a generous margin
        ensureCapacity(inLen * 2 + 16384);
        out.clear();
        int written = 0;
        if (handle != 0 && inLen > 0) {
            // slice() starts at the current position, which is where the native side starts reading
            ByteBuffer slice = in.slice();
            slice.order(ByteOrder.nativeOrder());
            written = nativeProcess(handle, slice, inLen, out, out.capacity());
            in.position(in.limit());
        }
        if (last && handle != 0) {
            int cap = out.capacity() - written;
            if (cap > 0) {
                out.position(written);
                ByteBuffer tail = out.slice();
                tail.order(ByteOrder.nativeOrder());
                written += nativeFlush(handle, tail, cap);
            }
        }
        out.position(0);
        out.limit(written);
        return out;
    }

    public void release() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
        out = null;
    }

    private void ensureCapacity(int capacity) {
        if (out == null || out.capacity() < capacity) {
            out = allocate(capacity);
        }
    }

    private static ByteBuffer allocate(int capacity) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }
}
