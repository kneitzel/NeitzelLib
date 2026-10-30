package de.neitzel.fx.view.component;

/**
 * Ordinary model that holds one envelope.
 */
public class Letter {

    private Envelope envelope;

    /**
     * Creates a letter for the given envelope.
     *
     * @param envelope the envelope
     */
    public Letter(Envelope envelope) {
        this.envelope = envelope;
    }

    /**
     * Returns the envelope.
     *
     * @return the envelope
     */
    public Envelope getEnvelope() {
        return envelope;
    }

    /**
     * Sets the envelope.
     *
     * @param envelope the envelope
     */
    public void setEnvelope(Envelope envelope) {
        this.envelope = envelope;
    }
}
