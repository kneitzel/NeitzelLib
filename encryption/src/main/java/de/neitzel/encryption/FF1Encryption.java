package de.neitzel.encryption;

import com.idealista.fpe.FormatPreservingEncryption;
import com.idealista.fpe.builder.FormatPreservingEncryptionBuilder;
import com.idealista.fpe.config.Domain;
import com.idealista.fpe.config.LengthRange;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Helper class to deal with FF1Encryption using com.idealista:format-preserving-encryption:1.0.0.
 */
public class FF1Encryption {

    /**
     * Key to use for all encryption / unencryption
     */
    private byte[] key;

    /**
     * Should to small strings be ignored?
     *
     * If this is set to true, small strings (less than 2 characters) will not be encrypted!
     */
    private boolean ignoreToShortStrings;

    /**
     * tweak to use for encryption
     */
    private byte[] tweak;

    /**
     * Domain used for encryption
     */
    private Domain domain;

    /**
     * Format preserving encryption to use.
     */
    private FormatPreservingEncryption encryption;

    /**
     * Minimum length of a string.
     */
    private int minLength;

    /**
     * Creates a new instance of FF1Encryption
     * @param key AES key to use.
     * @param tweak tweak to use for encryption / decryption
     * @param ignoreToShortStrings Ignore strings that are to short.
     */
    public FF1Encryption(byte[] key, byte[] tweak, boolean ignoreToShortStrings) {
        this(key, tweak, ignoreToShortStrings, new MainDomain(), 2, 1024);
    }

    /**
     * Creates a new instance of FF1Encryption
     * @param key AES key to use.
     * @param tweak tweak to use for encryption / decryption
     * @param ignoreToShortStrings Ignore strings that are to short.
     * @param domain Domain to use for encryption
     * @param minLength Minimum length of string.
     * @param maxLength Maximum length of string.
     */
    public FF1Encryption(byte[] key, byte[] tweak, boolean ignoreToShortStrings, Domain domain, int minLength, int maxLength) {
        this.key = key;
        this.tweak = tweak;
        this.ignoreToShortStrings = ignoreToShortStrings;
        this.domain = domain;
        this.minLength = minLength;

        encryption = FormatPreservingEncryptionBuilder
                .ff1Implementation().withDomain(domain)
                .withDefaultPseudoRandomFunction(key)
                .withLengthRange(new LengthRange(minLength, maxLength))
                .build();
    }

    /**
     * Encrypts a given text.
     * @param plainText Unencrypted text.
     * @return Encrypted text.
     */
    public String encrypt(String plainText) {
        // Handle null
        if (plainText == null) return null;

        // Handle short strings
        if (plainText.length() < minLength && ignoreToShortStrings) return plainText;

        // Return encrypted text.
        return encryption.encrypt(plainText, tweak);
    }

    /**
     * Decrypt a given text.
     * @param cipherText Encrypted text.
     * @return Decrypted text.
     */
    public String decrypt(String cipherText) {
        // Handle null
        if (cipherText == null) return null;

        // Handle short strings
        if (cipherText.length() < minLength && ignoreToShortStrings) return cipherText;

        // Return decrypted text.
        return encryption.decrypt(cipherText, tweak);
    }

    /**
     * Creates a new AES key woth the given length.
     * @param length Length of the key in bits. Must be 128, 192 or 256 bits
     * @return Byte array of the new key.
     */
    public static byte[] createNewKey(int length) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(length); // for example
            return keyGen.generateKey().getEncoded();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a new tweak of the given length.
     * @param length Number of bytes the new teeak should have.
     * @return byte array with the new tweak.
     */
    public static byte[] createNewTweak(int length) {
        Random random = new Random();
        byte[] key = new byte[length];
        random.nextBytes(key);
        return key;
    }
}
