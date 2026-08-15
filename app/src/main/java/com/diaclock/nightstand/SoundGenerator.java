package com.diaclock.nightstand;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * High-performance algorithmic PCM sound generator.
 * Synthesizes audible alarm sounds on the fly using AudioTrack in MODE_STATIC on STREAM_ALARM.
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

    private SoundGenerator() {
        // Utility class
    }

    public static boolean isBuiltin(String soundType) {
        return SOUND_BUILTIN_PULSE.equals(soundType) ||
               SOUND_BUILTIN_RADAR.equals(soundType) ||
               SOUND_BUILTIN_CHIME.equals(soundType);
    }

    public static boolean isPlaying() {
        synchronized (lock) {
            return isPlaying && activeAudioTrack != null;
        }
    }

    /**
     * Starts continuous looping playback of the specified built-in alarm sound.
     */
    public static void startAlarm(final String soundType) {
        startPlayback(soundType, true, null);
    }

    /**
     * Plays a single short preview of the specified sound and automatically stops.
     */
    public static void playPreview(final String soundType, final Runnable onComplete) {
        startPlayback(soundType, false, onComplete);
    }

    private static void startPlayback(final String soundType, final boolean loop, final Runnable onComplete) {
        synchronized (lock) {
            stopAlarm();
            try {
                short[] buffer = generateSoundBuffer(soundType);
                if (buffer == null || buffer.length == 0) return;

                int bufferSizeBytes = buffer.length * 2;
                AudioTrack track;

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
                    track = new AudioTrack(attributes, format, bufferSizeBytes, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
                } else {
                    track = new AudioTrack(
                            AudioManager.STREAM_ALARM,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSizeBytes,
                            AudioTrack.MODE_STATIC
                    );
                }

                int written = track.write(buffer, 0, buffer.length);
                if (written <= 0) {
                    Log.e(TAG, "Failed to write PCM samples to AudioTrack: " + written);
                    track.release();
                    return;
                }

                if (loop) {
                    track.setLoopPoints(0, buffer.length, -1);
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    track.setVolume(1.0f);
                } else {
                    track.setStereoVolume(1.0f, 1.0f);
                }

                activeAudioTrack = track;
                isPlaying = true;
                track.play();
                Log.d(TAG, "Started AudioTrack MODE_STATIC playback for: " + soundType + " (loop=" + loop + ")");

                if (!loop) {
                    long durationMs = (long) (buffer.length * 1000.0 / SAMPLE_RATE);
                    new Thread(() -> {
                        try {
                            Thread.sleep(durationMs + 100);
                        } catch (InterruptedException ignored) {}
                        synchronized (lock) {
                            if (activeAudioTrack == track) {
                                stopAlarm();
                            }
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }, "SoundGenerator-PreviewTimer").start();
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to start SoundGenerator: " + e.getMessage(), e);
                stopAlarm();
            }
        }
    }

    /**
     * Stops any currently playing audio generation and releases the AudioTrack.
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
     * Sound 1: Pulse Beep (Fast urgent dual-tone beeps: 880 Hz & 1760 Hz).
     * Total cycle duration: 1.2 seconds.
     */
    private static short[] generatePulseBeep() {
        int totalSamples = (int) (SAMPLE_RATE * 1.2);
        short[] samples = new short[totalSamples];

        int beep1Samples = (int) (SAMPLE_RATE * 0.14); // 140ms
        int gapSamples = (int) (SAMPLE_RATE * 0.08);   // 80ms
        int beep2Samples = (int) (SAMPLE_RATE * 0.18); // 180ms

        // Beep 1 (880 Hz + 1760 Hz)
        for (int i = 0; i < beep1Samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * i / beep1Samples);
            double val = (0.75 * Math.sin(2 * Math.PI * 880 * t) + 0.25 * Math.sin(2 * Math.PI * 1760 * t)) * envelope;
            samples[i] = (short) (val * 32000);
        }

        // Beep 2 (1046.5 Hz + 2093 Hz)
        int start2 = beep1Samples + gapSamples;
        for (int i = 0; i < beep2Samples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * i / beep2Samples);
            double val = (0.75 * Math.sin(2 * Math.PI * 1046.5 * t) + 0.25 * Math.sin(2 * Math.PI * 2093 * t)) * envelope;
            samples[start2 + i] = (short) (val * 32000);
        }

        return samples;
    }

    /**
     * Sound 2: Radar Siren (Ascending frequency sweep 600 Hz -> 1400 Hz).
     * Total cycle duration: 1.2 seconds.
     */
    private static short[] generateRadarSiren() {
        int totalSamples = (int) (SAMPLE_RATE * 1.2);
        short[] samples = new short[totalSamples];

        int sweepSamples = (int) (SAMPLE_RATE * 0.85); // 850ms sweep, 350ms pause
        double phase = 0.0;

        for (int i = 0; i < sweepSamples; i++) {
            double progress = (double) i / sweepSamples;
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
     * Total cycle duration: 1.8 seconds.
     */
    private static short[] generateSoftChime() {
        int totalSamples = (int) (SAMPLE_RATE * 1.8);
        short[] samples = new short[totalSamples];

        double[] freqs = {523.25, 659.25, 783.99, 1046.50};
        double[] weights = {0.35, 0.30, 0.20, 0.15};

        int chimeSamples = (int) (SAMPLE_RATE * 1.5);

        for (int i = 0; i < chimeSamples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double decay = Math.exp(-2.2 * t);
            double val = 0.0;
            for (int f = 0; f < freqs.length; f++) {
                val += weights[f] * Math.sin(2 * Math.PI * freqs[f] * t);
            }
            samples[i] = (short) (val * decay * 32000);
        }

        return samples;
    }
}
