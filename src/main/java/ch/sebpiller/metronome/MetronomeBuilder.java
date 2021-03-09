package ch.sebpiller.metronome;

public class MetronomeBuilder {
    private Tempo tempo = () -> 120;

    private Metronome.MetronomeListener beat = (ticOrTac, bpm) -> System.out.println((ticOrTac ? "tic   @ " : "  tac @ ") + bpm);

    public MetronomeBuilder withListener(Metronome.MetronomeListener beat) {
        this.beat = beat;
        return this;
    }

    public MetronomeBuilder withRhythm(Tempo tempo) {
        this.tempo = tempo;
        return this;
    }

    public Metronome build() {
        return new Metronome(tempo, beat);
    }
}
