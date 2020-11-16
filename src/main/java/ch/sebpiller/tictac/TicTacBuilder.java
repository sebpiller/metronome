package ch.sebpiller.tictac;

public class TicTacBuilder {
    private TempoProvider tempoProvider = () -> 120;

    private TicTac.TicTacListener beat = (ticOrTac, bpm) -> {
        System.out.println((ticOrTac ? "tic   @ " : "  tac @ ") + bpm);
    };

    public TicTacBuilder withListener(TicTac.TicTacListener beat) {
        this.beat = beat;
        return this;
    }

    public TicTacBuilder connectedToBpm(TempoProvider tempoProvider) {
        this.tempoProvider = tempoProvider;
        return this;
    }

    public TicTac build() {
        TicTac ticTac = new TicTac(tempoProvider, beat);
        return ticTac;
    }
}
