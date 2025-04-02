package de.neitzel.core.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The ToneGenerator class provides methods to generate and play tones.
 * It allows playing tones based on frequency or predefined tone names.
 */
public class ToneGenerator {
    /**
     * Plays a tone based on a predefined tone name for a specified duration.
     *
     * @param tone The name of the tone to play. Must correspond to a predefined tone in the tone table.
     * @param duration The duration of the tone in milliseconds.
     * @throws LineUnavailableException If a line for audio playback cannot be opened.
     */
    public static void playTone(String tone, int duration) throws LineUnavailableException {
        playTone(ToneTable.getFrequency(tone), duration);
    }

    /**
     * Plays a tone at the specified frequency and duration.
     *
     * @param frequency the frequency of the tone in Hertz (Hz)
     * @param duration  the duration of the tone in milliseconds
     * @throws LineUnavailableException if the audio line cannot be opened
     */
    public static void playTone(double frequency, int duration) throws LineUnavailableException {
        // Get the audio format
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);

        // Open the audio line
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {

            line.open(format);
            line.start();

            // Generate the tone data
            byte[] toneBuffer = new byte[2 * duration * 44100 / 1000];
            for (int i = 0; i < toneBuffer.length; i += 2) {
                double angle = i / (44100.0 / frequency) * 2.0 * Math.PI;
                short sample = (short) (Math.sin(angle) * 32767);
                toneBuffer[i] = (byte) (sample & 0xFF);
                toneBuffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            // Play the tone
            line.write(toneBuffer, 0, toneBuffer.length);
            line.drain();
        }
    }

    /**
     * Plays one or more specified tones for a given duration.
     * Each tone is played sequentially in the order they are provided.
     *
     * @param duration the duration of each tone in milliseconds
     * @param tones    the names of the tones to play, specified as a variable-length argument
     * @throws LineUnavailableException if the audio line cannot be opened or accessed
     */
    public static void playTone(int duration, String... tones) throws LineUnavailableException {
        for (String tone : tones) {
            playTone(tone, duration);
        }
    }
}
