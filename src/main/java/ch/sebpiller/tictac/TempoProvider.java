package ch.sebpiller.tictac;

/**
 * Provides a bpm.
 */
@FunctionalInterface
public interface TempoProvider {
    /**
     * Returns the rate (in BPM) at which a {@link TicTac} should tic and tac.
     */
    float getTempo();
}

