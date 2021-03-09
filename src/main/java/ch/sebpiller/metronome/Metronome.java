package ch.sebpiller.metronome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metronome does the threading job to call a method at a defined rate, as accurately as possible with Java.
 */
public class Metronome implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Metronome.class);
    private static final AtomicInteger LAST_THREAD_ID = new AtomicInteger(0);

    private final NotificationThread nt;

    /* Makes sure the thread is stopped cleanly when this object is collected.
     * See Effective Java, Item 7: http://sunmingtao.blogspot.com/2016/03/finalizer-guardian.html */
    @SuppressWarnings("java:S1068")
    private final Object finalizerGuardian = new Object() {
        /**
         * {@inheritDoc}
         *
         * @deprecated since Java 9, on class {@link Object}
         */
        @Deprecated
        @SuppressWarnings("java:S1113")
        @Override
        protected void finalize() throws Throwable {
            if (!Metronome.this.isTerminated()) {
                LOG.warn("TicTac has not been closed - finalizer guardian does the cleaning");
            }

            try {
                Metronome.this.close();
            } finally {
                super.finalize();
            }
        }
    };
    private final Object terminatedNotifier = new Object();

    public Metronome(Tempo tempo, MetronomeListener metronomeListener) {
        nt = new NotificationThread(tempo, metronomeListener);
        nt.start();
    }

    /**
     * Alias of #stop()
     */
    @Override
    public void close() {
        stop();
    }

    public void stop() {
        nt.stopNow();
    }

    public void waitTermination() {
        synchronized (terminatedNotifier) {
            while (!isTerminated()) {
                try {
                    terminatedNotifier.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isTerminated() {
        return nt.isTerminated();
    }

    @FunctionalInterface
    public interface MetronomeListener {
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

    private final class NotificationThread extends Thread {
        /**
         * Sleep the thread the amount of time required minus this value, to implement precise waiting.
         */
        private static final int THREAD_SLEEP_OFFSET = 20_000_000; // 20ms

        private final MetronomeListener listener;
        private final Tempo tempo;
        private boolean stopped;
        private boolean terminated;
        private Number bpm;

        NotificationThread(Tempo tempo, MetronomeListener listener) {
            this.tempo = tempo;
            this.listener = listener;
            setDaemon(true);
            setName("metronome+notif-" + LAST_THREAD_ID.incrementAndGet());
            setPriority(MAX_PRIORITY);
        }

        @Override
        public void run() {
            try {
                stopped = false;
                // wait data
                do {
                    bpm = tempo.get();

                    if (bpm == null || bpm.floatValue() <= 0) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } while (bpm == null || bpm.floatValue() <= 0);

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
            float bpmAsFloat;

            while (!stopped) {
                try {
                    // refresh desired tempo
                    bpm = tempo.get();
                    bpmAsFloat = bpm == null ? 0 : bpm.floatValue();

                    ///// Boom
                    lastBeatNanos = System.nanoTime();
                    listener.beat(beatCounter++ % 4 != 0, bpmAsFloat);

                    if (bpm == null || (bpmAsFloat = bpm.floatValue()) <= 0) {
                        // shutdown as soon as the bpm return 0 or null
                        stopped = true;
                    } else {
                        final long nanosBetweenTicks = (long) (60_000_000_000d / bpmAsFloat);
                        long until = lastBeatNanos + nanosBetweenTicks;

                        if (until < System.nanoTime()) {
                            // the processing time (tempo or listener) took too much time to tick at the
                            // correct instant - we are facing a "missed beat" situation.
                            int count = (int) ((System.nanoTime() - lastBeatNanos) / nanosBetweenTicks);

                            // recompute time for next beat
                            until = lastBeatNanos + (nanosBetweenTicks * (count + 1));

                            // notify of the missed beats
                            listener.missedBeats(count, bpmAsFloat); // FIXME support long processing time of missedBeats ?
                            beatCounter += count;
                        }

                        preciseSleep(until);
                    }
                } catch (Exception e) {
                    LOG.warn("exception during callback: {}", e.getMessage(), e);
                }
            }
        }

        public boolean isTerminated() {
            return terminated;
        }

        /**
         * Precise time waiting. Uses {@link Thread#sleep(long, int)} to wait until #until minus
         * {@value #THREAD_SLEEP_OFFSET}ns, then loop doing nothing until enough time has elapsed.
         *
         * This empty loop (which does nothing and particularly does NOT yield the current thread in any way) is an
         * attempt to reduce the odds of being interrupted by another thread. Thus implements the main objective of
         * "precise time waiting" of this API.
         */
        private void preciseSleep(long until) {
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
                    Thread.currentThread().interrupt();
                }
            }

            // "active" waiting, loop doing nothing until the required time has elapsed
            // using a loop in a high priority thread make it less likely to be put to sleep during the execution.
            while (System.nanoTime() < until) {
                // nothing. really, nothing
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
