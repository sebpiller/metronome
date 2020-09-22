package ch.sebpiller.tictac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tic-tac does the threading job to call a method at a defined rate, as accurately as possible with Java.
 */
public class TicTac implements AutoCloseable {
    /**
     * Sleep the thread the amount of time required minus this value, to implement precise waiting.
     */
    private static long NANOS_CORRECTION = 20_000_000;

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
        nt.stopNow();
    }

    @FunctionalInterface
    public interface TicTacListener {

        /**
         * @param ticOrTac tac(false) indicates the beginning of a measure, tic(true) indicates a regular beat. They
         *                 follow a pattern like TAC-tic-tic-tic-TAC-tic-tic-tic-TAC...
         */
        void beat(boolean ticOrTac, float bpm);
    }

    class NotificationThread extends Thread {
        private final TicTacListener ticTacListener;
        private final BpmSource bpmSource;
        private boolean stopped;
        private boolean terminated;
        private float bpm;

        NotificationThread(BpmSource bpmSource, TicTacListener ticTacListener) {
            this.bpmSource = bpmSource;
            this.ticTacListener = ticTacListener;
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
                        sleepInterruptible(200);
                } while (bpm <= 0);

                loopUntilStopped();
            } finally {
                // we are dead now, notify those waiting for it - you bastards :)
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
                lastBeatNanos = System.nanoTime(); // memorize last boom

                ///// Boom
                ticTacListener.beat(beatCounter++ % 4 != 0, bpm);

                // refresh desired tempo
                bpm = bpmSource.getBpm();

                if (bpm <= 0) {
                    // shutdown as soon as the bpm return 0
                    stopped = true;
                } else {
                    final long nanosBetweenTicks = (long) (60_000_000_000d / bpm);
                    final long sleepNanos = lastBeatNanos + nanosBetweenTicks - System.nanoTime() - NANOS_CORRECTION;

                    if (sleepNanos < 0) {
                        // the processing time (bpm source or tic-tac listener) took too much time to tick at the
                        // correct tempo
                        LOG.warn("missed tic! ({}ns)", sleepNanos);
                        beatCounter++;
                    } else {
                        sleepInterruptible(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    }

                    // fine grain waiting, waits doing nothing until the required time has elapsed
                    // wait until we have reache required nanos
                    final long l = lastBeatNanos + nanosBetweenTicks;
                    while (System.nanoTime() < l) {
                        // nothing
                    }
                }
            }
        }

        public boolean isTerminated() {
            return terminated;
        }

        protected void sleepInterruptible(long millis) {
            sleepInterruptible(millis, 0);
        }

        protected void sleepInterruptible(long millis, int nanos) {
            try {
                Thread.sleep(millis, nanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        protected void stopLater() {
            stopped = true;
        }

        protected void stopNow() {
            synchronized (terminatedNotifier) {
                stopLater();
                try {
                    terminatedNotifier.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
