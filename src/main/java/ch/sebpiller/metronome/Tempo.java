package ch.sebpiller.metronome;

import java.util.function.Supplier;

/**
 * Provides a rhythm, as a floating point number, in Beats Per Minute (BPM).
 */
@FunctionalInterface
public interface Tempo extends Supplier<Number> {
}

