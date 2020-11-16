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
    public TicTac(TempoProvider tempoProvider, TicTacListener ticTacListener) {
        nt = new NotificationThread(tempoProvider, ticTacListener);
        nt.start();
    }

    // alias of #stop()
    @Override
    public void close() {
        stop();
    }

    public void stop() {
        nt.stopNow();
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
        private int THREAD_SLEEP_OFFSET = 20_000_000; // 20ms

        private final TicTacListener listener;
        private final TempoProvider tempoProvider;
        private boolean stopped;
        private boolean terminated;
        private float bpm;

        NotificationThread(TempoProvider tempoProvider, TicTacListener listener) {
            this.tempoProvider = tempoProvider;
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
                    bpm = tempoProvider.getTempo();
                    if (bpm <= 0) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
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
                    bpm = tempoProvider.getTempo();

                    if (bpm <= 0) {
                        // shutdown as soon as the bpm return 0
                        stopped = true;
                    } else {
                        final long nanosBetweenTicks = (long) (60_000_000_000d / bpm);
                        long until = lastBeatNanos + nanosBetweenTicks;

                        if (until < System.nanoTime()) {
                            // the processing time (bpm source or tic-tac listener) took too much time to tick at the
                            // correct tempo.
                            int count = (int) ((System.nanoTime() - lastBeatNanos) / nanosBetweenTicks);

                            // recompute time for next beat
                            until = lastBeatNanos + (nanosBetweenTicks * (count + 1));

                            // notify of the missed beats
                            listener.missedBeats(count, bpm); // FIXME support long processing time of missedBeats ?
                            beatCounter += count;
                        }

                        sleepUntil(until);
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
         * Precise time waiting. Use {@link Thread#sleep(long)} to wait until #until - {@link #THREAD_SLEEP_OFFSET},
         * then loop doing nothing until enough time has elapsed.
         */
        private void sleepUntil(long until) {
            long now = System.nanoTime();
            if (LOG.isTraceEnabled()) {
                LOG.trace("sleeping until {} (sleeping {}ns)", until, until - now);
            }

            // if enough time, go to sleep
            if (until >= now + THREAD_SLEEP_OFFSET) {
                long sleepNanos = until - now - THREAD_SLEEP_OFFSET;
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            // "active" waiting, loop doing nothing until the required time has elapsed
            // using a loop in a high priority thread make it less likely to be put to sleep during the execution.
            while (System.nanoTime() < until) {
                // nothing
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
