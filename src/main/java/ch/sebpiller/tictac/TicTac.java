package ch.sebpiller.tictac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tic-tac does the threading job to call a method at a defined rate, as accurately as possible with Java.
 */
public class TicTac implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TicTac.class);

    private static byte id = 0; // #thread creation id
    final NotificationThread nt;
    /* makes sure the thread is stopped when this object is collected. */
    private final Object _finalizerGuard = new Object() {
        @Override
        protected void finalize() throws Throwable {
            try {
                TicTac.this.nt.stopLater();
            } finally {
                super.finalize();
            }
        }
    };
    private final Object terminatedNotifier = new Object();

    /**
     * Events are produced at regular intervals to ticTacListener. Internal processing time is compensated.
     */
    public TicTac(BpmSource bpmSource, TicTacListener ticTacListener) {
        nt = new NotificationThread(bpmSource, ticTacListener);
        nt.start();
    }

    // alias of #stop()
    @Override
    public void close() {
        stop();
    }

    public void stop() {
        nt.stopped = true;
        waitTermination();
    }

    public void waitTermination() {
        if (!nt.terminated) {
            synchronized (terminatedNotifier) {
                try {
                    terminatedNotifier.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @FunctionalInterface
    public interface TicTacListener {
        /**
         * Invoked when the thread did not have enough time to invoke a tick.
         *
         * @param count The number of ticks missed (>=1).
         * @param bpm   The bpm at which the thread runs.
         */
        default void missedBeats(int count, float bpm) {
            /* noop */
        }

        /**
         * @param ticOrTac tac(false) indicates the beginning of a measure, tic(true) indicates a regular beat. They
         *                 follow a pattern like TAC-tic-tic-tic-TAC-tic-tic-tic-TAC...
         */
        void beat(boolean ticOrTac, float bpm);
    }

    class NotificationThread extends Thread {
        /**
         * Sleep the thread the amount of time required minus this value, to implement precise waiting.
         */
        private int WARMING_TIME_NANOS = 20_000_000; // 20ms

        private final TicTacListener listener;
        private final BpmSource bpmSource;
        private boolean stopped;
        private boolean terminated;
        private float bpm;

        NotificationThread(BpmSource bpmSource, TicTacListener listener) {
            this.bpmSource = bpmSource;
            this.listener = listener;
            setDaemon(true);
            setName("tictac+notif-" + (++id));
            setPriority(MAX_PRIORITY);
        }

        @Override
        public void run() {
            try {
                stopped = false;
                // wait data
                do {
                    bpm = bpmSource.getBpm();
                    if (bpm <= 0)
                        sleepSilently(200, 0);
                } while (bpm <= 0);

                loopUntilStopped();
            } finally {
                // we are dead now, notify those waiting for it
                terminated = true;
                synchronized (terminatedNotifier) {
                    terminatedNotifier.notifyAll();
                }
            }
        }

        private void loopUntilStopped() {
            long lastBeatNanos;
            int beatCounter = 0;

            while (!stopped) {
                try {
                    lastBeatNanos = System.nanoTime(); // memorize last boom

                    ///// Boom
                    listener.beat(beatCounter++ % 4 != 0, bpm);

                    // refresh desired tempo
                    bpm = bpmSource.getBpm();

                    if (bpm <= 0) {
                        // shutdown as soon as the bpm return 0
                        stopped = true;
                    } else {
                        final long nanosBetweenTicks = (long) (60_000_000_000d / bpm);
                        long sleepUntil = lastBeatNanos + nanosBetweenTicks;

                        if (sleepUntil < System.nanoTime()) {
                            // the processing time (bpm source or tic-tac listener) took too much time to tick at the
                            // correct tempo.
                            int count = (int) ((System.nanoTime() - lastBeatNanos) / nanosBetweenTicks);

                            // recompute next time for event
                            sleepUntil = lastBeatNanos + (nanosBetweenTicks * count);

                            // notify of the missed beats
                            listener.missedBeats(count, bpm); // FIXME support long processing time of missedBeats ?
                            beatCounter += count;
                        }

                        sleepUntil(sleepUntil);
                    }
                } catch (Throwable t) {
                    LOG.error("error during callback: " + t, t);
                }
            }
        }

        public boolean isTerminated() {
            return terminated;
        }

        /**
         * Precise time waiting.
         */
        private void sleepUntil(long until) {
            long now = System.nanoTime();

            // if enough time, go to sleep
            if (until >= now + WARMING_TIME_NANOS) {
                long sleepNanos = until - now - WARMING_TIME_NANOS;
                sleepSilently(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
            }

            // fine grain waiting, waits doing nothing until the required time has elapsed
            while (System.nanoTime() < until) {
                // nothing
            }
        }

        private void sleepSilently(long millis, int nanos) {
            try {
                Thread.sleep(millis, nanos);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        protected void stopLater() {
            stopped = true;
        }

        protected void stopNow() {
            stopLater();
            waitTermination();
        }
    }
}
