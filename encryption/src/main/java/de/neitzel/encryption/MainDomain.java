package de.neitzel.encryption;

import com.idealista.fpe.config.Alphabet;
import com.idealista.fpe.config.Domain;
import com.idealista.fpe.config.GenericTransformations;

/**
 * Domain implementation that provides the alphabet and transformation helpers
 * used by the format-preserving encryption implementation in this project.
 * <p>
 * This class delegates character-to-index and index-to-character transformations
 * to a {@link GenericTransformations} helper configured with the {@link MainAlphabet}.
 */
public class MainDomain implements Domain {

    /**
     * The alphabet used by the domain for encoding/decoding characters.
     */
    private Alphabet _alphabet;

    /**
     * Helper instance performing actual transformations between characters and indices.
     */
    private GenericTransformations transformations;

    /**
     * Constructs a new {@code MainDomain} instance and initializes its alphabet and transformation helper.
     */
    public MainDomain() {
        _alphabet = new MainAlphabet();
        transformations = new GenericTransformations(_alphabet.availableCharacters());
    }

    /**
     * Returns the {@link Alphabet} implementation used by this domain.
     *
     * @return the alphabet used for transforming values in this domain
     */
    @Override
    public Alphabet alphabet() {
        return _alphabet;
    }

    /**
     * Transforms a string of characters into an array of integer indices according to the configured alphabet.
     *
     * @param data the input string to transform; may not be null
     * @return an integer array representing the indices of the characters in the given string
     */
    @Override
    public int[] transform(String data) {
        return transformations.transform(data);
    }

    /**
     * Transforms an array of integer indices back into a string using the configured alphabet.
     *
     * @param data the array of indices to transform back into characters
     * @return the resulting string produced from the given index array
     */
    @Override
    public String transform(int[] data) {
        return transformations.transform(data);
    }
}
