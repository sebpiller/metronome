package ch.sebpiller.sound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TicTabBuilder {
    private BpmFinder bpm = new BpmFinder();
    private TicTac.BeatListener beat = new LogBeatListener();

    public TicTabBuilder withListener(TicTac.BeatListener beat) {
        this.beat = beat;
        return this;
    }

    public TicTabBuilder connectedToBpm(BpmFinder bpm) {
        this.bpm = bpm;
        return this;
    }

    public TicTac build() {
        TicTac ticTac = new TicTac(bpm, beat);
        return ticTac;
    }

    private static class LogBeatListener implements TicTac.BeatListener {
        private static final Logger LOG = LoggerFactory.getLogger(LogBeatListener.class);

        @Override
        public boolean beat(boolean ticOrTac, float bpm) {
            LOG.info("{} at {}", ticOrTac ? "tac" : "tic", bpm);
            return false;
        }
    }
}
