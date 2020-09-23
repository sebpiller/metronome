package ch.sebpiller.tictac;

public class TicTacBuilder {
    private BpmSource bpmSource = () -> 120;

    private TicTac.TicTacListener beat = (ticOrTac, bpm) -> {
        System.out.println((ticOrTac ? "tic   @ " : "  tac @ ") + bpm);
    };

    public TicTacBuilder withListener(TicTac.TicTacListener beat) {
        this.beat = beat;
        return this;
    }

    public TicTacBuilder connectedToBpm(BpmSource bpmSource) {
        this.bpmSource = bpmSource;
        return this;
    }

    public TicTac build() {
        TicTac ticTac = new TicTac(bpmSource, beat);
        return ticTac;
    }
}
