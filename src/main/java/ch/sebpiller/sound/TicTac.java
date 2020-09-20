package ch.sebpiller.sound;

/**
 * Tic-tac call a method at each beat of tempo of an input audio stream (beat detection + timer). It is more or less a metronom :).
 * <p>
 * It does its best to be as accurate as your OS let you be... but this is java, and nobody can guarantee any timing
 * here. So you can define a "frame skip" policy: <TBD/>.
 */
public class TicTac {
    private BpmFinder connectedBpm;
    // TODO convert to a list one day
    private BeatListener beatListener;

    public TicTac(BpmFinder connectedBpm, BeatListener beatListener) {
        this.connectedBpm = connectedBpm;
        NotificationThread nt = new NotificationThread(connectedBpm, beatListener);
        nt.start();
    }

    public void addListener() {
        this.beatListener = beatListener;
    }

    /**
     * Events are produced at regular intervals.
     */
    @FunctionalInterface
    public interface BeatListener {
        /**
         * @param ticOrTac tac(true) indicates the beginning of a measure, tic(false) indicates a regular beat. They
         *                 follow a pattern like TAC-tic-tic-tic-TAC-tic-tic-tic-TAC...
         */
        boolean beat(boolean ticOrTac);
    }

    private static class NotificationThread extends Thread {
        private static int i = 0;
        private final BeatListener bl;
        private final BpmFinder bpm;
        private boolean stopped;

        NotificationThread(BpmFinder bpm, BeatListener bl) {
            this.bpm = bpm;
            this.bl = bl;
            setDaemon(true);
            setName("tictac-" + (++i));
            setPriority(MAX_PRIORITY);
        }

        @Override
        public void run() {
            long referenceTimeNanos;
            stopped = false;
            int i = 0;

            double averageBpm;
            while (!stopped) {
                averageBpm = bpm.getAverageBpm();

                if (averageBpm <= 0) {
                    // no data... wait and retry
                    try {
                        sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // ok, then tick and wait exactly the correct amount off time
                    referenceTimeNanos = System.nanoTime();

                    // compute next tick nanos:
                    long targetTimeNanos = (long) (referenceTimeNanos + (1_000_000_000 * 60d / averageBpm));
                    bl.beat(i++ % 4 == 0);
                    long sleepNanos = targetTimeNanos - System.nanoTime();

                    try {
                        sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        void stopLater() {
            stopped = true;
        }
    }
}
