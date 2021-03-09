package ch.sebpiller.metronome;


/**
 * Provides utility method to convert a frequency either in BPM or Hertz to a {@link Tempo}.
 */
public final class Tempos {
    private Tempos() {
    }

    public static Tempo forTempo(float bpm) {
        return () -> bpm;
    }

    public static Tempo forFrequency(float frequencyInHertz) {
        return () -> frequencyInHertz * 60f;
    }
}
