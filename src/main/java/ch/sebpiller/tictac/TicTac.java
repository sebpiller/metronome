package ch.sebpiller.tictac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tic-tac does the threading job to call a method at a defined rate, as accurately as possible with Java.
 */
public class TicTac implements AutoCloseable {
    /**
     * Like my girlfriend, Java has some problems to be on time. He is always a bite late. So, like in real world, we
     * can adjust the timing here ("yes honey, the appointment is at 17:30, not at 18:00 like you thought!") :)
     *
     * The objective is to gain in overall-fell precision.
     */
    private static long __JAVA_CORRECTION_NANOS = 1_500_000;

    private static final Logger LOG = LoggerFactory.getLogger(TicTac.class);
    private static byte id = 0; // #thread creation id
    final NotificationThread nt;
    /* makes sure the thread is stopped when this object is collected. */
    private final Object __finalizer__guardian = new Object() {
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
    private BpmSource bpmSource;

    /**
     * Events are produced at regular intervals to ticTacListener. Internal processing time is compensated.
     */
    public TicTac(BpmSource bpmSource, TicTacListener ticTacListener) {
        this.bpmSource = bpmSource;
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
         * @param ticOrTac tac(true) indicates the beginning of a measure, tic(false) indicates a regular beat. They
         *                 follow a pattern like TAC-tic-tic-tic-TAC-tic-tic-tic-TAC...
         */
        boolean beat(boolean ticOrTac, float bpm);
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
                int i = 0;


                // wait data
                do {
                    bpm = bpmSource.getBpm();
                    if (bpm <= 0)
                        sleepInterrupted(200);
                } while (bpm <= 0);

                loopUntilStopped(i);
            } finally {
                // we are dead now, notify those waiting for it - you bastards :)
                terminated = true;
                synchronized (terminatedNotifier) {
                    terminatedNotifier.notifyAll();
                }
            }
        }

        private void loopUntilStopped(int i) {
            long n;
            while (!stopped) {
                // ok, then tick and wait exactly the correct amount of time
                n = System.nanoTime(); // memorize last boom

                ///// Notify of tick
                ticTacListener.beat(i++ % 4 != 0, (float) bpm);

                // next loop bpm
                bpm = bpmSource.getBpm();

                if (bpm <= 0) {
                    // shutdown as soon as the bpm return 0
                    stopped = true;
                } else {
                    // compute next tick nanos:
                    long nanosBetweenTicks = (long) (60_000_000_000d / bpm);

                    /// How much time did we need to sleep until next boom
                    long sleepNanos = n + nanosBetweenTicks - System.nanoTime() - __JAVA_CORRECTION_NANOS;

                    if (sleepNanos < 0) {
                        LOG.warn("missed tic! ({}ns)", sleepNanos);
                        i++; // erf... let's hope we miss only one tick
                    } else {
                        sleepInterrupted(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    }
                }
            }
        }

        public boolean isTerminated() {
            return terminated;
        }

        protected void sleepInterrupted(long millis) {
            sleepInterrupted(millis, 0);
        }

        protected void sleepInterrupted(long millis, int nanos) {
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
