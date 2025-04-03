package de.neitzel.core.sound;

import java.util.HashMap;

/**
 * The ToneTable class provides a static lookup tool for associating musical tone names
 * with their corresponding frequencies in hertz (Hz). It allows users to
 * retrieve the frequency of a tone based on its standard notation name,
 * such as "C3", "A4", or "G#5".
 *
 * This class can be particularly useful in applications related to sound synthesis,
 * music theory, signal processing, and other audio-related domains that require precise
 * frequency information for specific tones.
 */
public class ToneTable {
    /**
     * Private constructor to prevent instantiation of the utility class.
     * This utility class is not meant to be instantiated, as it only provides
     * static utility methods for array-related operations.
     *
     * @throws UnsupportedOperationException always, to indicate that this class
     *                                        should not be instantiated.
     */
    private ToneTable() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A static map that associates written tone names with their corresponding frequencies in hertz (Hz).
     * The keys represent tone names (e.g., "C4", "D#5"), and the values are their respective frequencies.
     * This map serves as a reference for converting tone names into their precise frequency values,
     * which are used in applications such as tone generation or audio playback.
     *
     * The map is a crucial component of the ToneTable class, providing quick lookup of frequencies
     * for predefined tone names.
     */
    private static final HashMap<String, Double> toneMap = new HashMap<>();
    static {
        toneMap.put("C0", 16.35);
        toneMap.put("C#0", 17.32);
        toneMap.put("Db0", 17.32);
        toneMap.put("D0", 18.35);
        toneMap.put("D#0", 19.45);
        toneMap.put("Eb0", 19.45);
        toneMap.put("E0", 20.60);
        toneMap.put("F0", 21.83);
        toneMap.put("F#0", 23.12);
        toneMap.put("Gb0", 23.12);
        toneMap.put("G0", 24.50);
        toneMap.put("G#0", 25.96);
        toneMap.put("Ab0", 25.96);
        toneMap.put("A0", 27.50);
        toneMap.put("A#0", 29.14);
        toneMap.put("Bb0", 29.14);
        toneMap.put("B0", 30.87);
        toneMap.put("C1", 32.70);
        toneMap.put("C#1", 34.65);
        toneMap.put("Db1", 34.65);
        toneMap.put("D1", 36.71);
        toneMap.put("D#1", 38.89);
        toneMap.put("Eb1", 38.89);
        toneMap.put("E1", 41.20);
        toneMap.put("F1", 43.65);
        toneMap.put("F#1", 46.25);
        toneMap.put("Gb1", 46.25);
        toneMap.put("G1", 49.00);
        toneMap.put("G#1", 51.91);
        toneMap.put("Ab1", 51.91);
        toneMap.put("A1", 55.00);
        toneMap.put("A#1", 58.27);
        toneMap.put("Bb1", 58.27);
        toneMap.put("B1", 61.74);
        toneMap.put("C2", 65.41);
        toneMap.put("C#2", 69.30);
        toneMap.put("Db2", 69.30);
        toneMap.put("D2", 73.42);
        toneMap.put("D#2", 77.78);
        toneMap.put("Eb2", 77.78);
        toneMap.put("E2", 82.41);
        toneMap.put("F2", 87.31);
        toneMap.put("F#2", 92.50);
        toneMap.put("Gb2", 92.50);
        toneMap.put("G2", 98.00);
        toneMap.put("G#2", 103.83);
        toneMap.put("Ab2", 103.83);
        toneMap.put("A2", 110.00);
        toneMap.put("A#2", 116.54);
        toneMap.put("Bb2", 116.54);
        toneMap.put("B2", 123.47);
        toneMap.put("C3", 130.81);
        toneMap.put("C#3", 138.59);
        toneMap.put("Db3", 138.59);
        toneMap.put("D3", 146.83);
        toneMap.put("D#3", 155.56);
        toneMap.put("Eb3", 155.56);
        toneMap.put("E3", 164.81);
        toneMap.put("F3", 174.61);
        toneMap.put("F#3", 185.00);
        toneMap.put("Gb3", 185.00);
        toneMap.put("G3", 196.00);
        toneMap.put("G#3", 207.65);
        toneMap.put("Ab3", 207.65);
        toneMap.put("A3", 220.00);
        toneMap.put("A#3", 233.08);
        toneMap.put("Bb3", 233.08);
        toneMap.put("B3", 246.94);
        toneMap.put("C4", 261.63);
        toneMap.put("C#4", 277.18);
        toneMap.put("Db4", 277.18);
        toneMap.put("D4", 293.66);
        toneMap.put("D#4", 311.13);
        toneMap.put("Eb4", 311.13);
        toneMap.put("E4", 329.63);
        toneMap.put("F4", 349.23);
        toneMap.put("F#4", 369.99);
        toneMap.put("Gb4", 369.99);
        toneMap.put("G4", 392.00);
        toneMap.put("G#4", 415.30);
        toneMap.put("Ab4", 415.30);
        toneMap.put("A4", 440.00);
        toneMap.put("A#4", 466.16);
        toneMap.put("Bb4", 466.16);
        toneMap.put("B4", 493.88);
        toneMap.put("C5", 523.25);
        toneMap.put("C#5", 554.37);
        toneMap.put("Db5", 554.37);
        toneMap.put("D5", 587.33);
        toneMap.put("D#5", 622.25);
        toneMap.put("Eb5", 622.25);
        toneMap.put("E5", 659.25);
        toneMap.put("F5", 698.46);
        toneMap.put("F#5", 739.99);
        toneMap.put("Gb5", 739.99);
        toneMap.put("G5", 783.99);
        toneMap.put("G#5", 830.61);
        toneMap.put("Ab5", 830.61);
        toneMap.put("A5", 880.00);
        toneMap.put("A#5", 932.33);
        toneMap.put("Bb5", 932.33);
        toneMap.put("B5", 987.77);
        toneMap.put("C6", 1046.50);
        toneMap.put("C#6", 1108.73);
        toneMap.put("Db6", 1108.73);
        toneMap.put("D6", 1174.66);
        toneMap.put("D#6", 1244.51);
        toneMap.put("Eb6", 1244.51);
        toneMap.put("E6", 1318.51);
        toneMap.put("F6", 1396.91);
        toneMap.put("F#6", 1479.98);
        toneMap.put("Gb6", 1479.98);
        toneMap.put("G6", 1567.98);
        toneMap.put("G#6", 1661.22);
        toneMap.put("Ab6", 1661.22);
        toneMap.put("A6", 1760.00);
        toneMap.put("A#6", 1864.66);
        toneMap.put("Bb6", 1864.66);
        toneMap.put("B6", 1975.53);
        toneMap.put("C7", 2093.00);
        toneMap.put("C#7", 2217.46);
        toneMap.put("Db7", 2217.46);
        toneMap.put("D7", 2349.32);
        toneMap.put("D#7", 2489.02);
        toneMap.put("Eb7", 2489.02);
        toneMap.put("E7", 2637.02);
        toneMap.put("F7", 2793.83);
        toneMap.put("F#7", 2959.96);
        toneMap.put("Gb7", 2959.96);
        toneMap.put("G7", 3135.96);
        toneMap.put("G#7", 3322.44);
        toneMap.put("Ab7", 3322.44);
        toneMap.put("A7", 3520.00);
        toneMap.put("A#7", 3729.31);
        toneMap.put("Bb7", 3729.31);
        toneMap.put("B7", 3951.07);
        toneMap.put("C8", 4186.01);
        toneMap.put("C#8", 4434.92);
        toneMap.put("Db8", 4434.92);
        toneMap.put("D8", 4698.63);
        toneMap.put("D#8", 4978.03);
        toneMap.put("Eb8", 4978.03);
        toneMap.put("E8", 5274.04);
        toneMap.put("F8", 5587.65);
        toneMap.put("F#8", 5919.91);
        toneMap.put("Gb8", 5919.91);
        toneMap.put("G8", 6271.93);
        toneMap.put("G#8", 6644.88);
        toneMap.put("Ab8", 6644.88);
        toneMap.put("A8", 7040.00);
        toneMap.put("A#8", 7458.62);
        toneMap.put("Bb8", 7458.62);
        toneMap.put("B8", 7902.13);
    }

    /**
     * Retrieves the frequency of the tone based on the given tone name.
     * If the tone name exists in the tone map, its corresponding frequency is returned.
     * Otherwise, an exception is thrown.
     *
     * @param toneName the name of the tone whose frequency is to be retrieved
     * @return the frequency associated with the specified tone name
     * @throws IllegalArgumentException if the tone name is not found in the tone map
     */
    public static double getFrequency(String toneName) {
        if (toneMap.containsKey(toneName)) {
            return toneMap.get(toneName);
        } else {
            throw new IllegalArgumentException("Unknown tone name: " + toneName);
        }
    }
}