package ch.sebpiller.tictac;


/**
 * Provides utility method to convert a frequency either in BPM or Hertz to a {@link TempoProvider}.
 */
public class TempoUtils {
    public static TempoProvider forTempo(float bpm) {
        return () -> bpm;
    }

    public static TempoProvider forHertz(float frequencyInHertz) {
        return () -> frequencyInHertz * 60f;
    }
}
