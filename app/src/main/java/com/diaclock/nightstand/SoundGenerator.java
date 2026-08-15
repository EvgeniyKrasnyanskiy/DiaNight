package com.diaclock.nightstand;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * High-performance algorithmic PCM sound generator.
 * Synthesizes audible alarm sounds on the fly using AudioTrack on STREAM_ALARM without external audio assets.
 */
public final class SoundGenerator {

    private static final String TAG = "SoundGenerator";
    private static final int SAMPLE_RATE = 44100;

    public static final String SOUND_BUILTIN_PULSE = "builtin_pulse";
    public static final String SOUND_BUILTIN_RADAR = "builtin_radar";
    public static final String SOUND_BUILTIN_CHIME = "builtin_chime";
    public static final String SOUND_SYSTEM = "system";
    public static final String SOUND_SILENT = "silent";

    private static final Object lock = new Object();
    private static volatile boolean isPlaying = false;
    private static volatile AudioTrack activeAudioTrack = null;
    private static volatile Thread playbackThread = null;

    private SoundGenerator() {
        // Utility class
    }

    public static boolean isBuiltin(String soundType) {
        return SOUND_BUILTIN_PULSE.equals(soundType) ||
               SOUND_BUILTIN_RADAR.equals(soundType) ||
               SOUND_BUILTIN_CHIME.equals(soundType);
    }

    /**
     * Starts looping playback of the specified built-in alarm sound.
     */
    public static void startAlarm(final String soundType) {
        synchronized (lock) {
            stopAlarm();
            isPlaying = true;
            playbackThread = new Thread(() -> runAudioLoop(soundType, true), "SoundGenerator-Alarm");
            playbackThread.setPriority(Thread.MAX_PRIORITY);
            playbackThread.start();
        }
    }

    /**
     * Plays a single short preview of the specified sound and automatically stops.
     */
    public static void playPreview(final String soundType, final Runnable onComplete) {
        synchronized (lock) {
            stopAlarm();
            isPlaying = true;
            playbackThread = new Thread(() -> {
                try {
                    runAudioLoop(soundType, false);
                } finally {
                    synchronized (lock) {
                        isPlaying = false;
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }, "SoundGenerator-Preview");
            playbackThread.start();
        }
    }

    /**
     * Stops any currently playing audio generation.
     */
    public static void stopAlarm() {
        synchronized (lock) {
            isPlaying = false;
            if (activeAudioTrack != null) {
                try {
                    activeAudioTrack.pause();
                    activeAudioTrack.flush();
                    activeAudioTrack.stop();
                } catch (Exception ignored) {}
                try {
                    activeAudioTrack.release();
                } catch (Exception ignored) {}
                activeAudioTrack = null;
            }
            if (playbackThread != null) {
                playbackThread.interrupt();
                playbackThread = null;
            }
        }
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    private static void runAudioLoop(String soundType, boolean loop) {
        short[] buffer = generateSoundBuffer(soundType);
        if (buffer == null || buffer.length == 0) return;

        int bufferSize = Math.max(buffer.length * 2, AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        ));

        AudioTrack track = null;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build();
                track = new AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            } else {
                track = new AudioTrack(
                        AudioManager.STREAM_ALARM,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );
            }

            synchronized (lock) {
                if (!isPlaying) {
                    track.release();
                    return;
                }
                activeAudioTrack = track;
            }

            track.play();

            do {
                int written = 0;
                while (written < buffer.length && isPlaying && !Thread.currentThread().isInterrupted()) {
                    int result = track.write(buffer, written, buffer.length - written);
                    if (result <= 0) break;
                    written += result;
                }
                if (!loop) break;
            } while (isPlaying && !Thread.currentThread().isInterrupted());

        } catch (Exception e) {
            Log.e(TAG, "AudioTrack playback error: " + e.getMessage(), e);
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Exception ignored) {}
                try {
                    track.release();
                } catch (Exception ignored) {}
                synchronized (lock) {
                    if (activeAudioTrack == track) {
                        activeAudioTrack = null;
                    }
                }
            }
        }
    }

    private static short[] generateSoundBuffer(String soundType) {
        if (SOUND_BUILTIN_RADAR.equals(soundType)) {
            return generateRadarSiren();
        } else if (SOUND_BUILTIN_CHIME.equals(soundType)) {
            return generateSoftChime();
        } else {
            return generatePulseBeep();
        }
    }

    /**
     * Sound 1: Pulse Beep (Fast dual high-pitch urgent beeps: 880 Hz & 1760 Hz).
     * Duration: 1.5 seconds cycle.
     */
    private static short[] generatePulseBeep() {
        int totalSamples = (int) (SAMPLE_RATE * 1.5); // 1.5s cycle
        short[] samples = new short[totalSamples];

        int beep1Samples = (int) (SAMPLE_RATE * 0.12); // 120ms
        int gapSamples = (int) (SAMPLE_RATE * 0.08);   // 80ms
        int beep2Samples = (int) (SAMPLE_RATE * 0.16); // 160ms

        // Beep 1 (880 Hz + 1760 Hz harmonic)
        for (int i = 0; i < beep1Samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * i / beep1Samples); // Smooth sine envelope
            double val = (0.7 * Math.sin(2 * Math.PI * 880 * t) + 0.3 * Math.sin(2 * Math.PI * 1760 * t)) * envelope;
            samples[i] = (short) (val * 32000);
        }

        // Beep 2 (1046.5 Hz + 2093 Hz harmonic)
        int start2 = beep1Samples + gapSamples;
        for (int i = 0; i < beep2Samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * i / beep2Samples);
            double val = (0.7 * Math.sin(2 * Math.PI * 1046.5 * t) + 0.3 * Math.sin(2 * Math.PI * 2093 * t)) * envelope;
            samples[start2 + i] = (short) (val * 32000);
        }

        // Rest of buffer is silence
        return samples;
    }

    /**
     * Sound 2: Radar Siren (Ascending frequency sweep 600 Hz -> 1400 Hz).
     * Duration: 1.2 seconds cycle.
     */
    private static short[] generateRadarSiren() {
        int totalSamples = (int) (SAMPLE_RATE * 1.2);
        short[] samples = new short[totalSamples];

        int sweepSamples = (int) (SAMPLE_RATE * 0.8); // 800ms sweep, 400ms pause
        double phase = 0.0;

        for (int i = 0; i < sweepSamples; i++) {
            double progress = (double) i / sweepSamples;
            // Frequency sweeps from 600 to 1400 Hz
            double freq = 600.0 + 800.0 * progress;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * progress);
            double val = Math.sin(phase) * envelope;
            samples[i] = (short) (val * 32000);
        }

        return samples;
    }

    /**
     * Sound 3: Soft Chime (Harmonic chord C5+E5+G5+C6 with natural exponential decay).
     * Duration: 2.0 seconds cycle.
     */
    private static short[] generateSoftChime() {
        int totalSamples = (int) (SAMPLE_RATE * 2.0);
        short[] samples = new short[totalSamples];

        double[] freqs = {523.25, 659.25, 783.99, 1046.50};
        double[] weights = {0.4, 0.3, 0.2, 0.1};

        int chimeSamples = (int) (SAMPLE_RATE * 1.6);

        for (int i = 0; i < chimeSamples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double decay = Math.exp(-2.5 * t); // Smooth natural chime decay
            double val = 0.0;
            for (int f = 0; f < freqs.length; f++) {
                val += weights[f] * Math.sin(2 * Math.PI * freqs[f] * t);
            }
            samples[i] = (short) (val * decay * 32000);
        }

        return samples;
    }
}
