package de.neitzel.encryption;

import com.idealista.fpe.config.Alphabet;
import com.idealista.fpe.config.Domain;
import com.idealista.fpe.config.GenericTransformations;

public class MainDomain implements Domain {

    private Alphabet _alphabet;
    private GenericTransformations transformations;

    public MainDomain() {
        _alphabet = new MainAlphabet();
        transformations = new GenericTransformations(_alphabet.availableCharacters());
    }

    @Override
    public Alphabet alphabet() {
        return _alphabet;
    }

    @Override
    public int[] transform(String data) {
        return transformations.transform(data);
    }

    @Override
    public String transform(int[] data) {
        return transformations.transform(data);
    }
}
