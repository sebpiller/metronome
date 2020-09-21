package ch.sebpiller.tictac;

/**
 * Bpm source.
 */
@FunctionalInterface
public interface BpmSource {
    /**
     * Returns the rate at which a {@link TicTac} should tic and tac.
     */
    float getBpm();
}

