package ch.sebpiller.tictac;

public class TicTabBuilder {
    private BpmSource bpmSource = () -> 120;

    private TicTac.TicTacListener beat = (ticOrTac, bpm) -> {
        System.out.println((ticOrTac ? "tic   @ " : "  tac @ ") + bpm);
    };

    public TicTabBuilder withListener(TicTac.TicTacListener beat) {
        this.beat = beat;
        return this;
    }

    public TicTabBuilder connectedToBpm(BpmSource bpmSource) {
        this.bpmSource = bpmSource;
        return this;
    }

    public TicTac build() {
        TicTac ticTac = new TicTac(bpmSource, beat);
        return ticTac;
    }
}
